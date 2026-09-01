package io.github.jys0615.stayport.domain;

import java.util.List;

/**
 * 표준 요금. 두 공급사 모두에서 산출 가능한 값이 "기간 총액·세금 포함"뿐이라 그것을 공통 필드로 삼았다.
 *
 * <p>날짜별 분해는 주는 공급사에서만 채워지는 선택 필드다. 공통 분모에 맞춰 버리는 대신
 * optional로 보존해 정보 손실을 막는다.
 *
 * @param totalAmount     숙박 기간 전체 총액 — 세금 포함(gross)
 * @param currency        ISO 4217 통화 코드
 * @param dailyBreakdown  날짜별 분해. 제공하지 않는 공급사에서는 null
 */
public record Price(long totalAmount, String currency, List<DailyRate> dailyBreakdown) {

    public Price {
        dailyBreakdown = dailyBreakdown == null ? null : List.copyOf(dailyBreakdown);
    }

    public static Price of(long totalAmount, String currency) {
        return new Price(totalAmount, currency, null);
    }
}
