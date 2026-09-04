package io.github.jys0615.stayport.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jys0615.stayport.application.port.CatalogResult;
import io.github.jys0615.stayport.application.port.FailureType;
import io.github.jys0615.stayport.application.port.SupplierAdapter;
import io.github.jys0615.stayport.application.port.SupplierResult;
import io.github.jys0615.stayport.domain.SearchQuery;
import io.github.jys0615.stayport.domain.SupplierId;
import io.github.jys0615.stayport.support.MockSupplierServer;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * X-Api-Key가 실제로 붙어서 나가는지에 대한 대조 실험.
 *
 * <p>흉내 서버는 키 없는 요청을 거절한다(A 401, B 200+E401). 그래서 정상 키 컨텍스트의
 * 모든 테스트가 통과한다는 것 자체가 헤더 주입의 간접 증거다. 이 클래스는 반대 방향을
 * 고정한다 — 키를 비운 컨텍스트에서 두 공급사 모두 AUTH로 실패해야 한다. 통과한다면
 * 흉내 서버의 키 검사나 어댑터의 실패 분류 어느 쪽이 죽어 있다는 뜻이다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "stayport.suppliers.a.api-key=",
                "stayport.suppliers.b.api-key=",
                "spring.datasource.url=jdbc:h2:mem:auth-test;DB_CLOSE_DELAY=-1"
        })
@ActiveProfiles("test")
class SupplierAuthTest {

    private static final SearchQuery THREE_NIGHTS =
            new SearchQuery(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);

    @DynamicPropertySource
    static void supplierEndpoints(DynamicPropertyRegistry registry) {
        String baseUrl = MockSupplierServer.baseUrl();
        registry.add("stayport.suppliers.a.base-url", () -> baseUrl);
        registry.add("stayport.suppliers.b.base-url", () -> baseUrl);
    }

    @Autowired
    private List<SupplierAdapter> adapters;

    private SupplierAdapter adapter(SupplierId supplier) {
        return adapters.stream().filter(a -> a.supplier() == supplier).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("키 없이 부르면 A의 숙소 목록·재고 조회가 모두 AUTH로 실패한다")
    void supplierARejectsMissingKeyAsAuthFailure() {
        CatalogResult catalog = adapter(SupplierId.A).fetchCatalog().block();
        SupplierResult offers = adapter(SupplierId.A).fetchOffers(THREE_NIGHTS, List.of("A-10023")).block();

        assertThat(catalog).isInstanceOf(CatalogResult.Failure.class);
        assertThat(((CatalogResult.Failure) catalog).type()).isEqualTo(FailureType.AUTH);
        assertThat(offers).isInstanceOf(SupplierResult.Failure.class);
        assertThat(((SupplierResult.Failure) offers).type()).isEqualTo(FailureType.AUTH);
    }

    @Test
    @DisplayName("B는 키가 없어도 HTTP 200을 주지만, E401이 AUTH로 분류된다")
    void supplierBRejectsMissingKeyViaResultCode() {
        CatalogResult catalog = adapter(SupplierId.B).fetchCatalog().block();
        SupplierResult offers = adapter(SupplierId.B).fetchOffers(THREE_NIGHTS, List.of("B77120")).block();

        assertThat(catalog).isInstanceOf(CatalogResult.Failure.class);
        assertThat(((CatalogResult.Failure) catalog).type()).isEqualTo(FailureType.AUTH);
        assertThat(offers).isInstanceOf(SupplierResult.Failure.class);
        assertThat(((SupplierResult.Failure) offers).type()).isEqualTo(FailureType.AUTH);
    }
}
