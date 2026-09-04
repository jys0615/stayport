package io.github.jys0615.stayport.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jys0615.stayport.application.port.FailureType;
import io.github.jys0615.stayport.application.port.MappedRoomType;
import io.github.jys0615.stayport.application.port.MappingStore;
import io.github.jys0615.stayport.domain.SupplierId;
import io.github.jys0615.stayport.support.MockSupplierServer;
import io.github.jys0615.stayport.support.SupplierIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 매핑 동기화의 불변식.
 */
class MappingSyncTest extends SupplierIntegrationTest {

    @Autowired
    private MappingSyncService syncService;

    @Autowired
    private MappingStore mappingStore;

    @Test
    @DisplayName("다른 숙소가 같은 객실 코드를 써도 서로 다른 내부 식별자를 받는다")
    void sameRoomCodeInDifferentStaysGetsDistinctIdentifiers() {
        syncService.sync();

        // A-10023과 A-10077은 둘 다 객실 코드 DLX-TWN을 쓴다. 객실 코드는 숙소 안에서만
        // 유일하므로, 매핑 키에서 숙소 코드를 빼면 이 둘이 하나로 합쳐진다.
        List<MappedRoomType> dlxTwn = mappingStore.findAll().stream()
                .filter(mapping -> mapping.supplier() == SupplierId.A)
                .filter(mapping -> mapping.supplierRoomCode().equals("DLX-TWN"))
                .toList();

        assertThat(dlxTwn).hasSize(2);
        assertThat(dlxTwn).extracting(MappedRoomType::supplierStayCode)
                .containsExactlyInAnyOrder("A-10023", "A-10077");
        assertThat(dlxTwn).extracting(MappedRoomType::internalRoomTypeId).doesNotHaveDuplicates();
        assertThat(dlxTwn).extracting(MappedRoomType::internalStayId).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("같은 공급사 상품은 몇 번을 다시 동기화해도 같은 내부 식별자다")
    void resyncKeepsIdentifiersStable() {
        syncService.sync();
        Map<String, Long> before = identifiers();

        syncService.sync();
        syncService.sync();

        assertThat(identifiers()).isEqualTo(before);
    }

    @Test
    @DisplayName("B 숙소 목록 API가 200에 비정상 resultCode를 주면 동기화 실패로 잡힌다")
    void catalogErrorCodeFromBIsSyncFailure() {
        MockSupplierServer.mode("b", "error");

        SyncReport report = syncService.sync();

        Map<SupplierId, SyncReport.SupplierSync> bySupplier = report.suppliers().stream()
                .collect(Collectors.toMap(SyncReport.SupplierSync::supplier, Function.identity()));
        assertThat(bySupplier.get(SupplierId.B).status()).isEqualTo("FAILED");
        assertThat(bySupplier.get(SupplierId.B).failureType()).isEqualTo(FailureType.SUPPLIER_ERROR);
        // A는 영향을 받지 않는다.
        assertThat(bySupplier.get(SupplierId.A).status()).isEqualTo("OK");
    }

    private Map<String, Long> identifiers() {
        return mappingStore.findAll().stream().collect(Collectors.toMap(
                mapping -> mapping.supplier() + "/" + mapping.supplierStayCode() + "/" + mapping.supplierRoomCode(),
                MappedRoomType::internalRoomTypeId));
    }
}
