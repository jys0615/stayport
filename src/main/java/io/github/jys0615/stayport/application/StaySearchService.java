package io.github.jys0615.stayport.application;

import io.github.jys0615.stayport.application.port.FailureType;
import io.github.jys0615.stayport.application.port.MappedRoomType;
import io.github.jys0615.stayport.application.port.MappingStore;
import io.github.jys0615.stayport.application.port.QuarantineStore;
import io.github.jys0615.stayport.application.port.SkippedOffer;
import io.github.jys0615.stayport.application.port.SupplierAdapter;
import io.github.jys0615.stayport.application.port.SupplierOffer;
import io.github.jys0615.stayport.application.port.SupplierResult;
import io.github.jys0615.stayport.domain.SearchQuery;
import io.github.jys0615.stayport.domain.StayOffer;
import io.github.jys0615.stayport.domain.SupplierId;
import io.github.jys0615.stayport.infra.StayportProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 통합 검색: 매핑에서 공급사별 숙소 코드를 모아 병렬 조회하고, 내부 식별자로 해석해 합친다.
 * 한 공급사의 실패는 해당 공급사의 상태로만 남는다. 설계 근거는 docs/design.md §5·§7.
 *
 * <p>컨트롤러가 동기이므로 여기서 한 번 블로킹한다. 요청당 스레드 점유 상한 = 검색 예산(설정).
 * 응답 순서는 내부 식별자 기준으로 고정 — 병렬 완료 순서가 응답에 노출되지 않게.
 */
@Service
public class StaySearchService {

    private static final Logger log = LoggerFactory.getLogger(StaySearchService.class);

    /** 응답 순서 고정용. */
    private static final Comparator<StayOffer> STABLE_ORDER = Comparator
            .comparingLong(StayOffer::stayId)
            .thenComparingLong(StayOffer::roomTypeId)
            .thenComparing(StayOffer::supplier);

    private final List<SupplierAdapter> adapters;
    private final MappingStore mappingStore;
    private final QuarantineStore quarantineStore;
    private final SearchMetrics metrics;
    private final Duration totalBudget;

    StaySearchService(List<SupplierAdapter> adapters, MappingStore mappingStore,
            QuarantineStore quarantineStore, SearchMetrics metrics, StayportProperties properties) {
        this.adapters = List.copyOf(adapters);
        this.mappingStore = mappingStore;
        this.quarantineStore = quarantineStore;
        this.metrics = metrics;
        this.totalBudget = properties.search().totalBudget();
    }

    public SearchResult search(SearchQuery query) {
        List<MappedRoomType> mappings = mappingStore.findAll();
        Map<SupplierId, List<String>> stayCodes = stayCodesBySupplier(mappings);
        MappingIndex index = MappingIndex.of(mappings);

        List<SupplierAdapter> queryable = new ArrayList<>();
        List<SearchResult.SupplierOutcome> outcomes = new ArrayList<>();
        for (SupplierAdapter adapter : adapters) {
            if (stayCodes.getOrDefault(adapter.supplier(), List.of()).isEmpty()) {
                // 매핑이 없는 공급사는 부르지 않는다. 공급사 장애(FAILED)와 구분해 NO_MAPPING으로 남긴다.
                outcomes.add(SearchResult.SupplierOutcome.noMapping(adapter.supplier()));
            } else {
                queryable.add(adapter);
            }
        }

        List<StayOffer> stays = new ArrayList<>();
        for (SupplierResult result : callInParallel(queryable, query, stayCodes)) {
            switch (result) {
                case SupplierResult.Success success -> {
                    Resolved resolved = resolve(success.supplier(), success.offers(), index);
                    stays.addAll(resolved.offers());
                    quarantine(success.supplier(), success.skippedDetails());
                    outcomes.add(SearchResult.SupplierOutcome.ok(
                            success.supplier(),
                            resolved.offers().size(),
                            success.skippedItems() + resolved.unmapped()));
                }
                case SupplierResult.Partial partial -> {
                    Resolved resolved = resolve(partial.supplier(), partial.offers(), index);
                    stays.addAll(resolved.offers());
                    quarantine(partial.supplier(), partial.skippedDetails());
                    outcomes.add(new SearchResult.SupplierOutcome(
                            partial.supplier(),
                            SupplierStatus.PARTIAL,
                            resolved.offers().size(),
                            partial.skippedItems() + resolved.unmapped(),
                            partial.failedChunks(),
                            partial.failures()));
                }
                case SupplierResult.Failure failure -> outcomes.add(new SearchResult.SupplierOutcome(
                        failure.supplier(),
                        SupplierStatus.FAILED,
                        0,
                        0,
                        0,
                        Map.of(failure.type(), 1)));
            }
        }

        stays.sort(STABLE_ORDER);
        outcomes.sort(Comparator.comparing(SearchResult.SupplierOutcome::supplier));
        return new SearchResult(stays, outcomes);
    }

    /**
     * 공급사들을 병렬로 부르고 예산 안에 도착한 결과만 모은다.
     *
     * <p>주의: {@code collectList().timeout(예산)}으로 바꾸면 안 된다 — 하나만 늦어도 이미 받은
     * 결과까지 전부 버려진다. 회귀 테스트: SearchFailureIsolationTest.
     * 예산 안에 답이 없는 공급사는 TIMEOUT으로 채워 응답에서 빠지지 않게 한다.
     */
    private List<SupplierResult> callInParallel(
            List<SupplierAdapter> queryable, SearchQuery query, Map<SupplierId, List<String>> stayCodes) {

        if (queryable.isEmpty()) {
            return List.of();
        }

        List<SupplierResult> arrived = Flux.fromIterable(queryable)
                .flatMap(adapter -> Mono.defer(() -> {
                    long startedAt = System.nanoTime();
                    return adapter.fetchOffers(query, stayCodes.get(adapter.supplier()))
                            // 어댑터가 예외를 던져도(계약 위반) 다른 공급사의 결과는 유지한다.
                            .onErrorResume(error -> Mono.just(adapterBroke(adapter.supplier(), error)))
                            .doOnNext(result -> metrics.recordArrival(
                                    result, Duration.ofNanos(System.nanoTime() - startedAt)));
                }))
                .take(totalBudget)
                .collectList()
                .block();

        List<SupplierResult> results = new ArrayList<>(arrived == null ? List.of() : arrived);

        Set<SupplierId> answered = results.stream()
                .map(SupplierResult::supplier)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(SupplierId.class)));

        for (SupplierAdapter adapter : queryable) {
            if (!answered.contains(adapter.supplier())) {
                log.warn("supplier {}가 검색 예산 {}ms 안에 답하지 않았다. 나머지 결과로 응답한다",
                        adapter.supplier(), totalBudget.toMillis());
                metrics.recordBudgetTimeout(adapter.supplier());
                results.add(new SupplierResult.Failure(
                        adapter.supplier(), FailureType.TIMEOUT, "search budget exceeded"));
            }
        }
        return results;
    }

    private static Map<SupplierId, List<String>> stayCodesBySupplier(List<MappedRoomType> mappings) {
        Map<SupplierId, List<String>> codes = new LinkedHashMap<>();
        for (MappedRoomType mapping : mappings) {
            // 한 숙소에 객실 타입이 여러 개라 숙소 코드가 중복으로 나온다.
            List<String> perSupplier = codes.computeIfAbsent(mapping.supplier(), key -> new ArrayList<>());
            if (!perSupplier.contains(mapping.supplierStayCode())) {
                perSupplier.add(mapping.supplierStayCode());
            }
        }
        return codes;
    }

    /**
     * 공급사 코드 → 내부 식별자. 표시 이름은 공급사 응답이 아니라 매핑 스냅샷을 쓴다(design.md §4).
     * 매핑에 없는 상품은 제외하고 개수만 센다.
     */
    private Resolved resolve(SupplierId supplier, List<SupplierOffer> offers, MappingIndex index) {
        List<StayOffer> resolved = new ArrayList<>(offers.size());
        int unmapped = 0;

        for (SupplierOffer offer : offers) {
            MappedRoomType mapping = index.find(supplier, offer.stayCode(), offer.roomCode());
            if (mapping == null) {
                unmapped++;
                quarantineStore.keep(supplier, "매핑 없음",
                        "%s %s".formatted(offer.stayCode(), offer.roomCode()));
                continue;
            }
            resolved.add(new StayOffer(
                    mapping.internalStayId(),
                    mapping.stayName(),
                    mapping.internalRoomTypeId(),
                    mapping.roomTypeName(),
                    offer.maxOccupancy(),
                    offer.availableRooms(),
                    offer.breakfastIncluded(),
                    offer.price(),
                    supplier));
        }

        if (unmapped > 0) {
            log.warn("supplier {} 응답 중 {}건은 매핑이 없어 제외했다. 동기화가 밀렸을 수 있다",
                    supplier, unmapped);
        }
        return new Resolved(resolved, unmapped);
    }

    /** 어댑터가 모아온 스킵 상세를 격리 테이블로. block() 뒤라 JPA 호출이 안전하다. */
    private void quarantine(SupplierId supplier, List<SkippedOffer> details) {
        for (SkippedOffer detail : details) {
            quarantineStore.keep(supplier, detail.reason(), detail.payload());
        }
    }

    /** 어댑터가 예외를 던진 경우(계약 위반). 이 공급사만 실패 처리한다. */
    private SupplierResult adapterBroke(SupplierId supplier, Throwable error) {
        log.error("supplier {} 어댑터가 예외를 던졌다 (실패는 값으로 반환하는 계약)", supplier, error);
        FailureType type = error instanceof TimeoutException ? FailureType.TIMEOUT : FailureType.SUPPLIER_ERROR;
        return new SupplierResult.Failure(supplier, type, error.getClass().getSimpleName());
    }

    private record Resolved(List<StayOffer> offers, int unmapped) {
    }

    /**
     * (공급사, 숙소 코드, 객실 코드) → 매핑 색인. 키는 문자열 연결이 아니라 레코드 —
     * 코드에 구분자 문자가 들어오면 서로 다른 튜플이 한 키로 합쳐질 수 있다.
     */
    private record MappingIndex(Map<Key, MappedRoomType> byKey) {

        private record Key(SupplierId supplier, String stayCode, String roomCode) {
        }

        static MappingIndex of(List<MappedRoomType> mappings) {
            Map<Key, MappedRoomType> byKey = new LinkedHashMap<>();
            for (MappedRoomType mapping : mappings) {
                Key key = new Key(mapping.supplier(), mapping.supplierStayCode(), mapping.supplierRoomCode());
                MappedRoomType previous = byKey.putIfAbsent(key, mapping);
                if (previous != null) {
                    // 중복 키 = DB UNIQUE 제약이 깨졌다는 뜻. 덮어쓰지 않고 바로 실패시킨다.
                    throw new IllegalStateException("매핑 키가 중복이다: " + key);
                }
            }
            return new MappingIndex(byKey);
        }

        MappedRoomType find(SupplierId supplier, String stayCode, String roomCode) {
            return byKey.get(new Key(supplier, stayCode, roomCode));
        }
    }
}
