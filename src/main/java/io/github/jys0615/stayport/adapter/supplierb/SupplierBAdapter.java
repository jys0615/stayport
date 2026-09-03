package io.github.jys0615.stayport.adapter.supplierb;

import io.github.jys0615.stayport.adapter.ChunkedOffers;
import io.github.jys0615.stayport.adapter.SupplierErrors;
import io.github.jys0615.stayport.application.port.CatalogResult;
import io.github.jys0615.stayport.application.port.FailureType;
import io.github.jys0615.stayport.application.port.SupplierAdapter;
import io.github.jys0615.stayport.application.port.SupplierOffer;
import io.github.jys0615.stayport.application.port.SupplierResult;
import io.github.jys0615.stayport.application.port.SupplierRoomType;
import io.github.jys0615.stayport.application.port.SupplierStay;
import io.github.jys0615.stayport.domain.Price;
import io.github.jys0615.stayport.domain.SearchQuery;
import io.github.jys0615.stayport.domain.SupplierId;
import io.github.jys0615.stayport.infra.StayportProperties;
import io.github.jys0615.stayport.infra.SupplierWebClients;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Supplier B 어댑터.
 *
 * <p><b>B는 장애 상황에서도 HTTP 200을 준다.</b> 본문의 {@code resultCode}를 확인하지 않으면
 * 장애 응답의 {@code data: null}이 "결과 0건"으로 둔갑한다. 클라이언트에게 "방이 없다"와
 * "공급사가 죽었다"는 전혀 다른 사실이므로, 이 판정이 이 어댑터의 존재 이유에 가깝다.
 */
@Component
class SupplierBAdapter implements SupplierAdapter {

    private static final Logger log = LoggerFactory.getLogger(SupplierBAdapter.class);

    private static final String SUCCESS = "0000";

    private final WebClient client;
    private final StayportProperties.Supplier config;

    SupplierBAdapter(SupplierWebClients clients, StayportProperties properties) {
        this.client = clients.forSupplier(SupplierId.B);
        this.config = properties.suppliers().get(SupplierId.B);
    }

    @Override
    public SupplierId supplier() {
        return SupplierId.B;
    }

    @Override
    public Mono<CatalogResult> fetchCatalog() {
        return client.get()
                .uri(config.paths().catalog())
                .exchangeToMono(this::readCatalog)
                .timeout(config.callTimeout())
                .onErrorResume(error -> Mono.just(catalogFailure(SupplierErrors.classify(error), error.getClass().getSimpleName())));
    }

    @Override
    public Mono<SupplierResult> fetchOffers(SearchQuery query, List<String> stayCodes) {
        if (stayCodes.isEmpty()) {
            // 물어볼 것이 없으면 부르지 않는다. 빈 목록으로 호출하면 공급사가 400을 준다.
            return Mono.just(SupplierResult.Success.of(SupplierId.B, List.of()));
        }
        return Flux.fromIterable(ChunkedOffers.split(stayCodes, config.chunkSize()))
                .flatMap(chunk -> fetchChunk(query, chunk))
                .collectList()
                .map(results -> ChunkedOffers.merge(SupplierId.B, results));
    }

    /** 한도 이하의 코드 묶음 하나를 조회한다. 실패는 예외가 아니라 이 묶음의 결과로 돌아온다. */
    private Mono<SupplierResult> fetchChunk(SearchQuery query, List<String> stayCodes) {
        return client.get()
                .uri(builder -> builder.path(config.paths().availability())
                        .queryParam("propertyIds", String.join(",", stayCodes))
                        .queryParam("checkIn", query.checkIn())
                        .queryParam("checkOut", query.checkOut())
                        .queryParam("adults", query.adults())
                        .queryParam("children", query.children())
                        .build())
                .exchangeToMono(this::readOffers)
                .timeout(config.callTimeout())
                .onErrorResume(error -> Mono.just(offerFailure(SupplierErrors.classify(error),
                        error.getClass().getSimpleName())));
    }

    private Mono<SupplierResult> readOffers(ClientResponse response) {
        if (!response.statusCode().is2xxSuccessful()) {
            FailureType type = SupplierErrors.classify(response.statusCode());
            return response.releaseBody()
                    .then(Mono.just(offerFailure(type, "HTTP " + response.statusCode().value())));
        }
        return response.bodyToMono(SupplierBResponses.Search.class)
                .map(this::toOffers)
                // 위와 같은 이유. B는 실패도 200으로 주므로 빈 본문을 성공으로 접으면 더 위험하다.
                .switchIfEmpty(Mono.fromSupplier(
                        () -> offerFailure(FailureType.PARSE_ERROR, "2xx인데 응답 본문이 비어 있다")));
    }

    private SupplierResult toOffers(SupplierBResponses.Search body) {
        if (body == null || body.resultCode() == null) {
            return offerFailure(FailureType.PARSE_ERROR, "resultCode가 없다");
        }
        if (!SUCCESS.equals(body.resultCode())) {
            // HTTP는 200이었다. 여기서 걸러내지 않으면 장애가 "방 0건"으로 나간다.
            return offerFailure(classify(body.resultCode()), body.resultCode() + " " + body.resultMessage());
        }
        if (body.data() == null || body.data().items() == null) {
            return offerFailure(FailureType.PARSE_ERROR, "resultCode는 0000인데 data가 비어 있다");
        }

        List<SupplierOffer> offers = new ArrayList<>();
        int skipped = 0;
        for (SupplierBResponses.Item item : body.data().items()) {
            SupplierOffer offer = toOffer(item);
            if (offer == null) {
                skipped++;
                continue;
            }
            offers.add(offer);
        }

        if (skipped > 0) {
            log.warn("supplier B 재고·요금에서 {}건을 건너뜀 (사용 가능 {}건)", skipped, offers.size());
        }
        return new SupplierResult.Success(SupplierId.B, offers, skipped);
    }

    private SupplierOffer toOffer(SupplierBResponses.Item item) {
        if (isBlank(item.propertyId()) || isBlank(item.roomId())
                || item.inventory() == null || item.inventory().isEmpty()) {
            return null;
        }

        int availableRooms = Integer.MAX_VALUE;
        for (SupplierBResponses.Inventory night : item.inventory()) {
            availableRooms = Math.min(availableRooms, night.remainingRooms());
        }

        return new SupplierOffer(
                item.propertyId(),
                item.propertyName(),
                item.roomId(),
                item.roomName(),
                item.maxOccupancy(),
                availableRooms,
                item.breakfastIncluded(),
                // totalPrice는 이미 기간 전체의 세금 포함 총액이다. 곱하거나 나누지 않는다.
                // 날짜별 분해는 주지 않으므로 dailyBreakdown은 비운다 — 없는 값을 만들지 않는다.
                Price.of(item.totalPrice(), item.currency()));
    }

    private SupplierResult offerFailure(FailureType type, String detail) {
        log.warn("supplier B 재고·요금 조회 실패: {} ({})", type, detail);
        return new SupplierResult.Failure(SupplierId.B, type, detail);
    }

    private Mono<CatalogResult> readCatalog(ClientResponse response) {
        // B의 규약상 실패도 200으로 오지만, 규약을 벗어난 상태 코드가 오면 그것도 실패다.
        if (!response.statusCode().is2xxSuccessful()) {
            FailureType type = SupplierErrors.classify(response.statusCode());
            return response.releaseBody()
                    .then(Mono.just(catalogFailure(type, "HTTP " + response.statusCode().value())));
        }
        return response.bodyToMono(SupplierBResponses.Properties.class)
                .map(this::toCatalog)
                // 2xx인데 본문이 없으면 Reactor는 값 없이 완료한다. 그대로 두면 이 호출이
                // 결과 목록에서 조용히 사라져, 프로토콜 위반이 "숙소 0건"으로 읽힌다.
                .switchIfEmpty(Mono.fromSupplier(
                        () -> catalogFailure(FailureType.PARSE_ERROR, "2xx인데 응답 본문이 비어 있다")));
    }

    private CatalogResult toCatalog(SupplierBResponses.Properties body) {
        if (body == null || body.resultCode() == null) {
            return catalogFailure(FailureType.PARSE_ERROR, "resultCode가 없다");
        }
        if (!SUCCESS.equals(body.resultCode())) {
            return catalogFailure(classify(body.resultCode()), body.resultCode() + " " + body.resultMessage());
        }
        if (body.data() == null || body.data().items() == null) {
            // 성공 코드인데 데이터가 없는 건 공급사 응답이 규약을 깬 것이다.
            return catalogFailure(FailureType.PARSE_ERROR, "resultCode는 0000인데 data가 비어 있다");
        }

        List<SupplierStay> stays = new ArrayList<>();
        int skipped = 0;
        for (SupplierBResponses.Property property : body.data().items()) {
            List<SupplierRoomType> rooms = usableRooms(property);
            if (isBlank(property.propertyId()) || rooms.isEmpty()) {
                skipped++;
                continue;
            }
            stays.add(new SupplierStay(property.propertyId(), property.propertyName(), rooms));
        }

        if (skipped > 0) {
            log.warn("supplier B 숙소 목록에서 {}건을 건너뜀 (사용 가능 {}건)", skipped, stays.size());
        }
        return new CatalogResult.Success(SupplierId.B, stays);
    }

    /**
     * {@code resultCode}를 공통 실패 축으로 옮긴다. A의 HTTP 상태와 여기의 코드가 같은
     * {@code FailureType}으로 접히기 때문에, 호출부는 두 공급사의 실패를 같은 방식으로 다룬다.
     */
    private static FailureType classify(String resultCode) {
        return switch (resultCode) {
            case "E400" -> FailureType.INVALID_REQUEST;
            case "E401" -> FailureType.AUTH;
            case "E429" -> FailureType.RATE_LIMIT;
            default -> FailureType.SUPPLIER_ERROR;
        };
    }

    private static List<SupplierRoomType> usableRooms(SupplierBResponses.Property property) {
        if (property.rooms() == null) {
            return List.of();
        }
        return property.rooms().stream()
                .filter(room -> !isBlank(room.roomId()))
                .map(room -> new SupplierRoomType(room.roomId(), room.roomName(), room.maxOccupancy()))
                .toList();
    }

    private CatalogResult catalogFailure(FailureType type, String detail) {
        log.warn("supplier B 숙소 목록 조회 실패: {} ({})", type, detail);
        return new CatalogResult.Failure(SupplierId.B, type, detail);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
