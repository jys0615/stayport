package io.github.jys0615.stayport.application.port;

import io.github.jys0615.stayport.domain.SupplierId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 공급사 한 곳의 재고·요금 조회 결과. 실패는 예외가 아니라 값이다(근거: design.md §6).
 * 세 갈래는 응답 계약의 OK | PARTIAL | FAILED와 일대일.
 */
public sealed interface SupplierResult {

    SupplierId supplier();

    /**
     * 물어본 것을 전부 받았다.
     *
     * @param offers         정규화된 상품들 — 아직 공급사 코드 기준 (내부 식별자 해석은 유스케이스)
     * @param skippedItems   형태가 깨져 건너뛴 상품 수
     * @param skippedDetails 건너뛴 상품의 이유·원본. 저장은 블로킹이 안전한 유스케이스가 한다
     */
    record Success(SupplierId supplier, List<SupplierOffer> offers, int skippedItems,
                   List<SkippedOffer> skippedDetails) implements SupplierResult {

        public Success {
            offers = List.copyOf(offers);
            skippedDetails = List.copyOf(skippedDetails);
        }

        public static Success of(SupplierId supplier, List<SupplierOffer> offers) {
            return new Success(supplier, offers, 0, List.of());
        }
    }

    /**
     * 나눠 부른 것 중 일부만 받았다. 받은 상품은 버리지 않는다.
     *
     * @param failedChunks 실패한 묶음 수
     * @param failures     실패 유형별 개수 — 하나로 요약하지 않는다. 호출 완료 순서와 무관
     */
    record Partial(
            SupplierId supplier,
            List<SupplierOffer> offers,
            int skippedItems,
            List<SkippedOffer> skippedDetails,
            int failedChunks,
            Map<FailureType, Integer> failures) implements SupplierResult {

        public Partial {
            offers = List.copyOf(offers);
            skippedDetails = List.copyOf(skippedDetails);
            failures = Map.copyOf(new EnumMap<>(failures));
        }
    }

    /**
     * 아무것도 받지 못했다.
     *
     * @param detail 짧은 사유 — 공급사 원문 메시지는 담지 않는다
     */
    record Failure(SupplierId supplier, FailureType type, String detail) implements SupplierResult {
    }
}
