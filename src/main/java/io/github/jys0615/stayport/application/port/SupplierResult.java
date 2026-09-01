package io.github.jys0615.stayport.application.port;

import io.github.jys0615.stayport.domain.StayOffer;
import io.github.jys0615.stayport.domain.SupplierId;
import java.util.List;

/**
 * 공급사 한 곳의 재고·요금 조회 결과. 성공과 실패를 예외가 아니라 값으로 다뤄야
 * 병합 단계에서 "한쪽이 죽어도 나머지로 응답한다"가 분기 없이 성립한다.
 */
public sealed interface SupplierResult {

    SupplierId supplier();

    /**
     * @param offers       정규화에 성공한 상품들
     * @param skippedItems 형태가 깨져 건너뛴 상품 수. 조용한 손실을 막기 위해 세어서 응답에 노출한다
     */
    record Success(SupplierId supplier, List<StayOffer> offers, int skippedItems) implements SupplierResult {

        public Success {
            offers = List.copyOf(offers);
        }

        public static Success of(SupplierId supplier, List<StayOffer> offers) {
            return new Success(supplier, offers, 0);
        }
    }

    /**
     * @param type   실패 분류
     * @param detail 로그·응답에 남길 짧은 사유. 공급사 원문 메시지를 그대로 흘리지 않는다
     */
    record Failure(SupplierId supplier, FailureType type, String detail) implements SupplierResult {
    }
}
