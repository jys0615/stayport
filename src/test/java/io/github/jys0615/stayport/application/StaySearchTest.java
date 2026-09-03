package io.github.jys0615.stayport.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jys0615.stayport.application.SearchResult.SupplierOutcome;
import io.github.jys0615.stayport.application.port.FailureType;
import io.github.jys0615.stayport.domain.SearchQuery;
import io.github.jys0615.stayport.domain.StayOffer;
import io.github.jys0615.stayport.domain.SupplierId;
import io.github.jys0615.stayport.support.MockSupplierServer;
import io.github.jys0615.stayport.support.SupplierIntegrationTest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 통합 검색의 핵심 흐름과 장애 내성.
 *
 * <p>흉내 서버를 실제로 고장내며 확인한다. 정상 응답만 되는 환경에서는 부분 실패도 타임아웃도
 * 동작을 보일 수 없다.
 */
class StaySearchTest extends SupplierIntegrationTest {

    private static final SearchQuery THREE_NIGHTS =
            new SearchQuery(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);

    @Autowired
    private StaySearchService searchService;

    @Autowired
    private MappingSyncService syncService;

    @BeforeEach
    void syncMappings() {
        syncService.sync();
    }

    private static Map<SupplierId, SupplierOutcome> outcomes(SearchResult result) {
        return result.suppliers().stream()
                .collect(Collectors.toMap(SupplierOutcome::supplier, Function.identity()));
    }

    @Test
    @DisplayName("두 공급사 결과가 하나의 목록으로 합쳐진다")
    void mergesOffersFromBothSuppliers() {
        SearchResult result = searchService.search(THREE_NIGHTS);

        assertThat(result.stays()).hasSize(3);
        assertThat(result.stays()).extracting(StayOffer::supplier)
                .containsExactlyInAnyOrder(SupplierId.A, SupplierId.A, SupplierId.B);
        assertThat(outcomes(result).values()).allSatisfy(outcome ->
                assertThat(outcome.status()).isEqualTo(SupplierStatus.OK));
    }

    @Test
    @DisplayName("응답은 공급사 코드가 아니라 내부 식별자를 담는다")
    void exposesInternalIdentifiersOnly() {
        SearchResult result = searchService.search(THREE_NIGHTS);

        assertThat(result.stays()).allSatisfy(offer -> {
            assertThat(offer.stayId()).isPositive();
            assertThat(offer.roomTypeId()).isPositive();
            assertThat(offer.stayName()).isNotBlank();
            assertThat(offer.roomTypeName()).isNotBlank();
        });
    }

    @Test
    @DisplayName("같은 호텔을 두 공급사가 팔면 서로 다른 내부 숙소 식별자로 나온다")
    void sameHotelFromTwoSuppliersKeepsSeparateIdentifiers() {
        SearchResult result = searchService.search(THREE_NIGHTS);

        List<StayOffer> riverside = result.stays().stream()
                .filter(offer -> offer.stayName().contains("Riverside"))
                .toList();

        assertThat(riverside).hasSize(2);
        assertThat(riverside).extracting(StayOffer::stayId).doesNotHaveDuplicates();
        // 값만 다른 게 아니라 조건이 다르다. 싼 쪽을 고르면 조식 없는 상품을 고르게 된다.
        assertThat(riverside).extracting(StayOffer::breakfastIncluded).containsExactlyInAnyOrder(true, false);
        assertThat(riverside).extracting(offer -> offer.price().totalAmount())
                .containsExactlyInAnyOrder(429_000L, 452_000L);
    }

    @Test
    @DisplayName("재고 0인 상품도 응답에 남는다 — 빼면 '없음'과 '만실'이 구분되지 않는다")
    void soldOutOffersStayInTheResponse() {
        SearchResult result = searchService.search(THREE_NIGHTS);

        StayOffer soldOut = result.stays().stream()
                .filter(offer -> offer.stayName().contains("Namsan"))
                .findFirst()
                .orElseThrow();

        assertThat(soldOut.availableRooms()).isZero();
        assertThat(soldOut.bookable()).isFalse();
    }

    @Test
    @DisplayName("A가 죽어도 B의 결과만으로 응답한다")
    void survivesSupplierAOutage() {
        MockSupplierServer.mode("a", "error");

        SearchResult result = searchService.search(THREE_NIGHTS);

        assertThat(result.stays()).hasSize(1);
        assertThat(result.stays().getFirst().supplier()).isEqualTo(SupplierId.B);
        assertThat(outcomes(result).get(SupplierId.A).status()).isEqualTo(SupplierStatus.FAILED);
        assertThat(outcomes(result).get(SupplierId.B).status()).isEqualTo(SupplierStatus.OK);
    }

    @Test
    @DisplayName("B의 HTTP 200 + resultCode 실패도 부분 실패로 드러난다")
    void survivesSupplierBOutageSignalledInBody() {
        MockSupplierServer.mode("b", "error");

        SearchResult result = searchService.search(THREE_NIGHTS);

        assertThat(result.stays()).hasSize(2);
        assertThat(result.stays()).allSatisfy(offer ->
                assertThat(offer.supplier()).isEqualTo(SupplierId.A));
        assertThat(outcomes(result).get(SupplierId.B).status()).isEqualTo(SupplierStatus.FAILED);
        assertThat(outcomes(result).get(SupplierId.B).failures()).containsKey(FailureType.SUPPLIER_ERROR);
    }

    @Test
    @DisplayName("무응답 공급사는 예산 안에 끊기고, 나머지로 응답한다")
    void abandonsUnresponsiveSupplierWithinBudget() {
        MockSupplierServer.mode("a", "no-response");

        long startedAt = System.nanoTime();
        SearchResult result = searchService.search(THREE_NIGHTS);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(result.stays()).hasSize(1);
        assertThat(outcomes(result).get(SupplierId.A).failures()).containsEntry(FailureType.TIMEOUT, 1);
        assertThat(outcomes(result).get(SupplierId.B).status()).isEqualTo(SupplierStatus.OK);
        // 흉내 서버는 30초를 잔다. 검색 전체 예산은 3.5초이므로 그 안에 끝나야 한다.
        assertThat(elapsed).isLessThan(Duration.ofMillis(3_800));
    }

    @Test
    @DisplayName("두 공급사가 모두 죽으면 빈 목록과 함께 그 사실이 드러난다")
    void bothSuppliersDownYieldsEmptyResultWithReasons() {
        MockSupplierServer.mode("a", "error");
        MockSupplierServer.mode("b", "error");

        SearchResult result = searchService.search(THREE_NIGHTS);

        assertThat(result.stays()).isEmpty();
        assertThat(result.suppliers()).allSatisfy(outcome -> {
            assertThat(outcome.status()).isEqualTo(SupplierStatus.FAILED);
            assertThat(outcome.failures()).isNotEmpty();
        });
    }

    @Test
    @DisplayName("병렬 호출이므로 전체 지연은 가장 느린 공급사에 수렴한다")
    void parallelCallsDoNotAddUpLatency() {
        MockSupplierServer.mode("a", "no-response");
        MockSupplierServer.mode("b", "no-response");

        long startedAt = System.nanoTime();
        searchService.search(THREE_NIGHTS);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        // 순차로 불렀다면 3초 + 3초 = 6초가 된다. 병렬이면 max(3s)에 머문다.
        assertThat(elapsed).isLessThan(Duration.ofMillis(3_800));
    }

    @Test
    @DisplayName("응답 순서는 실행마다 같다 — 병렬 호출의 완료 순서가 새어 나오지 않는다")
    void responseOrderIsStableAcrossRuns() {
        List<List<Long>> orders = List.of(
                searchService.search(THREE_NIGHTS).stays().stream().map(StayOffer::stayId).toList(),
                searchService.search(THREE_NIGHTS).stays().stream().map(StayOffer::stayId).toList(),
                searchService.search(THREE_NIGHTS).stays().stream().map(StayOffer::stayId).toList());

        assertThat(orders).allMatch(order -> order.equals(orders.getFirst()));
        assertThat(orders.getFirst()).isSorted();
    }
}
