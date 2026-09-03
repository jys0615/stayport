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
 * 통합 검색 응답.
 *
 * <p>도메인 레코드를 그대로 내보내지 않는다. 도메인 모델을 고칠 때마다 외부 계약이 함께 흔들리면
 * 안 되고, 응답에는 도메인에 없는 것(요청 조건 되돌려주기, 숙박일 수)도 실린다.
 *
 * @param checkIn   요청한 체크인일. 클라이언트가 어떤 조건의 결과인지 확인할 수 있게 되돌려준다
 * @param nights    숙박일 수. 총액이 기간 전체 금액이므로 며칠분인지 알아야 값을 해석할 수 있다
 * @param stays     검색 결과. 내부 식별자 순으로 고정된다
 * @param suppliers 공급사별로 어디까지 받았는지. 부분 실패의 사실이 여기 드러난다
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
     * @param availableRooms 요청 기간 전체를 예약할 수 있는 객실 수. 0이면 예약 불가
     * @param bookable       {@code availableRooms > 0}. 판정 규칙을 클라이언트가 다시 쓰지 않도록 함께 준다
     * @param maxOccupancy   객실 1실 기준 최대 수용 인원. 인원 조건 판단의 재료다
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
     * @param totalAmount    숙박 기간 전체 총액, 세금 포함(gross). 두 공급사에서 모두 산출 가능한 값
     * @param dailyBreakdown 날짜별 분해. 주지 않는 공급사에서는 {@code null}이다
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

    /**
     * @param grossAmount 그날 고객이 내는 금액 (세금 포함)
     * @param netAmount   세금 별도 금액. 분해를 주지 않는 공급사에서는 {@code null}
     * @param taxAmount   세금. 위와 같다
     */
    public record Night(LocalDate date, long grossAmount, Long netAmount, Long taxAmount) {

        static Night of(DailyRate rate) {
            return new Night(rate.date(), rate.grossAmount(), rate.netAmount(), rate.taxAmount());
        }
    }

    /**
     * @param status         OK / PARTIAL / FAILED / NO_MAPPING
     * @param returnedOffers 이 공급사에서 결과로 들어간 상품 수
     * @param skippedItems   형태가 깨졌거나 매핑이 없어 버린 상품 수. 조용한 손실을 드러낸다
     * @param failedChunks   나눠 부른 것 중 실패한 묶음 수
     * @param failures       실패 유형별 개수. 하나로 요약하지 않는다 — 재시도 판단의 근거다
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
