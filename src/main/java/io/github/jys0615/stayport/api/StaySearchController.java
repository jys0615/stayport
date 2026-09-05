package io.github.jys0615.stayport.api;

import io.github.jys0615.stayport.application.SearchResult;
import io.github.jys0615.stayport.application.StaySearchService;
import io.github.jys0615.stayport.domain.SearchQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "검색", description = "고객용 통합 숙소 검색")
class StaySearchController {

    private final StaySearchService searchService;

    StaySearchController(StaySearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(
            summary = "날짜·인원으로 보유 숙소를 검색한다",
            description = """
                    공급사들을 동시에 조회해 하나의 표준 형태로 돌려줍니다. 요청이 성립하면 항상 200이고,
                    공급사 장애는 응답 본문의 suppliers[]에 상태로 담깁니다 — 한 공급사가 실패하거나
                    늦어도 이미 받은 다른 공급사의 결과는 그대로 반환합니다. 자세한 계약은 docs/api.md.
                    """)
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
