package io.github.jys0615.stayport.application;

import io.github.jys0615.stayport.application.port.FailureType;
import io.github.jys0615.stayport.application.port.SupplierResult;
import io.github.jys0615.stayport.domain.SupplierId;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 공급사 호출 지표. 부하 측정(docs/load-test.md)에서 "이 상황을 알아채는 지표"로 꼽은
 * 공급사별 응답 지연과 실패 유형 분포를 실제로 노출한다. 해석은 docs/monitoring.md.
 *
 * <ul>
 *   <li>{@code stayport.supplier.call} (timer) — 응답이 도착한 호출의 소요 시간. supplier·outcome 태그
 *   <li>{@code stayport.supplier.failure} (counter) — 실패 유형 발생 횟수. supplier·type 태그
 *   <li>{@code stayport.supplier.skipped} (counter) — 형태 불량·매핑 없음으로 버린 상품 수
 * </ul>
 */
@Component
public class SearchMetrics {

    private final MeterRegistry registry;

    SearchMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 예산 안에 도착한 결과 하나를 기록한다. */
    void recordArrival(SupplierResult result, Duration elapsed) {
        switch (result) {
            case SupplierResult.Success success -> {
                callTimer(success.supplier(), "ok").record(elapsed);
                skipped(success.supplier(), success.skippedItems());
            }
            case SupplierResult.Partial partial -> {
                callTimer(partial.supplier(), "partial").record(elapsed);
                skipped(partial.supplier(), partial.skippedItems());
                failures(partial.supplier(), partial.failures());
            }
            case SupplierResult.Failure failure -> {
                callTimer(failure.supplier(), "failed").record(elapsed);
                failures(failure.supplier(), Map.of(failure.type(), 1));
            }
        }
    }

    /** 검색 예산 안에 아예 답하지 않은 공급사. 호출이 끝나지 않았으므로 타이머는 기록하지 않는다. */
    void recordBudgetTimeout(SupplierId supplier) {
        failures(supplier, Map.of(FailureType.TIMEOUT, 1));
    }

    private Timer callTimer(SupplierId supplier, String outcome) {
        return Timer.builder("stayport.supplier.call")
                .description("응답이 도착한 공급사 호출의 소요 시간")
                .tag("supplier", supplier.name())
                .tag("outcome", outcome)
                .register(registry);
    }

    private void failures(SupplierId supplier, Map<FailureType, Integer> counts) {
        counts.forEach((type, count) -> Counter.builder("stayport.supplier.failure")
                .description("공급사 호출 실패 유형별 횟수")
                .tag("supplier", supplier.name())
                .tag("type", type.name())
                .register(registry)
                .increment(count));
    }

    private void skipped(SupplierId supplier, int count) {
        if (count > 0) {
            Counter.builder("stayport.supplier.skipped")
                    .description("형태 불량·매핑 없음으로 응답에서 뺀 상품 수")
                    .tag("supplier", supplier.name())
                    .register(registry)
                    .increment(count);
        }
    }
}
