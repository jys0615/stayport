package io.github.jys0615.stayport.api;

import io.github.jys0615.stayport.application.SearchResult;
import io.github.jys0615.stayport.application.StaySearchService;
import io.github.jys0615.stayport.domain.SearchQuery;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통합 검색 API.
 *
 * <p><b>요청 검증 실패만 400이고 나머지는 전부 200이다.</b> 두 공급사가 모두 죽어도
 * {@code 200}에 {@code suppliers: [FAILED, FAILED]}를 담아 돌려준다.
 *
 * <p>스스로도 불편한 결정이라 반대 논거를 먼저 적어본다 — "아무것도 못 가져왔는데 200은
 * 거짓말 아닌가". 그래도 200인 이유는 이 API의 실패가 <b>공급사 단위로 부분적</b>이기
 * 때문이다. 응답 전체를 5xx로 접는 순간 "A는 됐고 B만 죽었다"를 표현할 자리가 사라지고,
 * 그러면 부분 실패 허용이 설계에만 있고 계약에는 없는 것이 된다. 클라이언트의 재시도 판단도
 * 상태 코드가 아니라 {@code suppliers[].status}를 보고 공급사 단위로 내려야 맞다.
 */
@RestController
class StaySearchController {

    private final StaySearchService searchService;

    StaySearchController(StaySearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/api/v1/stays/search")
    ResponseEntity<?> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam int adults,
            @RequestParam(defaultValue = "0") int children) {

        SearchQuery query;
        try {
            query = new SearchQuery(checkIn, checkOut, adults, children);
        } catch (IllegalArgumentException invalid) {
            // 조건 자체가 성립하지 않는 요청이다. 공급사를 부르기 전에 여기서 끊는다.
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_REQUEST",
                    "message", invalid.getMessage()));
        }

        SearchResult result = searchService.search(query);
        return ResponseEntity.ok(StaySearchResponse.of(query, result));
    }
}
