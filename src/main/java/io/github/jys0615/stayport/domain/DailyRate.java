package io.github.jys0615.stayport.domain;

import java.time.LocalDate;

/**
 * 날짜별 요금 한 줄.
 *
 * @param grossAmount 그날 1박의 세금 포함 금액 (공통 의미는 이것 하나)
 * @param netAmount   분해를 주지 않는 공급사에서는 null. taxAmount도 같다
 */
public record DailyRate(LocalDate date, long grossAmount, Long netAmount, Long taxAmount) {

    public static DailyRate grossOnly(LocalDate date, long grossAmount) {
        return new DailyRate(date, grossAmount, null, null);
    }

    public static DailyRate decomposed(LocalDate date, long netAmount, long taxAmount) {
        return new DailyRate(date, netAmount + taxAmount, netAmount, taxAmount);
    }
}
