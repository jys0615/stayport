package io.github.jys0615.stayport.application;

import io.github.jys0615.stayport.application.port.CatalogResult;
import io.github.jys0615.stayport.application.port.FailureType;
import io.github.jys0615.stayport.application.port.SupplierAdapter;
import io.github.jys0615.stayport.application.port.SupplierResult;
import io.github.jys0615.stayport.domain.SearchQuery;
import io.github.jys0615.stayport.domain.SupplierId;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.List;
import java.util.concurrent.TimeUnit;
import reactor.core.publisher.Mono;

/**
 * 계속 실패하는 공급사를 잠시 부르지 않는다. 검색 예산의 대부분을 어차피 실패할 호출에 쓰는 대신
 * 즉시 접고 나머지 공급사의 결과로 응답하기 위한 것이다 (근거: docs/design.md §10).
 *
 * <p>이 어댑터들은 실패를 예외가 아니라 값({@link SupplierResult.Failure})으로 돌려주므로
 * 라이브러리의 리액터 연산자를 그대로 붙이면 브레이커가 실패를 한 건도 보지 못한다. 결과를 열어
 * 보고 직접 기록하는 이유다.
 *
 * <p>기록 규칙 두 가지:
 * <ul>
 *   <li>{@code Partial}은 성공으로 센다 — 일부라도 받았다면 부를 가치가 있는 상태다.
 *   <li>숙소 목록 동기화({@link #fetchCatalog()})는 보호하지 않는다 — 검색과 달리 사람이
 *       부르는 복구 경로라, 열린 서킷 때문에 복구를 못 하면 곤란하다.
 * </ul>
 */
final class CircuitBreakingSupplierAdapter implements SupplierAdapter {

    private final SupplierAdapter delegate;
    private final CircuitBreaker breaker;

    CircuitBreakingSupplierAdapter(SupplierAdapter delegate, CircuitBreaker breaker) {
        this.delegate = delegate;
        this.breaker = breaker;
    }

    @Override
    public SupplierId supplier() {
        return delegate.supplier();
    }

    @Override
    public Mono<CatalogResult> fetchCatalog() {
        return delegate.fetchCatalog();
    }

    @Override
    public Mono<SupplierResult> fetchOffers(SearchQuery query, List<String> stayCodes) {
        if (!breaker.tryAcquirePermission()) {
            return Mono.just(new SupplierResult.Failure(
                    supplier(), FailureType.CIRCUIT_OPEN, "서킷이 열려 있어 호출하지 않았다"));
        }
        long startedAt = System.nanoTime();
        return delegate.fetchOffers(query, stayCodes)
                .doOnNext(result -> record(result, System.nanoTime() - startedAt))
                .doOnError(error -> breaker.onError(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS, error))
                // 예산 초과로 구독이 끊기면 성공도 실패도 아니다 — 허가만 돌려준다.
                .doOnCancel(breaker::releasePermission);
    }

    private void record(SupplierResult result, long elapsedNanos) {
        if (result instanceof SupplierResult.Failure failure) {
            breaker.onError(elapsedNanos, TimeUnit.NANOSECONDS,
                    new SupplierCallFailed(supplier(), failure.type()));
        } else {
            breaker.onSuccess(elapsedNanos, TimeUnit.NANOSECONDS);
        }
    }

    /** 브레이커에 실패를 알리기 위한 표식. 스택 트레이스는 쓰이지 않는다. */
    private static final class SupplierCallFailed extends RuntimeException {
        SupplierCallFailed(SupplierId supplier, FailureType type) {
            super(supplier + " " + type, null, false, false);
        }
    }
}
