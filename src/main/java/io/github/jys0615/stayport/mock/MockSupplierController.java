package io.github.jys0615.stayport.mock;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공급사 흉내 서버. 요청 파라미터를 무시하고 고정 응답을 준다.
 *
 * <p>안내받은 뼈대에서 세 가지를 늘렸다. 셋 다 우리 쪽 설계 결정을 실제로 검증하기 위해서다.
 *
 * <ol>
 *   <li><b>숙소 목록에도 장애 모드</b> — "기동 시 동기화가 실패해도 앱은 뜬다"는 결정을 확인할
 *       방법이 없으면 그 결정은 문서에만 남는다.
 *   <li><b>{@code X-Api-Key} 필수</b> — 없으면 A는 401, B는 200+E401. 헤더 주입을 단위 테스트의
 *       assert가 아니라 연동 경로에서 확인하게 된다.
 *   <li><b>코드 목록 50개 초과 거절</b> — 청크 분할이 실제로 일어나지 않으면 실패하도록 만들었다.
 *       현재 데이터는 1청크뿐이라 이게 없으면 경계가 영원히 안 밟힌다.
 * </ol>
 *
 * <p>채점 대상이 아니다. {@code mock} 프로파일에서 9090으로만 뜬다.
 */
@RestController
@Profile("mock")
class MockSupplierController {

    private static final Logger log = LoggerFactory.getLogger(MockSupplierController.class);

    /** 클라이언트 타임아웃(3s)을 넉넉히 넘기되, 스레드를 10분씩 잡아두진 않는 값. */
    private static final Duration NO_RESPONSE_DELAY = Duration.ofSeconds(30);

    private static final int MAX_CODES = 50;
    private static final Set<String> MODES = Set.of("normal", "error", "no-response");

    private final Map<String, String> modes = new ConcurrentHashMap<>();

    // ── 고장 스위치 ──────────────────────────────────────────────────────────

    @PostMapping("/control/{supplier}/mode")
    ResponseEntity<Map<String, String>> setMode(@PathVariable String supplier, @RequestParam String value) {
        String key = supplier.toLowerCase();
        if (!MODES.contains(value)) {
            return ResponseEntity.badRequest().body(Map.of("error", "unknown mode: " + value));
        }
        modes.put(key, value);
        log.info("mock supplier {} mode -> {}", key, value);
        return ResponseEntity.ok(Map.of(key, value));
    }

    @GetMapping("/control/modes")
    Map<String, String> currentModes() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("a", mode("a"));
        snapshot.put("b", mode("b"));
        return snapshot;
    }

    // ── Supplier A — 실패를 HTTP 상태로 알린다 ───────────────────────────────

    @GetMapping(value = "/a/v1/hotels", produces = APPLICATION_JSON_VALUE)
    ResponseEntity<String> hotelsA(@RequestHeader(value = "X-Api-Key", required = false) String apiKey) {
        if (isBlank(apiKey)) {
            return status(HttpStatus.UNAUTHORIZED, MockResponses.A_UNAUTHORIZED);
        }
        return switch (mode("a")) {
            case "error" -> status(HttpStatus.SERVICE_UNAVAILABLE, MockResponses.A_UNAVAILABLE);
            case "no-response" -> hang();
            default -> ResponseEntity.ok(MockResponses.A_HOTELS);
        };
    }

    @GetMapping(value = "/a/v1/availability", produces = APPLICATION_JSON_VALUE)
    ResponseEntity<String> availabilityA(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestParam String hotelCodes,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            @RequestParam int adults,
            @RequestParam int children) {

        if (isBlank(apiKey)) {
            return status(HttpStatus.UNAUTHORIZED, MockResponses.A_UNAUTHORIZED);
        }
        if (countCodes(hotelCodes) > MAX_CODES) {
            return ResponseEntity.badRequest().body(MockResponses.A_TOO_MANY_CODES);
        }
        return switch (mode("a")) {
            case "error" -> status(HttpStatus.SERVICE_UNAVAILABLE, MockResponses.A_UNAVAILABLE);
            case "no-response" -> hang();
            default -> ResponseEntity.ok(MockResponses.A_AVAILABILITY);
        };
    }

    // ── Supplier B — 장애에도 HTTP 200을 주고 본문 resultCode로만 알린다 ─────

    @GetMapping(value = "/b/api/properties", produces = APPLICATION_JSON_VALUE)
    ResponseEntity<String> propertiesB(@RequestHeader(value = "X-Api-Key", required = false) String apiKey) {
        if (isBlank(apiKey)) {
            return ResponseEntity.ok(MockResponses.B_UNAUTHORIZED);
        }
        return switch (mode("b")) {
            case "error" -> ResponseEntity.ok(MockResponses.B_UNAVAILABLE);
            case "no-response" -> hang();
            default -> ResponseEntity.ok(MockResponses.B_PROPERTIES);
        };
    }

    @GetMapping(value = "/b/api/search", produces = APPLICATION_JSON_VALUE)
    ResponseEntity<String> searchB(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestParam String propertyIds,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            @RequestParam int adults,
            @RequestParam int children) {

        if (isBlank(apiKey)) {
            return ResponseEntity.ok(MockResponses.B_UNAUTHORIZED);
        }
        if (countCodes(propertyIds) > MAX_CODES) {
            return ResponseEntity.ok(MockResponses.B_TOO_MANY_CODES);
        }
        return switch (mode("b")) {
            case "error" -> ResponseEntity.ok(MockResponses.B_UNAVAILABLE);
            case "no-response" -> hang();
            default -> ResponseEntity.ok(MockResponses.B_SEARCH);
        };
    }

    // ── 거들기 ──────────────────────────────────────────────────────────────

    private String mode(String supplier) {
        return modes.getOrDefault(supplier, "normal");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static int countCodes(String csv) {
        return csv.isBlank() ? 0 : csv.split(",", -1).length;
    }

    private static ResponseEntity<String> status(HttpStatus status, String body) {
        return ResponseEntity.status(status).body(body);
    }

    /** 연결은 붙어 있는데 응답이 오지 않는 상황. 클라이언트의 타임아웃이 발동해야 한다. */
    private static ResponseEntity<String> hang() {
        try {
            Thread.sleep(NO_RESPONSE_DELAY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ResponseEntity.ok(MockResponses.EMPTY);
    }
}
