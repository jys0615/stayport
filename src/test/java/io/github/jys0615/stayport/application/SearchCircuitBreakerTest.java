package io.github.jys0615.stayport.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jys0615.stayport.application.SearchResult.SupplierOutcome;
import io.github.jys0615.stayport.application.port.FailureType;
import io.github.jys0615.stayport.application.port.MappingStore;
import io.github.jys0615.stayport.domain.SearchQuery;
import io.github.jys0615.stayport.domain.SupplierId;
import io.github.jys0615.stayport.infra.SupplierCircuitBreakers;
import io.github.jys0615.stayport.support.MockSupplierServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
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
 * 반복 실패하는 공급사를 잠시 부르지 않는지. 시간에 기대면 CI에서 흔들리므로 대기 시간을 길게 두고
 * 반열림 전이는 직접 시킨다 — 재는 것은 "얼마나 기다리는가"가 아니라 상태 전이의 결과다.
 *
 * <p>임계값을 낮춰 잡은 전용 컨텍스트다. 다른 테스트는 서킷이 열리지 않는 설정을 쓴다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:circuit-e2e;DB_CLOSE_DELAY=-1",
            "stayport.circuit-breaker.sliding-window-size=2",
            "stayport.circuit-breaker.minimum-calls=2",
            "stayport.circuit-breaker.failure-rate-threshold=50",
            "stayport.circuit-breaker.wait-duration-in-open-state=60s",
            "stayport.circuit-breaker.permitted-calls-in-half-open-state=1"
        })
@ActiveProfiles("test")
class SearchCircuitBreakerTest {

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
    private MappingStore mappingStore;

    @Autowired
    private SupplierCircuitBreakers circuitBreakers;

    @BeforeEach
    void resetState() {
        MockSupplierServer.reset();
        circuitBreakers.forSupplier(SupplierId.A).reset();
        long stayId = mappingStore.upsertStay(SupplierId.A, "A-10023", "Riverside Hotel Seoul");
        mappingStore.upsertRoomType(SupplierId.A, "A-10023", "DLX-TWN", stayId, "Deluxe Twin", 2);
    }

    @Test
    @DisplayName("반복 실패한 공급사는 서킷이 열려 더 부르지 않고, 그 사실이 CIRCUIT_OPEN으로 남는다")
    void repeatedFailuresOpenTheCircuit() {
        MockSupplierServer.mode("a", "error");

        // 창이 2건이므로 두 번 실패하면 열린다.
        assertThat(failureTypeOf(searchService.search(THREE_NIGHTS))).isEqualTo(FailureType.SUPPLIER_ERROR);
        assertThat(failureTypeOf(searchService.search(THREE_NIGHTS))).isEqualTo(FailureType.SUPPLIER_ERROR);

        assertThat(circuitBreakers.forSupplier(SupplierId.A).getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
        // 세 번째 검색은 공급사를 부르지 않는다 — 실패 사유가 바뀌는 것으로 드러난다.
        assertThat(failureTypeOf(searchService.search(THREE_NIGHTS))).isEqualTo(FailureType.CIRCUIT_OPEN);
    }

    @Test
    @DisplayName("서킷이 열려도 다른 공급사의 검색은 그대로 응답한다")
    void openCircuitDoesNotAffectOtherSuppliers() {
        long stayId = mappingStore.upsertStay(SupplierId.B, "B77120", "Riverside Hotel Seoul");
        mappingStore.upsertRoomType(SupplierId.B, "B77120", "R-401", stayId, "Deluxe Twin Room", 2);
        MockSupplierServer.mode("a", "error");

        searchService.search(THREE_NIGHTS);
        searchService.search(THREE_NIGHTS);
        SearchResult result = searchService.search(THREE_NIGHTS);

        Map<SupplierId, SupplierOutcome> outcomes = result.suppliers().stream()
                .collect(Collectors.toMap(SupplierOutcome::supplier, Function.identity()));
        assertThat(outcomes.get(SupplierId.A).failures()).containsKey(FailureType.CIRCUIT_OPEN);
        assertThat(outcomes.get(SupplierId.B).status()).isEqualTo(SupplierStatus.OK);
        assertThat(result.stays()).isNotEmpty();
    }

    @Test
    @DisplayName("공급사가 회복되면 시험 호출을 거쳐 서킷이 닫힌다")
    void circuitClosesAfterSupplierRecovers() {
        MockSupplierServer.mode("a", "error");
        searchService.search(THREE_NIGHTS);
        searchService.search(THREE_NIGHTS);
        CircuitBreaker breaker = circuitBreakers.forSupplier(SupplierId.A);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // 대기 시간이 지난 상황을 시계 대신 상태 전이로 만든다.
        MockSupplierServer.mode("a", "normal");
        breaker.transitionToHalfOpenState();

        SearchResult result = searchService.search(THREE_NIGHTS);

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(result.suppliers().stream()
                .filter(outcome -> outcome.supplier() == SupplierId.A)
                .findFirst().orElseThrow().status())
                .isEqualTo(SupplierStatus.OK);
    }

    private static FailureType failureTypeOf(SearchResult result) {
        return result.suppliers().stream()
                .filter(outcome -> outcome.supplier() == SupplierId.A)
                .findFirst().orElseThrow()
                .failures().keySet().iterator().next();
    }
}
