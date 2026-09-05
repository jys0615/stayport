package io.github.jys0615.stayport.infra;

import io.github.jys0615.stayport.domain.SupplierId;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 공급사별 서킷 브레이커 보관소. 하나가 열려도 다른 공급사는 영향받지 않도록 공급사마다 따로 둔다.
 *
 * <p>열린 상태는 {@code stayport.supplier.circuit.open} 게이지(1=열림)로도 보인다 —
 * 실패 카운터만으로는 "실패가 멈춘 것"과 "부르는 것을 멈춘 것"을 구분할 수 없다.
 */
@Component
public class SupplierCircuitBreakers {

    private final CircuitBreakerRegistry registry;
    private final MeterRegistry meters;

    SupplierCircuitBreakers(StayportProperties properties, MeterRegistry meters) {
        StayportProperties.CircuitBreaker settings = properties.circuitBreaker();
        this.registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.slidingWindowSize())
                .minimumNumberOfCalls(settings.minimumCalls())
                .failureRateThreshold(settings.failureRateThreshold())
                .waitDurationInOpenState(settings.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(settings.permittedCallsInHalfOpenState())
                .build());
        this.meters = meters;
    }

    public CircuitBreaker forSupplier(SupplierId supplier) {
        CircuitBreaker breaker = registry.circuitBreaker(supplier.name());
        Gauge.builder("stayport.supplier.circuit.open", breaker,
                        b -> b.getState() == CircuitBreaker.State.OPEN ? 1 : 0)
                .description("공급사 서킷이 열려 있는지 (1=열림, 호출하지 않는 상태)")
                .tag("supplier", supplier.name())
                .register(meters);
        return breaker;
    }
}
