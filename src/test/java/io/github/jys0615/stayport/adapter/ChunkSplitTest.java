package io.github.jys0615.stayport.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jys0615.stayport.application.port.FailureType;
import io.github.jys0615.stayport.application.port.SupplierAdapter;
import io.github.jys0615.stayport.application.port.SupplierResult;
import io.github.jys0615.stayport.domain.SearchQuery;
import io.github.jys0615.stayport.domain.SupplierId;
import io.github.jys0615.stayport.support.SupplierIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 공급사 호출 한도(50개)를 넘는 코드 목록이 나눠서 조회되는지 확인한다.
 *
 * <p>흉내 서버는 51개를 받으면 거절한다. 그래서 나누지 않으면 이 테스트가 실패한다 —
 * 현재 데이터가 두 숙소뿐이라 그냥 두면 청크 분할이 틀려도 아무 일이 안 일어난다.
 */
class ChunkSplitTest extends SupplierIntegrationTest {

    private static final SearchQuery THREE_NIGHTS =
            new SearchQuery(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);

    @Autowired
    private List<SupplierAdapter> adapters;

    private SupplierAdapter adapter(SupplierId supplier) {
        return adapters.stream().filter(a -> a.supplier() == supplier).findFirst().orElseThrow();
    }

    private static List<String> codes(int count) {
        return IntStream.range(0, count).mapToObj("A-%05d"::formatted).toList();
    }

    private SupplierResult fetch(SupplierId supplier, int codeCount) {
        return adapter(supplier).fetchOffers(THREE_NIGHTS, codes(codeCount)).block();
    }

    @Test
    @DisplayName("나누기: 코드 50개는 한 번에 나간다")
    void fiftyCodesGoInOneCall() {
        assertThat(ChunkedOffers.split(codes(50), 50)).hasSize(1);
        assertThat(fetch(SupplierId.A, 50)).isInstanceOf(SupplierResult.Success.class);
    }

    @Test
    @DisplayName("나누기: 코드 51개는 두 번으로 갈린다 (50 + 1)")
    void fiftyOneCodesSplitIntoTwo() {
        List<List<String>> chunks = ChunkedOffers.split(codes(51), 50);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).hasSize(50);
        assertThat(chunks.get(1)).hasSize(1);
    }

    @Test
    @DisplayName("51개를 보내도 성공한다 — 나누지 않으면 공급사가 400으로 거절한다")
    void oversizedRequestStillSucceedsBecauseItIsSplit() {
        SupplierResult a = fetch(SupplierId.A, 51);
        SupplierResult b = fetch(SupplierId.B, 51);

        assertThat(a).isInstanceOf(SupplierResult.Success.class);
        assertThat(b).isInstanceOf(SupplierResult.Success.class);

        // 흉내 서버는 어떤 코드를 받아도 같은 고정 응답을 준다. 두 번 불렸으므로 같은 상품이 두 벌
        // 돌아온다 — 실제 공급사라면 묶음마다 다른 상품이 온다. 여기서 확인하려는 것은 "두 번 불렸다"다.
        assertThat(((SupplierResult.Success) a).offers()).hasSize(4);
        assertThat(((SupplierResult.Success) b).offers()).hasSize(2);
    }

    @Test
    @DisplayName("물어볼 코드가 없으면 공급사를 부르지 않는다")
    void emptyCodesSkipTheCallEntirely() {
        SupplierResult result = adapter(SupplierId.A).fetchOffers(THREE_NIGHTS, List.of()).block();

        assertThat(result).isInstanceOf(SupplierResult.Success.class);
        assertThat(((SupplierResult.Success) result).offers()).isEmpty();
    }

    @Test
    @DisplayName("묶음 일부만 실패하면 PARTIAL — 받은 것을 버리지 않는다")
    void mixedChunkResultsBecomePartial() {
        SupplierResult merged = ChunkedOffers.merge(SupplierId.A, List.of(
                (SupplierResult) SupplierResult.Success.of(SupplierId.A, List.of()),
                new SupplierResult.Failure(SupplierId.A, FailureType.TIMEOUT, "3s")));

        assertThat(merged).isInstanceOf(SupplierResult.Partial.class);
        SupplierResult.Partial partial = (SupplierResult.Partial) merged;
        assertThat(partial.failedChunks()).isEqualTo(1);
        assertThat(partial.failures()).containsExactly(entry(FailureType.TIMEOUT, 1));
    }

    @Test
    @DisplayName("PARTIAL은 실패를 유형별로 센다 — 하나로 요약하면 고쳐야 할 문제가 묻힌다")
    void partialCountsFailuresByType() {
        SupplierResult merged = ChunkedOffers.merge(SupplierId.A, List.of(
                (SupplierResult) SupplierResult.Success.of(SupplierId.A, List.of()),
                new SupplierResult.Failure(SupplierId.A, FailureType.TIMEOUT, "3s"),
                new SupplierResult.Failure(SupplierId.A, FailureType.TIMEOUT, "3s"),
                new SupplierResult.Failure(SupplierId.A, FailureType.AUTH, "HTTP 401")));

        SupplierResult.Partial partial = (SupplierResult.Partial) merged;
        assertThat(partial.failedChunks()).isEqualTo(3);
        assertThat(partial.failures())
                .containsOnly(entry(FailureType.TIMEOUT, 2), entry(FailureType.AUTH, 1));
    }

    @Test
    @DisplayName("묶음이 전부 실패하면 FAILED")
    void allChunksFailingBecomesFailure() {
        SupplierResult merged = ChunkedOffers.merge(SupplierId.B, List.of(
                new SupplierResult.Failure(SupplierId.B, FailureType.SUPPLIER_ERROR, "E503"),
                new SupplierResult.Failure(SupplierId.B, FailureType.TIMEOUT, "3s")));

        assertThat(merged).isInstanceOf(SupplierResult.Failure.class);
        assertThat(((SupplierResult.Failure) merged).detail())
                .contains("SUPPLIER_ERROR x1", "TIMEOUT x1");
    }

    @Test
    @DisplayName("대표 분류는 재시도해도 달라지지 않는 것을 먼저 고른다 — 순서와 무관하게 같다")
    void representativeFailureIgnoresCompletionOrder() {
        List<SupplierResult> timeoutFirst = List.of(
                new SupplierResult.Failure(SupplierId.A, FailureType.TIMEOUT, "3s"),
                new SupplierResult.Failure(SupplierId.A, FailureType.AUTH, "HTTP 401"));
        List<SupplierResult> authFirst = List.of(
                new SupplierResult.Failure(SupplierId.A, FailureType.AUTH, "HTTP 401"),
                new SupplierResult.Failure(SupplierId.A, FailureType.TIMEOUT, "3s"));

        // 묶음은 병렬로 나가므로 끝나는 순서가 매번 다르다. 그래도 결론은 같아야 한다.
        for (List<SupplierResult> results : List.of(timeoutFirst, authFirst)) {
            SupplierResult merged = ChunkedOffers.merge(SupplierId.A, results);
            assertThat(((SupplierResult.Failure) merged).type()).isEqualTo(FailureType.AUTH);
        }
    }

    @Test
    @DisplayName("청크 크기가 0 이하면 즉시 거절한다")
    void nonPositiveChunkSizeIsRejected() {
        assertThatThrownBy(() -> ChunkedOffers.split(codes(3), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
