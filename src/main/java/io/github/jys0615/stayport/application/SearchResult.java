package io.github.jys0615.stayport.application;

import io.github.jys0615.stayport.application.port.FailureType;
import io.github.jys0615.stayport.domain.StayOffer;
import io.github.jys0615.stayport.domain.SupplierId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 통합 검색 한 번의 결과.
 *
 * @param stays     내부 식별자까지 해석이 끝난 상품들. 여러 공급사의 결과가 섞여 있다
 * @param suppliers 공급사별로 어디까지 받았는지
 */
public record SearchResult(List<StayOffer> stays, List<SupplierOutcome> suppliers) {

    public SearchResult {
        stays = List.copyOf(stays);
        suppliers = List.copyOf(suppliers);
    }

    /**
     * @param returnedOffers 이 공급사에서 결과로 들어간 상품 수
     * @param skippedItems   형태가 깨졌거나 매핑이 없어 버린 상품 수. 조용한 손실을 드러낸다
     * @param failedChunks   나눠 부른 것 중 실패한 묶음 수
     * @param failures       실패 유형별 개수. 하나로 요약하지 않는다
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
