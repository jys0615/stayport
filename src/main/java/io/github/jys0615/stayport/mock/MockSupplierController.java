package io.github.jys0615.stayport.mock;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
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
 * 공급사 흉내 서버 — mock 프로파일에서 9090으로만 뜬다. 요청 내용과 무관하게 고정 응답.
 *
 * <p>제공 뼈대에서 늘린 것: 숙소 목록 장애 모드, X-Api-Key 필수(A 401 / B 200+E401),
 * 코드 50개 초과 거절, empty-body 모드. 각각이 무엇을 검증하는지는 테스트 클래스 참조.
 */
@RestController
@Profile("mock")
class MockSupplierController {

    private static final Logger log = LoggerFactory.getLogger(MockSupplierController.class);

    /** 클라이언트 타임아웃(3s)보다 충분히 긴 값. */
    private static final Duration NO_RESPONSE_DELAY = Duration.ofSeconds(30);

    private static final int MAX_CODES = 50;
    private static final Set<String> MODES =
            Set.of("normal", "error", "no-response", "empty-body", "duplicate-items", "slow");

    /** slow 모드의 지연. 클라이언트 타임아웃(3s)보다는 짧아서 성공 응답으로 처리된다. */
    private static final Duration SLOW_DELAY = Duration.ofSeconds(2);

    private final Map<String, String> modes = new ConcurrentHashMap<>();

    /** 이 코드가 포함된 재고 조회만 실패시킨다. 묶음 일부만 실패하는 상황(PARTIAL) 재현용. */
    private final Map<String, String> failCodes = new ConcurrentHashMap<>();

    // ── 고장 스위치 ──

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

    @PostMapping("/control/{supplier}/fail-code")
    ResponseEntity<Map<String, String>> setFailCode(@PathVariable String supplier, @RequestParam String value) {
        String key = supplier.toLowerCase();
        if (value.isBlank()) {
            failCodes.remove(key);
        } else {
            failCodes.put(key, value);
        }
        log.info("mock supplier {} fail-code -> {}", key, value.isBlank() ? "(해제)" : value);
        return ResponseEntity.ok(Map.of(key, value));
    }

    @GetMapping("/control/modes")
    Map<String, String> currentModes() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("a", mode("a"));
        snapshot.put("b", mode("b"));
        return snapshot;
    }

    // ── Supplier A ──

    @GetMapping(value = "/a/v1/hotels", produces = APPLICATION_JSON_VALUE)
    ResponseEntity<String> hotelsA(@RequestHeader(value = "X-Api-Key", required = false) String apiKey) {
        if (isBlank(apiKey)) {
            return status(HttpStatus.UNAUTHORIZED, MockResponses.A_UNAUTHORIZED);
        }
        return switch (mode("a")) {
            case "error" -> status(HttpStatus.SERVICE_UNAVAILABLE, MockResponses.A_UNAVAILABLE);
            case "no-response" -> hang();
            case "empty-body" -> emptyBody();
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
        if (containsFailCode("a", hotelCodes)) {
            return status(HttpStatus.SERVICE_UNAVAILABLE, MockResponses.A_UNAVAILABLE);
        }
        return switch (mode("a")) {
            case "error" -> status(HttpStatus.SERVICE_UNAVAILABLE, MockResponses.A_UNAVAILABLE);
            case "no-response" -> hang();
            case "empty-body" -> emptyBody();
            case "duplicate-items" -> ResponseEntity.ok(MockResponses.A_AVAILABILITY_DUPLICATED);
            case "slow" -> delayed(MockResponses.A_AVAILABILITY);
            default -> ResponseEntity.ok(MockResponses.A_AVAILABILITY);
        };
    }

    // ── Supplier B — 장애에도 200 + resultCode ──

    @GetMapping(value = "/b/api/properties", produces = APPLICATION_JSON_VALUE)
    ResponseEntity<String> propertiesB(@RequestHeader(value = "X-Api-Key", required = false) String apiKey) {
        if (isBlank(apiKey)) {
            return ResponseEntity.ok(MockResponses.B_UNAUTHORIZED);
        }
        return switch (mode("b")) {
            case "error" -> ResponseEntity.ok(MockResponses.B_UNAVAILABLE);
            case "no-response" -> hang();
            case "empty-body" -> emptyBody();
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
        if (containsFailCode("b", propertyIds)) {
            return ResponseEntity.ok(MockResponses.B_UNAVAILABLE);
        }
        return switch (mode("b")) {
            case "error" -> ResponseEntity.ok(MockResponses.B_UNAVAILABLE);
            case "no-response" -> hang();
            case "empty-body" -> emptyBody();
            case "slow" -> delayed(MockResponses.B_SEARCH);
            default -> ResponseEntity.ok(MockResponses.B_SEARCH);
        };
    }

    // ── 헬퍼 ──

    private String mode(String supplier) {
        return modes.getOrDefault(supplier, "normal");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private boolean containsFailCode(String supplier, String csv) {
        String failCode = failCodes.get(supplier);
        return failCode != null && List.of(csv.split(",", -1)).contains(failCode);
    }

    private static int countCodes(String csv) {
        return csv.isBlank() ? 0 : csv.split(",", -1).length;
    }

    private static ResponseEntity<String> status(HttpStatus status, String body) {
        return ResponseEntity.status(status).body(body);
    }

    /** N초 뒤 정상 응답 — 타임아웃 값을 정하거나 부하에서 스레드 점유를 관찰할 때. */
    private static ResponseEntity<String> delayed(String body) {
        try {
            Thread.sleep(SLOW_DELAY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ResponseEntity.ok(body);
    }

    /** 200 + 빈 본문. */
    private static ResponseEntity<String> emptyBody() {
        return ResponseEntity.ok().build();
    }

    /** 연결은 유지한 채 응답을 주지 않는다. */
    private static ResponseEntity<String> hang() {
        try {
            Thread.sleep(NO_RESPONSE_DELAY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ResponseEntity.ok(MockResponses.EMPTY);
    }
}
