package io.github.jys0615.stayport.domain;

import java.util.List;

/**
 * 표준 요금 (근거: design.md §2).
 *
 * @param totalAmount    숙박 기간 전체 총액, 세금 포함(gross)
 * @param dailyBreakdown 날짜별 분해 — 주지 않는 공급사에서는 null
 */
public record Price(long totalAmount, String currency, List<DailyRate> dailyBreakdown) {

    public Price {
        dailyBreakdown = dailyBreakdown == null ? null : List.copyOf(dailyBreakdown);
    }

    public static Price of(long totalAmount, String currency) {
        return new Price(totalAmount, currency, null);
    }
}
