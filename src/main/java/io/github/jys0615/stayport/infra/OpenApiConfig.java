package io.github.jys0615.stayport.infra;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 생성 문서의 머리말. 계약의 세부는 docs/api.md에 있고 여기서는 어디를 봐야 하는지만 가리킨다. */
@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI stayportOpenApi() {
        return new OpenAPI().info(new Info()
                .title("stayport API")
                .version("v1")
                .description("""
                        서로 다른 스펙의 숙박 공급사를 하나의 표준 상품 모델로 통합한 검색 API입니다.

                        요청이 성립하면 항상 200이고, 공급사 장애는 응답 본문의 suppliers[]에 상태로 \
                        담깁니다. 상태별로 클라이언트가 무엇을 해야 하는지는 docs/api.md에 있습니다."""));
    }
}
