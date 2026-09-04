package io.github.jys0615.stayport.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jys0615.stayport.application.SearchResult.SupplierOutcome;
import io.github.jys0615.stayport.application.port.MappingStore;
import io.github.jys0615.stayport.domain.SearchQuery;
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
 * 공급사가 우리 매핑에 없는 숙소를 돌려줬을 때의 동작.
 *
 * <p>동기화를 돌리지 않고 A-10023 하나만 손으로 매핑한다. 흉내 서버는 요청 코드와 무관하게
 * A-10023과 A-10044를 돌려주므로, A-10044가 "응답에는 있는데 매핑에는 없는" 상품이 된다.
 * 매핑을 직접 구성하므로 다른 테스트와 DB를 공유하지 않는다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.url=jdbc:h2:mem:unmapped-e2e;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class SearchUnmappedOfferTest {

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

    @BeforeEach
    void mapOnlyOneStay() {
        MockSupplierServer.reset();
        long stayId = mappingStore.upsertStay(SupplierId.A, "A-10023", "Riverside Hotel Seoul");
        mappingStore.upsertRoomType(SupplierId.A, "A-10023", "DLX-TWN", stayId, "Deluxe Twin", 2);
    }

    @Test
    @DisplayName("매핑에 없는 상품은 결과에서 빠지고, 빠졌다는 사실이 skippedItems로 남는다")
    void unmappedOfferIsSkippedAndCounted() {
        SearchResult result = searchService.search(THREE_NIGHTS);
        Map<SupplierId, SupplierOutcome> outcomes = result.suppliers().stream()
                .collect(Collectors.toMap(SupplierOutcome::supplier, Function.identity()));

        // A-10023만 해석되고, A-10044는 내부 식별자를 만들 수 없어 빠진다.
        assertThat(result.stays()).hasSize(1);
        assertThat(result.stays().getFirst().stayName()).contains("Riverside");

        SupplierOutcome a = outcomes.get(SupplierId.A);
        assertThat(a.status()).isEqualTo(SupplierStatus.OK);
        assertThat(a.returnedOffers()).isEqualTo(1);
        assertThat(a.skippedItems()).isEqualTo(1);

        // B는 매핑이 아예 없으므로 부르지 않았고, 그 사실이 FAILED가 아닌 NO_MAPPING으로 남는다.
        assertThat(outcomes.get(SupplierId.B).status()).isEqualTo(SupplierStatus.NO_MAPPING);
    }
}
