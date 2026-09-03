package io.github.jys0615.stayport.application.port;

import io.github.jys0615.stayport.domain.SupplierId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 공급사 한 곳의 재고·요금 조회 결과. 성공과 실패를 예외가 아니라 값으로 다뤄야
 * 병합 단계에서 "한쪽이 죽어도 나머지로 응답한다"가 분기 없이 성립한다.
 */
public sealed interface SupplierResult {

    SupplierId supplier();

    /**
     * 이 공급사에 물어본 것을 전부 받았다.
     *
     * @param offers       정규화에 성공한 상품들. 아직 공급사 코드 기준이다 —
     *                     내부 식별자로 바꾸는 것은 유스케이스의 일이다
     * @param skippedItems 형태가 깨져 건너뛴 상품 수. 조용한 손실을 막기 위해 세어서 응답에 노출한다
     */
    record Success(SupplierId supplier, List<SupplierOffer> offers, int skippedItems) implements SupplierResult {

        public Success {
            offers = List.copyOf(offers);
        }

        public static Success of(SupplierId supplier, List<SupplierOffer> offers) {
            return new Success(supplier, offers, 0);
        }
    }

    /**
     * 여러 번에 나눠 물어봤고 일부만 받았다.
     *
     * <p>받은 것을 버리지 않는다. 절반이라도 보여주는 편이 아무것도 안 보여주는 것보다 낫고,
     * 못 받은 사실은 함께 알린다. 응답 계약의 {@code PARTIAL}이 이 상태다.
     *
     * <p>실패를 하나로 요약하지 않고 <b>유형별 개수를 그대로</b> 담는다. 대표값 하나만 남기면
     * "타임아웃 3건과 인증 실패 1건"이 "타임아웃"으로 뭉개지고, 정작 고쳐야 할 인증 문제가
     * 사라진다. 요약이 필요한 곳에서는 {@link FailureType#mostSignificant}로 고른다.
     *
     * @param failedChunks 실패한 호출 묶음의 수
     * @param failures     실패 유형별 묶음 수. 호출 완료 순서와 무관하게 같은 값이 나온다
     */
    record Partial(
            SupplierId supplier,
            List<SupplierOffer> offers,
            int skippedItems,
            int failedChunks,
            Map<FailureType, Integer> failures) implements SupplierResult {

        public Partial {
            offers = List.copyOf(offers);
            failures = Map.copyOf(new EnumMap<>(failures));
        }
    }

    /**
     * 이 공급사에서 아무것도 받지 못했다.
     *
     * @param type   실패 분류
     * @param detail 로그·응답에 남길 짧은 사유. 공급사 원문 메시지를 그대로 흘리지 않는다
     */
    record Failure(SupplierId supplier, FailureType type, String detail) implements SupplierResult {
    }
}
