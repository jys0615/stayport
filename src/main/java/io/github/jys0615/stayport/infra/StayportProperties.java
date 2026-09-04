package io.github.jys0615.stayport.infra;

import io.github.jys0615.stayport.domain.SupplierId;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 애플리케이션 설정. 공급사가 맵이라 새 공급사는 yml 항목 추가만으로 바인딩된다.
 */
@ConfigurationProperties(prefix = "stayport")
public record StayportProperties(Search search, Sync sync, Map<SupplierId, Supplier> suppliers) {

    /** @param totalBudget 검색 한 건의 전체 예산 = max(공급사 호출) + 병합 여유. 값 근거: design.md §5 */
    public record Search(Duration totalBudget) {
    }

    /** @param onStartup 기동 시 자동 동기화 여부 */
    public record Sync(boolean onStartup) {
    }

    /**
     * @param apiKey      X-Api-Key로 전송. 로그에는 마스킹만 남긴다
     * @param chunkSize   1회 호출당 숙소 코드 상한 (규약 50)
     * @param callTimeout 호출 1회 전체 제한 — 연결·응답·역직렬화 포함
     */
    public record Supplier(
            String baseUrl,
            String apiKey,
            int chunkSize,
            Duration connectTimeout,
            Duration callTimeout,
            Paths paths) {

        /** 로그용 마스킹 형태. */
        public String maskedApiKey() {
            if (apiKey == null || apiKey.length() <= 4) {
                return "****";
            }
            return "****" + apiKey.substring(apiKey.length() - 4);
        }
    }

    /** 엔드포인트 경로. */
    public record Paths(String catalog, String availability) {
    }
}
