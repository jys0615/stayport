package io.github.jys0615.stayport.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jys0615.stayport.application.SearchResult.SupplierOutcome;
import io.github.jys0615.stayport.application.port.FailureType;
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
 * 청크 일부만 실패했을 때 검색 응답이 PARTIAL로 나가는 것을 검색 경로 끝까지 확인한다.
 *
 * <p>기본 데이터는 1청크뿐이라 이 상황이 생기지 않는다. 매핑을 51개 넘게 심어 2청크를 만들고,
 * 흉내 서버의 fail-code로 두 번째 청크만 실패시킨다. 매핑을 직접 심으므로 다른 테스트와
 * DB를 공유하지 않는다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.url=jdbc:h2:mem:partial-e2e;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class SearchPartialChunkTest {

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

    @Autowired
    private MappingStore mappingStore;

    @BeforeEach
    void prepare() {
        MockSupplierServer.reset();
        syncService.sync();
        // 실제 매핑(A 숙소 3개)에 가짜 코드를 더해 A의 숙소를 52개로 만든다 → 50 + 2로 갈린다.
        for (int i = 1; i <= 49; i++) {
            String code = "A-EX-%02d".formatted(i);
            long stayId = mappingStore.upsertStay(SupplierId.A, code, "Extra " + i);
            mappingStore.upsertRoomType(SupplierId.A, code, "RT", stayId, "Extra Room", 2);
        }
    }

    private static Map<SupplierId, SupplierOutcome> outcomes(SearchResult result) {
        return result.suppliers().stream()
                .collect(Collectors.toMap(SupplierOutcome::supplier, Function.identity()));
    }

    @Test
    @DisplayName("두 번째 청크만 실패하면 PARTIAL — 첫 청크의 상품은 그대로 반환된다")
    void secondChunkFailureYieldsPartialWithFirstChunkOffers() {
        // A-EX-49는 마지막에 심었으므로 두 번째 청크에 들어간다.
        MockSupplierServer.failCode("a", "A-EX-49");

        SearchResult result = searchService.search(THREE_NIGHTS);
        SupplierOutcome a = outcomes(result).get(SupplierId.A);

        assertThat(a.status()).isEqualTo(SupplierStatus.PARTIAL);
        assertThat(a.failedChunks()).isEqualTo(1);
        assertThat(a.failures()).containsEntry(FailureType.SUPPLIER_ERROR, 1);
        // 첫 청크가 돌려준 상품(흉내 서버 고정 응답의 A-10023·A-10044)은 살아 있다.
        assertThat(a.returnedOffers()).isEqualTo(2);
        assertThat(result.stays()).filteredOn(offer -> offer.supplier() == SupplierId.A).hasSize(2);
        // B는 이 일과 무관하다.
        assertThat(outcomes(result).get(SupplierId.B).status()).isEqualTo(SupplierStatus.OK);
    }

    @Test
    @DisplayName("fail-code를 해제하면 다시 전 청크 성공 = OK")
    void recoversToOkWhenFailCodeCleared() {
        MockSupplierServer.failCode("a", "A-EX-49");
        assertThat(outcomes(searchService.search(THREE_NIGHTS)).get(SupplierId.A).status())
                .isEqualTo(SupplierStatus.PARTIAL);

        MockSupplierServer.failCode("a", "");

        SearchResult result = searchService.search(THREE_NIGHTS);
        SupplierOutcome a = outcomes(result).get(SupplierId.A);
        assertThat(a.status()).isEqualTo(SupplierStatus.OK);
        assertThat(a.failedChunks()).isZero();
        // 상품 수는 단언하지 않는다 — 흉내 서버는 어느 청크에도 같은 고정 응답을 주므로
        // 두 청크가 모두 성공하면 같은 상품이 두 벌 돌아온다. 실제 공급사는 요청한 코드만
        // 돌려주므로(청크가 서로소) 생기지 않는 상황이다.
    }
}
