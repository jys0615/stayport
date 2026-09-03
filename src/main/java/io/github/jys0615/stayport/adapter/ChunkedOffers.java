package io.github.jys0615.stayport.adapter;

import io.github.jys0615.stayport.application.port.FailureType;
import io.github.jys0615.stayport.application.port.SupplierOffer;
import io.github.jys0615.stayport.application.port.SupplierResult;
import io.github.jys0615.stayport.domain.SupplierId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 숙소 코드를 공급사 호출 한도에 맞게 나누고, 나눠 부른 결과를 하나로 합친다.
 *
 * <p>나누는 것은 어댑터의 일이다. 한도는 공급사마다 다를 수 있는 공급사 사정이고, 이미 공급사별
 * 설정으로 들어와 있다. 유스케이스가 "50개씩 잘라라"를 알면 그 값이 경계를 넘는다.
 *
 * <p>합치는 규칙은 두 공급사가 같으므로 여기 모았다.
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
     * 묶음별 결과를 공급사 하나의 결과로 합친다.
     *
     * <p>전부 성공하면 성공, 전부 실패하면 실패, 섞이면 {@code Partial}이다. 섞였을 때 전체를
     * 실패로 접지 않는 이유는, 그러면 이미 받은 절반을 버리게 되고 클라이언트가 "일부는 받았다"를
     * 알 방법이 없어지기 때문이다.
     *
     * <p><b>결과가 호출 완료 순서에 의존하지 않는다.</b> 묶음은 병렬로 나가고 끝나는 순서는
     * 매번 다르므로, 실패 요약을 "먼저 끝난 실패"로 정하면 같은 요청이 실행마다 다른 분류를
     * 내놓는다. 그래서 유형별 개수를 세어 담고, 하나로 줄여야 할 때는 선언된 우선순위로 고른다.
     */
    public static SupplierResult merge(SupplierId supplier, List<SupplierResult> chunkResults) {
        List<SupplierOffer> offers = new ArrayList<>();
        int skippedItems = 0;
        Map<FailureType, Integer> failures = new EnumMap<>(FailureType.class);

        for (SupplierResult result : chunkResults) {
            switch (result) {
                case SupplierResult.Success success -> {
                    offers.addAll(success.offers());
                    skippedItems += success.skippedItems();
                }
                case SupplierResult.Failure failure ->
                        failures.merge(failure.type(), 1, Integer::sum);
                case SupplierResult.Partial partial ->
                        // 묶음 하나의 결과가 Partial일 수는 없다. 들어오면 삼키지 않고 드러낸다.
                        throw new IllegalArgumentException("묶음 결과에 Partial이 올 수 없다: " + partial.supplier());
            }
        }

        if (failures.isEmpty()) {
            return new SupplierResult.Success(supplier, offers, skippedItems);
        }

        int failedChunks = failures.values().stream().mapToInt(Integer::intValue).sum();
        if (offers.isEmpty() && failedChunks == chunkResults.size()) {
            FailureType type = FailureType.mostSignificant(failures.keySet());
            return new SupplierResult.Failure(supplier, type, summarize(failures));
        }
        return new SupplierResult.Partial(supplier, offers, skippedItems, failedChunks, failures);
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
