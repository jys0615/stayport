package io.github.jys0615.stayport.application;

import io.github.jys0615.stayport.application.port.FailureType;
import io.github.jys0615.stayport.domain.StayOffer;
import io.github.jys0615.stayport.domain.SupplierId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 통합 검색 한 번의 결과.
 */
public record SearchResult(List<StayOffer> stays, List<SupplierOutcome> suppliers) {

    public SearchResult {
        stays = List.copyOf(stays);
        suppliers = List.copyOf(suppliers);
    }

    /**
     * @param skippedItems 형태가 깨졌거나 매핑이 없어 버린 상품 수
     * @param failures     실패 유형별 개수
     */
    public record SupplierOutcome(
            SupplierId supplier,
            SupplierStatus status,
            int returnedOffers,
            int skippedItems,
            int failedChunks,
            Map<FailureType, Integer> failures) {

        public SupplierOutcome {
            failures = failures.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(failures));
        }

        static SupplierOutcome ok(SupplierId supplier, int returnedOffers, int skippedItems) {
            return new SupplierOutcome(supplier, SupplierStatus.OK, returnedOffers, skippedItems, 0, Map.of());
        }

        static SupplierOutcome noMapping(SupplierId supplier) {
            return new SupplierOutcome(supplier, SupplierStatus.NO_MAPPING, 0, 0, 0, Map.of());
        }
    }
}
