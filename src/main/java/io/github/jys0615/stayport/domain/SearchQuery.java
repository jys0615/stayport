package io.github.jys0615.stayport.domain;

import java.time.LocalDate;

/**
 * 검색 조건 — 날짜와 인원뿐, 대상은 보유 숙소 전체.
 *
 * @param checkOut 체크아웃일 (숙박일에 포함되지 않음)
 */
public record SearchQuery(LocalDate checkIn, LocalDate checkOut, int adults, int children) {

    public SearchQuery {
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("checkOut must be after checkIn");
        }
        if (adults < 1) {
            throw new IllegalArgumentException("adults must be at least 1");
        }
        if (children < 0) {
            throw new IllegalArgumentException("children must not be negative");
        }
    }

    /** 숙박일 수 (체크아웃일 제외). */
    public int nights() {
        return (int) (checkOut.toEpochDay() - checkIn.toEpochDay());
    }

    /** 총 투숙 인원. */
    public int totalGuests() {
        return adults + children;
    }
}
