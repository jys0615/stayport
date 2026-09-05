package io.github.jys0615.stayport.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jys0615.stayport.application.port.MappingStore;
import io.github.jys0615.stayport.domain.SearchQuery;
import io.github.jys0615.stayport.domain.SupplierId;
import io.github.jys0615.stayport.support.MockSupplierServer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 공급사 호출이 지표로 남는지. 값 해석은 docs/monitoring.md — 여기서는 "기록된다"만 고정한다.
 * 매핑을 직접 구성하므로 다른 테스트와 DB를 공유하지 않는다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.url=jdbc:h2:mem:metrics-e2e;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class SearchMetricsTest {

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
    private MeterRegistry meterRegistry;

    @BeforeEach
    void mapSupplierA() {
        MockSupplierServer.reset();
        long stayId = mappingStore.upsertStay(SupplierId.A, "A-10023", "Riverside Hotel Seoul");
        mappingStore.upsertRoomType(SupplierId.A, "A-10023", "DLX-TWN", stayId, "Deluxe Twin", 2);
    }

    @Test
    @DisplayName("성공한 호출은 supplier·outcome 태그가 붙은 타이머로 남는다")
    void successfulCallIsTimed() {
        searchService.search(THREE_NIGHTS);

        Timer timer = meterRegistry.get("stayport.supplier.call")
                .tag("supplier", "A").tag("outcome", "ok").timer();
        assertThat(timer.count()).isGreaterThanOrEqualTo(1);
        assertThat(timer.max(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThan(0);
    }

    @Test
    @DisplayName("공급사 장애는 실패 유형별 카운터로 남는다")
    void failureIsCountedByType() {
        MockSupplierServer.mode("a", "error");

        searchService.search(THREE_NIGHTS);

        Counter failures = meterRegistry.get("stayport.supplier.failure")
                .tag("supplier", "A").tag("type", "SUPPLIER_ERROR").counter();
        assertThat(failures.count()).isGreaterThanOrEqualTo(1);

        Timer failedTimer = meterRegistry.get("stayport.supplier.call")
                .tag("supplier", "A").tag("outcome", "failed").timer();
        assertThat(failedTimer.count()).isGreaterThanOrEqualTo(1);
    }
}
