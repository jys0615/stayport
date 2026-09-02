package io.github.jys0615.stayport.adapter.supplierb;

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
        throw new UnsupportedOperationException("재고·요금 조회는 아직 구현되지 않았다");
    }

    private Mono<CatalogResult> readCatalog(ClientResponse response) {
        // B의 규약상 실패도 200으로 오지만, 규약을 벗어난 상태 코드가 오면 그것도 실패다.
        if (!response.statusCode().is2xxSuccessful()) {
            FailureType type = SupplierErrors.classify(response.statusCode());
            return response.releaseBody()
                    .then(Mono.just(catalogFailure(type, "HTTP " + response.statusCode().value())));
        }
        return response.bodyToMono(SupplierBResponses.Properties.class).map(this::toCatalog);
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
