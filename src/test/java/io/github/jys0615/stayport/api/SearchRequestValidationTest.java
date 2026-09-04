package io.github.jys0615.stayport.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 성립하지 않는 요청은 전부 같은 모양의 400으로 나가야 한다.
 *
 * <p>바인딩 단계 실패(파라미터 누락·형식 오류)와 값 검증 실패는 스프링 기본 동작에서 서로 다른
 * 본문으로 나간다. 이 테스트는 다섯 가지 실패가 모두 {@code {error, message}} 한 가지 스키마로
 * 나가는지 고정한다 — 공급사는 호출되지 않으므로 흉내 서버가 없어도 돈다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchRequestValidationTest {

    private static final String SEARCH = "/api/v1/stays/search";

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("checkOut이 checkIn보다 빠르면 400")
    void checkOutBeforeCheckIn() throws Exception {
        mvc.perform(get(SEARCH)
                        .param("checkIn", "2026-09-04").param("checkOut", "2026-09-01")
                        .param("adults", "2").param("children", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("adults가 0이면 400")
    void zeroAdults() throws Exception {
        mvc.perform(get(SEARCH)
                        .param("checkIn", "2026-09-01").param("checkOut", "2026-09-04")
                        .param("adults", "0").param("children", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("children이 음수면 400")
    void negativeChildren() throws Exception {
        mvc.perform(get(SEARCH)
                        .param("checkIn", "2026-09-01").param("checkOut", "2026-09-04")
                        .param("adults", "2").param("children", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("날짜 형식이 틀려도 같은 스키마의 400 — 바인딩 실패가 다른 모양으로 새지 않는다")
    void malformedDateUsesSameErrorShape() throws Exception {
        mvc.perform(get(SEARCH)
                        .param("checkIn", "2026-13-99").param("checkOut", "2026-09-04")
                        .param("adults", "2").param("children", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("필수 파라미터가 없어도 같은 스키마의 400")
    void missingParameterUsesSameErrorShape() throws Exception {
        mvc.perform(get(SEARCH)
                        .param("checkIn", "2026-09-01").param("checkOut", "2026-09-04")
                        .param("children", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
