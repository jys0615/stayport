package io.github.jys0615.stayport.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 실제 HTTP로 흉내 서버를 부르는 테스트의 공통 설정.
 *
 * <p>어댑터를 mock 객체로 대체하지 않는다. 이 계층에서 확인하려는 것이 "HTTP 응답을 어떻게
 * 해석하느냐"이고, 그걸 mock으로 바꿔치우면 정작 확인하려던 것이 사라진다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public abstract class SupplierIntegrationTest {

    @DynamicPropertySource
    static void supplierEndpoints(DynamicPropertyRegistry registry) {
        String baseUrl = MockSupplierServer.baseUrl();
        registry.add("stayport.suppliers.a.base-url", () -> baseUrl);
        registry.add("stayport.suppliers.b.base-url", () -> baseUrl);
    }

    @BeforeEach
    void resetSupplierModes() {
        MockSupplierServer.reset();
    }
}
