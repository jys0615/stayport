package io.github.jys0615.stayport.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.jys0615.stayport.application.port.QuarantineStore;
import io.github.jys0615.stayport.domain.SupplierId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 격리 기록이 실제로 응답 본문에 실리는지. 엔티티를 그대로 내보내던 때는 접근자가
 * {@code getX()}가 아니라 필드가 통째로 빠져 {@code [{}]}가 나갔다.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:quarantine-api;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuarantineEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuarantineStore quarantineStore;

    @Test
    @DisplayName("격리 기록은 사유와 원본 payload를 담아 내려간다")
    void quarantineRowsCarryReasonAndPayload() throws Exception {
        quarantineStore.keep(SupplierId.A, "형태 이상", "{\"hotelCode\":\"A-10023\"}");

        mockMvc.perform(get("/internal/quarantine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].supplier").value("A"))
                .andExpect(jsonPath("$[0].reason").value("형태 이상"))
                .andExpect(jsonPath("$[0].payload").value("{\"hotelCode\":\"A-10023\"}"))
                .andExpect(jsonPath("$[0].occurredAt").exists());
    }
}
