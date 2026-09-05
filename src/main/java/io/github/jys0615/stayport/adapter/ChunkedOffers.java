package io.github.jys0615.stayport.adapter;

import io.github.jys0615.stayport.application.port.FailureType;
import io.github.jys0615.stayport.application.port.SkippedOffer;
import io.github.jys0615.stayport.application.port.SupplierOffer;
import io.github.jys0615.stayport.application.port.SupplierResult;
import io.github.jys0615.stayport.domain.SupplierId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 숙소 코드를 호출 한도(공급사별 설정)에 맞게 나누고, 묶음별 결과를 합친다.
 * 분할이 어댑터 계층의 일인 이유: design.md §5.
 */
public final class ChunkedOffers {

    private ChunkedOffers() {
    }

    /** 코드 목록을 {@code size}개 이하의 묶음으로 나눈다. */
    public static List<List<String>> split(List<String> codes, int size) {
        if (size < 1) {
            throw new IllegalArgumentException("청크 크기는 1 이상이어야 한다: " + size);
        }
        List<List<String>> chunks = new ArrayList<>();
        for (int from = 0; from < codes.size(); from += size) {
            chunks.add(List.copyOf(codes.subList(from, Math.min(from + size, codes.size()))));
        }
        return chunks;
    }

    /**
     * 묶음별 결과 합치기: 전부 성공=Success, 전부 실패=Failure, 섞임=Partial(받은 것 유지).
     * 결과는 호출 완료 순서와 무관하다 — 실패는 유형별 개수로 담고, 하나로 줄일 때는
     * FailureType 선언 순서를 쓴다. 회귀 테스트: ChunkSplitTest.
     */
    public static SupplierResult merge(SupplierId supplier, List<SupplierResult> chunkResults) {
        List<SupplierOffer> offers = new ArrayList<>();
        List<SkippedOffer> skippedDetails = new ArrayList<>();
        int skippedItems = 0;
        Map<FailureType, Integer> failures = new EnumMap<>(FailureType.class);

        for (SupplierResult result : chunkResults) {
            switch (result) {
                case SupplierResult.Success success -> {
                    offers.addAll(success.offers());
                    skippedItems += success.skippedItems();
                    skippedDetails.addAll(success.skippedDetails());
                }
                case SupplierResult.Failure failure ->
                        failures.merge(failure.type(), 1, Integer::sum);
                case SupplierResult.Partial partial ->
                        // 묶음 하나의 결과로는 나올 수 없는 값.
                        throw new IllegalArgumentException("묶음 결과에 Partial이 올 수 없다: " + partial.supplier());
            }
        }

        if (failures.isEmpty()) {
            return new SupplierResult.Success(supplier, offers, skippedItems, skippedDetails);
        }

        int failedChunks = failures.values().stream().mapToInt(Integer::intValue).sum();
        if (offers.isEmpty() && failedChunks == chunkResults.size()) {
            FailureType type = FailureType.mostSignificant(failures.keySet());
            return new SupplierResult.Failure(supplier, type, summarize(failures));
        }
        return new SupplierResult.Partial(supplier, offers, skippedItems, skippedDetails, failedChunks, failures);
    }

    /** 로그와 응답에 실을 짧은 요약. 예: {@code "TIMEOUT x3, AUTH x1"} */
    private static String summarize(Map<FailureType, Integer> failures) {
        StringBuilder text = new StringBuilder();
        failures.forEach((type, count) -> {
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(type).append(" x").append(count);
        });
        return text.toString();
    }
}
