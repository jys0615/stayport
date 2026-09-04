package io.github.jys0615.stayport.adapter.suppliera;

import io.github.jys0615.stayport.adapter.ChunkedOffers;
import io.github.jys0615.stayport.adapter.SupplierErrors;
import io.github.jys0615.stayport.application.port.CatalogResult;
import io.github.jys0615.stayport.application.port.FailureType;
import io.github.jys0615.stayport.application.port.SupplierAdapter;
import io.github.jys0615.stayport.application.port.SupplierOffer;
import io.github.jys0615.stayport.application.port.SupplierResult;
import io.github.jys0615.stayport.application.port.SupplierRoomType;
import io.github.jys0615.stayport.application.port.SupplierStay;
import io.github.jys0615.stayport.domain.DailyRate;
import io.github.jys0615.stayport.domain.Price;
import io.github.jys0615.stayport.domain.SearchQuery;
import io.github.jys0615.stayport.domain.SupplierId;
import io.github.jys0615.stayport.infra.StayportProperties;
import io.github.jys0615.stayport.infra.SupplierWebClients;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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

/** Supplier A 어댑터 — 실패를 HTTP 상태 코드로 알린다. */
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
        if (stayCodes.isEmpty()) {
            // 물어볼 것이 없으면 부르지 않는다. 빈 목록으로 호출하면 공급사가 400을 준다.
            return Mono.just(SupplierResult.Success.of(SupplierId.A, List.of()));
        }
        return Flux.fromIterable(ChunkedOffers.split(stayCodes, config.chunkSize()))
                .flatMap(chunk -> fetchChunk(query, chunk))
                .collectList()
                .map(results -> ChunkedOffers.merge(SupplierId.A, results));
    }

    /** 한도 이하의 코드 묶음 하나를 조회한다. 실패는 예외가 아니라 이 묶음의 결과로 돌아온다. */
    private Mono<SupplierResult> fetchChunk(SearchQuery query, List<String> stayCodes) {
        return client.get()
                .uri(builder -> builder.path(config.paths().availability())
                        .queryParam("hotelCodes", String.join(",", stayCodes))
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
            String detail = "HTTP " + response.statusCode().value();
            return response.releaseBody().then(Mono.just(offerFailure(type, detail)));
        }
        return response.bodyToMono(SupplierAResponses.Availability.class)
                .map(this::toOffers)
                // readCatalog와 같은 이유.
                .switchIfEmpty(Mono.fromSupplier(
                        () -> offerFailure(FailureType.PARSE_ERROR, "2xx인데 응답 본문이 비어 있다")));
    }

    private SupplierResult toOffers(SupplierAResponses.Availability body) {
        if (body == null || body.items() == null) {
            return offerFailure(FailureType.PARSE_ERROR, "items가 없다");
        }

        List<SupplierOffer> offers = new ArrayList<>();
        Set<List<String>> seen = new HashSet<>();
        int skipped = 0;
        for (SupplierAResponses.Item item : body.items()) {
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
            log.warn("supplier A 재고·요금에서 {}건을 건너뜀 (사용 가능 {}건)", skipped, offers.size());
        }
        return new SupplierResult.Success(SupplierId.A, offers, skipped);
    }

    /** 상품 1건 정규화. 형태가 이상하면 null — 그 건만 스킵된다. */
    private SupplierOffer toOffer(SupplierAResponses.Item item) {
        if (isBlank(item.hotelCode()) || isBlank(item.roomTypeCode())
                || item.dailyRates() == null || item.dailyRates().isEmpty()) {
            return null;
        }

        List<DailyRate> breakdown = new ArrayList<>(item.dailyRates().size());
        long total = 0;
        int availableRooms = Integer.MAX_VALUE;

        for (SupplierAResponses.DailyRate rate : item.dailyRates()) {
            LocalDate date = parseDate(rate.date());
            if (date == null) {
                return null;
            }
            // nightlyRate는 net — 고객 결제액은 net + tax.
            breakdown.add(DailyRate.decomposed(date, rate.nightlyRate(), rate.taxAmount()));
            total += rate.nightlyRate() + rate.taxAmount();
            availableRooms = Math.min(availableRooms, rate.remainingRooms());
        }

        return new SupplierOffer(
                item.hotelCode(),
                item.hotelName(),
                item.roomTypeCode(),
                item.roomTypeName(),
                item.maxOccupancy(),
                availableRooms,
                item.breakfastIncluded(),
                new Price(total, item.currency(), breakdown));
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            log.warn("supplier A 응답의 날짜를 읽을 수 없다: {}", value);
            return null;
        }
    }

    private SupplierResult offerFailure(FailureType type, String detail) {
        log.warn("supplier A 재고·요금 조회 실패: {} ({})", type, detail);
        return new SupplierResult.Failure(SupplierId.A, type, detail);
    }

    private Mono<CatalogResult> readCatalog(ClientResponse response) {
        if (!response.statusCode().is2xxSuccessful()) {
            FailureType type = SupplierErrors.classify(response.statusCode());
            String detail = "HTTP " + response.statusCode().value();
            // releaseBody 없이 버리면 커넥션이 반납되지 않는다.
            return response.releaseBody().then(Mono.just(catalogFailure(type, detail)));
        }
        return response.bodyToMono(SupplierAResponses.Hotels.class)
                .map(this::toCatalog)
                // 2xx + 빈 본문이면 bodyToMono가 값 없이 완료한다. 성공으로 처리하면 안 된다.
                .switchIfEmpty(Mono.fromSupplier(
                        () -> catalogFailure(FailureType.PARSE_ERROR, "2xx인데 응답 본문이 비어 있다")));
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
