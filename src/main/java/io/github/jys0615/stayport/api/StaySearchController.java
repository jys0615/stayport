package io.github.jys0615.stayport.api;

import io.github.jys0615.stayport.application.SearchResult;
import io.github.jys0615.stayport.application.StaySearchService;
import io.github.jys0615.stayport.domain.SearchQuery;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통합 검색 API. 계약: 요청 검증 실패만 400, 나머지는 전부 200 —
 * 공급사 실패는 suppliers[].status로 표현한다. 근거는 docs/design.md §7.
 */
@RestController
class StaySearchController {

    private final StaySearchService searchService;

    StaySearchController(StaySearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/api/v1/stays/search")
    StaySearchResponse search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam int adults,
            @RequestParam(defaultValue = "0") int children) {

        SearchQuery query;
        try {
            query = new SearchQuery(checkIn, checkOut, adults, children);
        } catch (IllegalArgumentException invalid) {
            // 성립하지 않는 조건은 공급사를 부르기 전에 끊는다. 400 형태는 ApiErrorHandler가 통일한다.
            throw new InvalidSearchRequestException(invalid.getMessage());
        }

        SearchResult result = searchService.search(query);
        return StaySearchResponse.of(query, result);
    }
}
