package io.github.jys0615.stayport.infra;

import io.github.jys0615.stayport.domain.SupplierId;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 애플리케이션 설정 전체.
 *
 * <p>공급사를 맵으로 둔 것은 의도적이다. 새 공급사를 붙일 때 이 클래스는 손대지 않고
 * 어댑터 구현체와 yml 항목만 추가하면 된다.
 *
 * @param search    검색 유스케이스 설정
 * @param sync      매핑 동기화 설정
 * @param suppliers 공급사별 연동 설정
 */
@ConfigurationProperties(prefix = "stayport")
public record StayportProperties(Search search, Sync sync, Map<SupplierId, Supplier> suppliers) {

    /**
     * @param totalBudget 검색 API 한 건에 허용하는 전체 시간. 공급사 호출은 병렬이므로
     *                    이 값은 "가장 느린 공급사 + 병합·직렬화 여유"로 잡는다.
     *                    MVC에서는 이 값이 곧 서블릿 스레드 1개의 최대 점유 시간이기도 하다
     */
    public record Search(Duration totalBudget) {
    }

    /**
     * @param onStartup 기동 시 자동 동기화 여부. 실패해도 앱은 계속 뜨고 수동 재동기화로 복구한다
     */
    public record Sync(boolean onStartup) {
    }

    /**
     * @param baseUrl        공급사 주소
     * @param apiKey         X-Api-Key 헤더로 보낼 키. 로그에는 남기지 않는다
     * @param chunkSize      1회 호출에 넣을 숙소 코드 최대 개수 (공급사 규약상 50)
     * @param connectTimeout TCP 연결 수립 제한
     * @param callTimeout    호출 1회 전체 제한 — 연결·응답·역직렬화를 모두 포함한다
     * @param paths          엔드포인트 경로
     */
    public record Supplier(
            String baseUrl,
            String apiKey,
            int chunkSize,
            Duration connectTimeout,
            Duration callTimeout,
            Paths paths) {

        /** 로그에 남길 때 쓰는 형태. 키 전체가 로그에 흘러가면 회수할 방법이 없다. */
        public String maskedApiKey() {
            if (apiKey == null || apiKey.length() <= 4) {
                return "****";
            }
            return "****" + apiKey.substring(apiKey.length() - 4);
        }
    }

    /**
     * @param catalog      숙소 목록 조회 경로
     * @param availability 재고·요금 조회 경로
     */
    public record Paths(String catalog, String availability) {
    }
}
