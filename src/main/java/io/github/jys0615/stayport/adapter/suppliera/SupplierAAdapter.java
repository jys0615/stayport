package io.github.jys0615.stayport.adapter.suppliera;

import io.github.jys0615.stayport.adapter.SupplierErrors;
import io.github.jys0615.stayport.application.port.CatalogResult;
import io.github.jys0615.stayport.application.port.FailureType;
import io.github.jys0615.stayport.application.port.SupplierAdapter;
import io.github.jys0615.stayport.application.port.SupplierResult;
import io.github.jys0615.stayport.application.port.SupplierRoomType;
import io.github.jys0615.stayport.application.port.SupplierStay;
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
import reactor.core.publisher.Mono;

/**
 * Supplier A 어댑터. 실패를 HTTP 상태 코드로 알리는 쪽이다.
 */
@Component
class SupplierAAdapter implements SupplierAdapter {

    private static final Logger log = LoggerFactory.getLogger(SupplierAAdapter.class);

    private final WebClient client;
    private final StayportProperties.Supplier config;

    SupplierAAdapter(SupplierWebClients clients, StayportProperties properties) {
        this.client = clients.forSupplier(SupplierId.A);
        this.config = properties.suppliers().get(SupplierId.A);
    }

    @Override
    public SupplierId supplier() {
        return SupplierId.A;
    }

    @Override
    public Mono<CatalogResult> fetchCatalog() {
        return client.get()
                .uri(config.paths().catalog())
                .exchangeToMono(this::readCatalog)
                .timeout(config.callTimeout())
                .onErrorResume(error -> Mono.just(catalogFailure(SupplierErrors.classify(error), error)));
    }

    @Override
    public Mono<SupplierResult> fetchOffers(SearchQuery query, List<String> stayCodes) {
        throw new UnsupportedOperationException("재고·요금 조회는 아직 구현되지 않았다");
    }

    private Mono<CatalogResult> readCatalog(ClientResponse response) {
        if (!response.statusCode().is2xxSuccessful()) {
            FailureType type = SupplierErrors.classify(response.statusCode());
            String detail = "HTTP " + response.statusCode().value();
            // 본문을 읽지 않고 버리면 커넥션이 반납되지 않는다.
            return response.releaseBody().then(Mono.just(catalogFailure(type, detail)));
        }
        return response.bodyToMono(SupplierAResponses.Hotels.class).map(this::toCatalog);
    }

    private CatalogResult toCatalog(SupplierAResponses.Hotels body) {
        if (body == null || body.items() == null) {
            return catalogFailure(FailureType.PARSE_ERROR, "items가 없다");
        }

        List<SupplierStay> stays = new ArrayList<>();
        int skipped = 0;
        for (SupplierAResponses.Hotel hotel : body.items()) {
            List<SupplierRoomType> roomTypes = usableRoomTypes(hotel);
            if (isBlank(hotel.hotelCode()) || roomTypes.isEmpty()) {
                skipped++;
                continue;
            }
            stays.add(new SupplierStay(hotel.hotelCode(), hotel.hotelName(), roomTypes));
        }

        // 목록 한 건이 이상해도 나머지는 쓴다. 대신 버린 사실은 남긴다.
        if (skipped > 0) {
            log.warn("supplier A 숙소 목록에서 {}건을 건너뜀 (사용 가능 {}건)", skipped, stays.size());
        }
        return new CatalogResult.Success(SupplierId.A, stays);
    }

    private static List<SupplierRoomType> usableRoomTypes(SupplierAResponses.Hotel hotel) {
        if (hotel.roomTypes() == null) {
            return List.of();
        }
        return hotel.roomTypes().stream()
                .filter(room -> !isBlank(room.roomTypeCode()))
                .map(room -> new SupplierRoomType(room.roomTypeCode(), room.roomTypeName(), room.maxOccupancy()))
                .toList();
    }

    private CatalogResult catalogFailure(FailureType type, String detail) {
        log.warn("supplier A 숙소 목록 조회 실패: {} ({})", type, detail);
        return new CatalogResult.Failure(SupplierId.A, type, detail);
    }

    private CatalogResult catalogFailure(FailureType type, Throwable error) {
        log.warn("supplier A 숙소 목록 조회 실패: {} ({})", type, error.toString());
        return new CatalogResult.Failure(SupplierId.A, type, error.getClass().getSimpleName());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
