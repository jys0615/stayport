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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Supplier B 어댑터 — 장애 시에도 HTTP 200을 주고 본문 resultCode로만 실패를 알린다.
 * resultCode 확인을 빠뜨리면 장애가 "결과 0건"으로 처리된다.
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
                // readCatalog와 같은 이유.
                .switchIfEmpty(Mono.fromSupplier(
                        () -> offerFailure(FailureType.PARSE_ERROR, "2xx인데 응답 본문이 비어 있다")));
    }

    private SupplierResult toOffers(SupplierBResponses.Search body) {
        if (body == null || body.resultCode() == null) {
            return offerFailure(FailureType.PARSE_ERROR, "resultCode가 없다");
        }
        if (!SUCCESS.equals(body.resultCode())) {
            return offerFailure(classify(body.resultCode()), body.resultCode() + " " + body.resultMessage());
        }
        if (body.data() == null || body.data().items() == null) {
            return offerFailure(FailureType.PARSE_ERROR, "resultCode는 0000인데 data가 비어 있다");
        }

        List<SupplierOffer> offers = new ArrayList<>();
        Set<List<String>> seen = new HashSet<>();
        int skipped = 0;
        for (SupplierBResponses.Item item : body.data().items()) {
            SupplierOffer offer = toOffer(item);
            if (offer == null) {
                skipped++;
                continue;
            }
            // 같은 응답 안에 같은 (숙소, 객실)이 두 번 오는 것은 규약 이상이다. 첫 건만 쓴다.
            if (!seen.add(List.of(offer.stayCode(), offer.roomCode()))) {
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
                // totalPrice는 기간 전체 총액(세금 포함) — 그대로 쓴다. 날짜별 분해는 미제공이라 null.
                Price.of(item.totalPrice(), item.currency()));
    }

    private SupplierResult offerFailure(FailureType type, String detail) {
        log.warn("supplier B 재고·요금 조회 실패: {} ({})", type, detail);
        return new SupplierResult.Failure(SupplierId.B, type, detail);
    }

    private Mono<CatalogResult> readCatalog(ClientResponse response) {
        // 규약상 실패도 200으로 오지만, 규약 밖 상태 코드도 실패로 다룬다.
        if (!response.statusCode().is2xxSuccessful()) {
            FailureType type = SupplierErrors.classify(response.statusCode());
            return response.releaseBody()
                    .then(Mono.just(catalogFailure(type, "HTTP " + response.statusCode().value())));
        }
        return response.bodyToMono(SupplierBResponses.Properties.class)
                .map(this::toCatalog)
                // 2xx + 빈 본문이면 bodyToMono가 값 없이 완료한다. 성공으로 처리하면 안 된다.
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

    /** resultCode → FailureType. A의 HTTP 상태 분류와 같은 축. */
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
