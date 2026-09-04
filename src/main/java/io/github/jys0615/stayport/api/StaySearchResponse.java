package io.github.jys0615.stayport.api;

import io.github.jys0615.stayport.application.SearchResult;
import io.github.jys0615.stayport.application.SupplierStatus;
import io.github.jys0615.stayport.application.port.FailureType;
import io.github.jys0615.stayport.domain.DailyRate;
import io.github.jys0615.stayport.domain.SearchQuery;
import io.github.jys0615.stayport.domain.StayOffer;
import io.github.jys0615.stayport.domain.SupplierId;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 통합 검색 응답. 도메인 레코드를 직접 노출하지 않는다 — 외부 계약 분리.
 *
 * @param nights    숙박일 수. totalAmount가 기간 전체 금액이라 해석에 필요
 * @param suppliers 공급사별 상태 — 부분 실패가 여기 드러난다
 */
public record StaySearchResponse(
        LocalDate checkIn,
        LocalDate checkOut,
        int nights,
        int adults,
        int children,
        List<Stay> stays,
        List<Supplier> suppliers) {

    static StaySearchResponse of(SearchQuery query, SearchResult result) {
        return new StaySearchResponse(
                query.checkIn(),
                query.checkOut(),
                query.nights(),
                query.adults(),
                query.children(),
                result.stays().stream().map(Stay::of).toList(),
                result.suppliers().stream().map(Supplier::of).toList());
    }

    /**
     * @param availableRooms 기간 전체 예약 가능 수. 0 = 예약 불가 (bookable로도 제공)
     */
    public record Stay(
            long stayId,
            String stayName,
            long roomTypeId,
            String roomTypeName,
            int maxOccupancy,
            int availableRooms,
            boolean bookable,
            boolean breakfastIncluded,
            SupplierId supplier,
            Price price) {

        static Stay of(StayOffer offer) {
            return new Stay(
                    offer.stayId(),
                    offer.stayName(),
                    offer.roomTypeId(),
                    offer.roomTypeName(),
                    offer.maxOccupancy(),
                    offer.availableRooms(),
                    offer.bookable(),
                    offer.breakfastIncluded(),
                    offer.supplier(),
                    Price.of(offer));
        }
    }

    /**
     * @param totalAmount    기간 전체 총액, 세금 포함(gross)
     * @param dailyBreakdown 주지 않는 공급사에서는 null
     */
    public record Price(long totalAmount, String currency, List<Night> dailyBreakdown) {

        static Price of(StayOffer offer) {
            List<DailyRate> breakdown = offer.price().dailyBreakdown();
            return new Price(
                    offer.price().totalAmount(),
                    offer.price().currency(),
                    breakdown == null ? null : breakdown.stream().map(Night::of).toList());
        }
    }

    /** @param netAmount net·tax 분해를 주지 않는 공급사에서는 null */
    public record Night(LocalDate date, long grossAmount, Long netAmount, Long taxAmount) {

        static Night of(DailyRate rate) {
            return new Night(rate.date(), rate.grossAmount(), rate.netAmount(), rate.taxAmount());
        }
    }

    /**
     * @param skippedItems 형태가 깨졌거나 매핑이 없어 버린 상품 수
     * @param failures     실패 유형별 개수 — 재시도 판단의 근거
     */
    public record Supplier(
            SupplierId supplier,
            SupplierStatus status,
            int returnedOffers,
            int skippedItems,
            int failedChunks,
            Map<FailureType, Integer> failures) {

        static Supplier of(SearchResult.SupplierOutcome outcome) {
            return new Supplier(
                    outcome.supplier(),
                    outcome.status(),
                    outcome.returnedOffers(),
                    outcome.skippedItems(),
                    outcome.failedChunks(),
                    outcome.failures());
        }
    }
}
