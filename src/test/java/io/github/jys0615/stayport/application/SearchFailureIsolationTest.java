package io.github.jys0615.stayport.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jys0615.stayport.application.SearchResult.SupplierOutcome;
import io.github.jys0615.stayport.application.port.FailureType;
import io.github.jys0615.stayport.domain.SearchQuery;
import io.github.jys0615.stayport.domain.StayOffer;
import io.github.jys0615.stayport.domain.SupplierId;
import io.github.jys0615.stayport.support.MockSupplierServer;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 한 공급사의 실패가 다른 공급사의 결과를 지우지 않는지 확인한다.
 *
 * <p>검색 전체 예산을 <b>800ms로 낮춰서</b> 돌린다. 운영 값(3.5초)에서는 어댑터별 제한(3초)이
 * 항상 먼저 걸려서 전체 예산이 발동하는 경로를 밟을 수 없다. 즉 그 경로의 버그를 테스트로
 * 잡으려면 예산을 어댑터 제한보다 짧게 만들어야 한다.
 *
 * <p>이 설정에서 A를 무응답으로 두면 A는 예산을 넘기고 B는 곧바로 답한다. 예상 동작은
 * "B의 결과 + A는 TIMEOUT"이다. 예산 초과를 한 덩어리로 처리하면 B의 결과까지 사라진다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "stayport.search.total-budget=800ms")
@ActiveProfiles("test")
class SearchFailureIsolationTest {

    private static final SearchQuery THREE_NIGHTS =
            new SearchQuery(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);

    @DynamicPropertySource
    static void supplierEndpoints(DynamicPropertyRegistry registry) {
        String baseUrl = MockSupplierServer.baseUrl();
        registry.add("stayport.suppliers.a.base-url", () -> baseUrl);
        registry.add("stayport.suppliers.b.base-url", () -> baseUrl);
    }

    @Autowired
    private StaySearchService searchService;

    @Autowired
    private MappingSyncService syncService;

    @BeforeEach
    void resetAndSync() {
        MockSupplierServer.reset();
        syncService.sync();
    }

    private static Map<SupplierId, SupplierOutcome> outcomes(SearchResult result) {
        return result.suppliers().stream()
                .collect(Collectors.toMap(SupplierOutcome::supplier, Function.identity()));
    }

    @Test
    @DisplayName("전체 예산을 넘겨도 이미 도착한 공급사의 결과는 남는다")
    void budgetOverrunKeepsResultsThatAlreadyArrived() {
        MockSupplierServer.mode("a", "no-response");

        SearchResult result = searchService.search(THREE_NIGHTS);

        // B는 예산 안에 답했다. A가 늦었다고 B의 결과가 사라지면 안 된다.
        assertThat(result.stays())
                .isNotEmpty()
                .allSatisfy(offer -> assertThat(offer.supplier()).isEqualTo(SupplierId.B));
        assertThat(outcomes(result).get(SupplierId.B).status()).isEqualTo(SupplierStatus.OK);

        // 예산 안에 답하지 않은 공급사는 응답에서 빠지지 않고 타임아웃으로 남는다.
        assertThat(outcomes(result).get(SupplierId.A).status()).isEqualTo(SupplierStatus.FAILED);
        assertThat(outcomes(result).get(SupplierId.A).failures()).containsEntry(FailureType.TIMEOUT, 1);
    }

    @Test
    @DisplayName("예산을 넘긴 공급사도 응답 목록에서 빠지지 않는다")
    void everyQueriedSupplierAppearsInTheResponse() {
        MockSupplierServer.mode("a", "no-response");

        SearchResult result = searchService.search(THREE_NIGHTS);

        assertThat(result.suppliers()).extracting(SupplierOutcome::supplier)
                .containsExactlyInAnyOrder(SupplierId.A, SupplierId.B);
    }

    @Test
    @DisplayName("양쪽이 다 늦으면 빈 목록이지만 두 공급사가 모두 타임아웃으로 남는다")
    void bothSlowSuppliersAreBothReported() {
        MockSupplierServer.mode("a", "no-response");
        MockSupplierServer.mode("b", "no-response");

        SearchResult result = searchService.search(THREE_NIGHTS);

        assertThat(result.stays()).isEmpty();
        assertThat(result.suppliers()).hasSize(2);
        assertThat(result.suppliers()).allSatisfy(outcome -> {
            assertThat(outcome.status()).isEqualTo(SupplierStatus.FAILED);
            assertThat(outcome.failures()).containsEntry(FailureType.TIMEOUT, 1);
        });
    }

    @Test
    @DisplayName("2xx인데 본문이 빈 응답은 성공이 아니다 — '재고 0건'으로 둔갑하면 안 된다")
    void emptyResponseBodyIsNotSuccess() {
        MockSupplierServer.mode("a", "empty-body");

        SearchResult result = searchService.search(THREE_NIGHTS);

        SupplierOutcome a = outcomes(result).get(SupplierId.A);
        assertThat(a.status()).isEqualTo(SupplierStatus.FAILED);
        assertThat(a.failures()).containsEntry(FailureType.PARSE_ERROR, 1);
        // 빈 본문을 성공으로 접으면 여기서 status=OK, returnedOffers=0 이 나왔을 것이다.
        assertThat(a.returnedOffers()).isZero();

        // B는 정상이므로 B의 결과는 그대로 나온다.
        assertThat(result.stays())
                .isNotEmpty()
                .allSatisfy(offer -> assertThat(offer.supplier()).isEqualTo(SupplierId.B));
    }

    @Test
    @DisplayName("양쪽이 빈 본문이면 두 공급사 모두 PARSE_ERROR")
    void emptyBodyFromBothSuppliersFailsBoth() {
        MockSupplierServer.mode("a", "empty-body");
        MockSupplierServer.mode("b", "empty-body");

        SearchResult result = searchService.search(THREE_NIGHTS);

        assertThat(result.stays()).isEmpty();
        assertThat(result.suppliers()).allSatisfy(outcome ->
                assertThat(outcome.failures()).containsEntry(FailureType.PARSE_ERROR, 1));
    }

    @Test
    @DisplayName("빈 본문은 숙소 목록 동기화에서도 실패로 잡힌다")
    void emptyBodyAlsoFailsCatalogSync() {
        MockSupplierServer.mode("a", "empty-body");

        SyncReport report = syncService.sync();

        assertThat(report.suppliers())
                .filteredOn(supplier -> supplier.supplier() == SupplierId.A)
                .singleElement()
                .satisfies(supplier -> {
                    assertThat(supplier.status()).isEqualTo("FAILED");
                    assertThat(supplier.failureType()).isEqualTo(FailureType.PARSE_ERROR);
                });
    }

    @Test
    @DisplayName("정상 복구되면 두 공급사 결과가 다시 다 나온다")
    void recoversWhenSuppliersComeBack() {
        MockSupplierServer.mode("a", "no-response");
        assertThat(searchService.search(THREE_NIGHTS).stays()).hasSize(1);

        MockSupplierServer.mode("a", "normal");
        SearchResult result = searchService.search(THREE_NIGHTS);

        assertThat(result.stays()).hasSize(3);
        assertThat(result.stays()).extracting(StayOffer::supplier)
                .containsExactlyInAnyOrder(SupplierId.A, SupplierId.A, SupplierId.B);
    }
}
