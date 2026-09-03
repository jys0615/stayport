package io.github.jys0615.stayport.application;

import io.github.jys0615.stayport.application.port.FailureType;
import io.github.jys0615.stayport.application.port.MappedRoomType;
import io.github.jys0615.stayport.application.port.MappingStore;
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
 * 통합 검색.
 *
 * <p>흐름은 이렇다. 매핑에서 공급사별 숙소 코드를 꺼내 → 코드가 있는 공급사들을 동시에 부르고 →
 * 돌아온 상품을 내부 식별자로 해석해 → 하나의 목록으로 합친다. 한쪽이 실패해도 나머지로
 * 응답하고, 어디까지 받았는지를 함께 싣는다.
 *
 * <p><b>공급사 호출은 동시에 나간다.</b> 순차로 부르면 지연이 그대로 더해지지만 병렬이면
 * 전체 지연이 가장 느린 공급사에 수렴한다. 공급사 수는 설정으로 늘어날 수 있으므로 고정 인자를
 * 받는 {@code Mono.zip}이 아니라 {@code Flux.flatMap}으로 묶는다.
 *
 * <p><b>경계에서 한 번 블로킹한다.</b> 컨트롤러는 동기 DTO를 돌려주므로 여기서 결과를 받아야
 * 한다. 그 대가로 요청 하나가 예산만큼 서블릿 스레드를 점유하고, 그래서 예산이 짧아야 한다.
 * 타임아웃 값이 곧 스레드 점유 시간의 상한이다.
 *
 * <p><b>응답 순서는 고정한다.</b> 병렬 호출은 끝나는 순서가 매번 다르므로, 그대로 담으면 같은
 * 요청이 실행마다 다른 순서를 돌려준다. 정렬 기능을 넣는 것이 아니라 응답을 안정시키는 것이다.
 */
@Service
public class StaySearchService {

    private static final Logger log = LoggerFactory.getLogger(StaySearchService.class);

    /** 응답 순서 고정용. 내부 식별자 순이고, 같은 객실을 두 공급사가 팔면 공급사 순으로 갈린다. */
    private static final Comparator<StayOffer> STABLE_ORDER = Comparator
            .comparingLong(StayOffer::stayId)
            .thenComparingLong(StayOffer::roomTypeId)
            .thenComparing(StayOffer::supplier);

    private final List<SupplierAdapter> adapters;
    private final MappingStore mappingStore;
    private final Duration totalBudget;

    StaySearchService(List<SupplierAdapter> adapters, MappingStore mappingStore, StayportProperties properties) {
        this.adapters = List.copyOf(adapters);
        this.mappingStore = mappingStore;
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
                // 물어볼 것이 없다. 부르지 않고, 안 물어봤다는 사실만 남긴다. 이걸 실패로 적으면
                // 공급사 장애와 구분되지 않고, 빈 결과로 두면 아무 사실도 전하지 못한다.
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
                    outcomes.add(SearchResult.SupplierOutcome.ok(
                            success.supplier(),
                            resolved.offers().size(),
                            success.skippedItems() + resolved.unmapped()));
                }
                case SupplierResult.Partial partial -> {
                    Resolved resolved = resolve(partial.supplier(), partial.offers(), index);
                    stays.addAll(resolved.offers());
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
     * 공급사들을 동시에 부르고, 예산 안에 도착한 것을 모은다.
     *
     * <p><b>예산이 지나도 이미 도착한 결과는 버리지 않는다.</b> 처음에는
     * {@code collectList().timeout(예산)}으로 짰는데, {@code collectList}는 전원이 끝나야 값을
     * 내므로 하나가 늦는 순간 이미 받아둔 다른 공급사의 결과까지 통째로 버려졌다. 부분 실패
     * 허용이 바로 그 지점에서 깨진다. {@code take(예산)}은 시간이 다 되면 그때까지 흘러온 것만
     * 들고 완료한다.
     *
     * <p>예산 안에 답이 없는 공급사는 응답에서 빠지는 게 아니라 타임아웃으로 채운다. 빠뜨리면
     * 클라이언트가 그 공급사를 물어봤는지조차 알 수 없다.
     */
    private List<SupplierResult> callInParallel(
            List<SupplierAdapter> queryable, SearchQuery query, Map<SupplierId, List<String>> stayCodes) {

        if (queryable.isEmpty()) {
            return List.of();
        }

        List<SupplierResult> arrived = Flux.fromIterable(queryable)
                .flatMap(adapter -> adapter.fetchOffers(query, stayCodes.get(adapter.supplier()))
                        // 어댑터는 실패를 값으로 돌려주기로 약속했다. 약속이 깨져 예외가 올라와도
                        // 다른 공급사의 결과가 함께 사라지지는 않게 한다.
                        .onErrorResume(error -> Mono.just(adapterBroke(adapter.supplier(), error))))
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
                results.add(new SupplierResult.Failure(
                        adapter.supplier(), FailureType.TIMEOUT, "search budget exceeded"));
            }
        }
        return results;
    }

    private static Map<SupplierId, List<String>> stayCodesBySupplier(List<MappedRoomType> mappings) {
        Map<SupplierId, List<String>> codes = new LinkedHashMap<>();
        for (MappedRoomType mapping : mappings) {
            // 한 숙소에 객실 타입이 여럿이므로 같은 숙소 코드가 여러 번 나온다. 중복을 걸러야
            // 공급사에 같은 코드를 여러 번 보내지 않는다.
            List<String> perSupplier = codes.computeIfAbsent(mapping.supplier(), key -> new ArrayList<>());
            if (!perSupplier.contains(mapping.supplierStayCode())) {
                perSupplier.add(mapping.supplierStayCode());
            }
        }
        return codes;
    }

    /**
     * 공급사 코드를 내부 식별자로 바꾼다.
     *
     * <p>여기서 숙소명과 객실 타입명도 매핑에 저장된 스냅샷으로 갈아탄다. 공급사가 준 이름을
     * 쓰지 않는 것은 표시 값의 출처를 한 경로로 두기로 했기 때문이다.
     *
     * <p>매핑에 없는 상품은 버린다. 공급사가 우리 목록에 없는 숙소를 돌려준 경우인데, 내부
     * 식별자를 만들어 낼 수는 없다. 버린 수는 세어서 응답에 드러낸다.
     */
    private Resolved resolve(SupplierId supplier, List<SupplierOffer> offers, MappingIndex index) {
        List<StayOffer> resolved = new ArrayList<>(offers.size());
        int unmapped = 0;

        for (SupplierOffer offer : offers) {
            MappedRoomType mapping = index.find(supplier, offer.stayCode(), offer.roomCode());
            if (mapping == null) {
                unmapped++;
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

    /** 어댑터가 예외를 던진 경우 — 계약 위반이다. 원인을 남기고 이 공급사만 실패로 접는다. */
    private SupplierResult adapterBroke(SupplierId supplier, Throwable error) {
        log.error("supplier {} 어댑터가 예외를 던졌다. 실패를 값으로 돌려주기로 한 계약을 어긴 것이다",
                supplier, error);
        FailureType type = error instanceof TimeoutException ? FailureType.TIMEOUT : FailureType.SUPPLIER_ERROR;
        return new SupplierResult.Failure(supplier, type, error.getClass().getSimpleName());
    }

    private record Resolved(List<StayOffer> offers, int unmapped) {
    }

    /** {@code (공급사, 숙소 코드, 객실 코드)} 로 매핑을 찾는 색인. */
    private record MappingIndex(Map<String, MappedRoomType> byKey) {

        static MappingIndex of(List<MappedRoomType> mappings) {
            Map<String, MappedRoomType> byKey = new LinkedHashMap<>();
            for (MappedRoomType mapping : mappings) {
                byKey.put(key(mapping.supplier(), mapping.supplierStayCode(), mapping.supplierRoomCode()), mapping);
            }
            return new MappingIndex(byKey);
        }

        MappedRoomType find(SupplierId supplier, String stayCode, String roomCode) {
            return byKey.get(key(supplier, stayCode, roomCode));
        }

        private static String key(SupplierId supplier, String stayCode, String roomCode) {
            return supplier + " " + stayCode + " " + roomCode;
        }
    }
}
