package io.github.jys0615.stayport.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jys0615.stayport.application.port.SupplierAdapter;
import io.github.jys0615.stayport.application.port.SupplierOffer;
import io.github.jys0615.stayport.application.port.SupplierResult;
import io.github.jys0615.stayport.domain.SearchQuery;
import io.github.jys0615.stayport.domain.SupplierId;
import io.github.jys0615.stayport.support.MockSupplierServer;
import io.github.jys0615.stayport.support.SupplierIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 두 공급사의 재고·요금 응답이 같은 표준 형태로 접히는지 확인한다.
 *
 * <p>여기서 쓰는 기대값은 스펙 문서의 설명이 아니라 흉내 서버가 실제로 주는 응답에서 나온 것이다.
 * 문서는 의도를 말하고 응답은 사실을 말한다.
 */
class SupplierOffersTest extends SupplierIntegrationTest {

    /** 3박: 09-01 ~ 09-04 (체크아웃일은 숙박일이 아니다) */
    private static final SearchQuery THREE_NIGHTS =
            new SearchQuery(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);

    @Autowired
    private List<SupplierAdapter> adapters;

    private SupplierAdapter adapter(SupplierId supplier) {
        return adapters.stream()
                .filter(a -> a.supplier() == supplier)
                .findFirst()
                .orElseThrow();
    }

    private Map<String, SupplierOffer> offersOf(SupplierId supplier, String... codes) {
        SupplierResult result = adapter(supplier).fetchOffers(THREE_NIGHTS, List.of(codes)).block();
        assertThat(result).isInstanceOf(SupplierResult.Success.class);
        return ((SupplierResult.Success) result).offers().stream()
                .collect(Collectors.toMap(SupplierOffer::stayCode, Function.identity()));
    }

    @Test
    @DisplayName("A: 총액은 날짜별 (nightlyRate + taxAmount)의 합이다")
    void supplierATotalIsSumOfNightlyPlusTax() {
        SupplierOffer offer = offersOf(SupplierId.A, "A-10023", "A-10044").get("A-10023");

        // (120000+12000) + (150000+15000) + (120000+12000)
        assertThat(offer.price().totalAmount()).isEqualTo(429_000L);
        assertThat(offer.price().currency()).isEqualTo("KRW");
    }

    @Test
    @DisplayName("A: 날짜별 분해를 보존하고 net·tax까지 채운다")
    void supplierAKeepsDailyBreakdown() {
        SupplierOffer offer = offersOf(SupplierId.A, "A-10023").get("A-10023");

        assertThat(offer.price().dailyBreakdown()).hasSize(3);
        assertThat(offer.price().dailyBreakdown().getFirst().date()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(offer.price().dailyBreakdown().getFirst().grossAmount()).isEqualTo(132_000L);
        assertThat(offer.price().dailyBreakdown().getFirst().netAmount()).isEqualTo(120_000L);
        assertThat(offer.price().dailyBreakdown().getFirst().taxAmount()).isEqualTo(12_000L);
    }

    @Test
    @DisplayName("B: totalPrice는 기간 총액이므로 그대로 쓴다 — 1박 요금으로 오해하면 3배가 된다")
    void supplierBTotalIsUsedAsIs() {
        SupplierOffer offer = offersOf(SupplierId.B, "B77120").get("B77120");

        assertThat(offer.price().totalAmount()).isEqualTo(452_000L);
    }

    @Test
    @DisplayName("B: 날짜별 분해를 주지 않으므로 dailyBreakdown은 비어 있다")
    void supplierBHasNoDailyBreakdown() {
        SupplierOffer offer = offersOf(SupplierId.B, "B77120").get("B77120");

        assertThat(offer.price().dailyBreakdown()).isNull();
    }

    @Test
    @DisplayName("재고는 기간 내 최솟값이다 — 하루라도 0이면 예약 불가")
    void availableRoomsIsMinimumOverStay() {
        Map<String, SupplierOffer> a = offersOf(SupplierId.A, "A-10023", "A-10044");

        assertThat(a.get("A-10023").availableRooms()).isEqualTo(1);   // 3, 1, 5
        assertThat(a.get("A-10044").availableRooms()).isZero();       // 2, 0, 4
        assertThat(offersOf(SupplierId.B, "B77120").get("B77120").availableRooms()).isEqualTo(1);
    }

    @Test
    @DisplayName("조식 조건 차이가 결과에 보존된다 — 같은 객실이라도 병합하면 근거가 사라진다")
    void breakfastDifferenceSurvives() {
        assertThat(offersOf(SupplierId.A, "A-10023").get("A-10023").breakfastIncluded()).isFalse();
        assertThat(offersOf(SupplierId.B, "B77120").get("B77120").breakfastIncluded()).isTrue();
    }

    @Test
    @DisplayName("A: HTTP 503은 실패다")
    void supplierAHttpErrorIsFailure() {
        MockSupplierServer.mode("a", "error");

        SupplierResult result = adapter(SupplierId.A).fetchOffers(THREE_NIGHTS, List.of("A-10023")).block();

        assertThat(result).isInstanceOf(SupplierResult.Failure.class);
        assertThat(((SupplierResult.Failure) result).type().name()).isEqualTo("SUPPLIER_ERROR");
    }

    @Test
    @DisplayName("B: HTTP 200이어도 resultCode가 0000이 아니면 실패다")
    void supplierBTwoHundredWithErrorCodeIsFailure() {
        MockSupplierServer.mode("b", "error");

        SupplierResult result = adapter(SupplierId.B).fetchOffers(THREE_NIGHTS, List.of("B77120")).block();

        assertThat(result).isInstanceOf(SupplierResult.Failure.class);
        assertThat(((SupplierResult.Failure) result).type().name()).isEqualTo("SUPPLIER_ERROR");
    }

    @Test
    @DisplayName("같은 응답에 같은 (숙소, 객실)이 두 번 오면 첫 건만 쓰고 스킵으로 센다")
    void duplicateItemsInOneResponseKeepFirstAndCount() {
        MockSupplierServer.mode("a", "duplicate-items");

        SupplierResult result = adapter(SupplierId.A).fetchOffers(THREE_NIGHTS, List.of("A-10023")).block();

        assertThat(result).isInstanceOf(SupplierResult.Success.class);
        SupplierResult.Success success = (SupplierResult.Success) result;
        assertThat(success.offers()).hasSize(1);
        assertThat(success.skippedItems()).isEqualTo(1);
        // 첫 건이 이긴다 — 두 번째 건의 값(총액 1,098,000·재고 9)이 아니라 첫 건의 값이어야 한다.
        assertThat(success.offers().getFirst().price().totalAmount()).isEqualTo(132_000L);
        assertThat(success.offers().getFirst().availableRooms()).isEqualTo(3);
    }

    @Test
    @DisplayName("무응답은 타임아웃으로 접힌다")
    void noResponseBecomesTimeout() {
        MockSupplierServer.mode("a", "no-response");

        long startedAt = System.nanoTime();
        SupplierResult result = adapter(SupplierId.A).fetchOffers(THREE_NIGHTS, List.of("A-10023")).block();
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        assertThat(result).isInstanceOf(SupplierResult.Failure.class);
        assertThat(((SupplierResult.Failure) result).type().name()).isEqualTo("TIMEOUT");
        // 공급사 호출 제한은 3초다. 흉내 서버는 30초를 자므로, 우리 쪽 제한이 발동해야 한다.
        assertThat(elapsedMillis).isLessThan(4_000L);
    }
}
