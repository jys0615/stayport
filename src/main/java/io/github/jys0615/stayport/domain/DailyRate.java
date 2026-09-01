package io.github.jys0615.stayport.domain;

import java.time.LocalDate;

/**
 * 날짜별 요금 한 줄. 공통 의미는 {@code grossAmount}(세금 포함) 하나뿐이고,
 * net·tax 분해는 그것을 주는 공급사에서만 채워진다.
 *
 * @param date        숙박일
 * @param grossAmount 그날 1박의 세금 포함 금액
 * @param netAmount   세금 별도 금액. 분해를 제공하지 않는 공급사에서는 null
 * @param taxAmount   세금. 분해를 제공하지 않는 공급사에서는 null
 */
public record DailyRate(LocalDate date, long grossAmount, Long netAmount, Long taxAmount) {

    public static DailyRate grossOnly(LocalDate date, long grossAmount) {
        return new DailyRate(date, grossAmount, null, null);
    }

    public static DailyRate decomposed(LocalDate date, long netAmount, long taxAmount) {
        return new DailyRate(date, netAmount + taxAmount, netAmount, taxAmount);
    }
}
