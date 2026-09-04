package io.github.jys0615.stayport.adapter.suppliera;

import java.util.List;

/** Supplier A 응답 DTO — 이 패키지 밖으로 나가지 않는다. */
final class SupplierAResponses {

    private SupplierAResponses() {
    }

    record Hotels(List<Hotel> items) {
    }

    record Hotel(String hotelCode, String hotelName, List<RoomType> roomTypes) {
    }

    record RoomType(String roomTypeCode, String roomTypeName, int maxOccupancy) {
    }

    record Availability(List<Item> items) {
    }

    /** {@code breakfastIncluded}는 재고·요금 API에만 있다. */
    record Item(
            String hotelCode,
            String hotelName,
            String roomTypeCode,
            String roomTypeName,
            int maxOccupancy,
            boolean breakfastIncluded,
            String currency,
            List<DailyRate> dailyRates) {
    }

    /**
     * 날짜는 String으로 받고 어댑터가 파싱한다 — 코덱에 맡기면 형식 변경 시 응답 전체가 깨진다.
     * nightlyRate는 세금 별도(net).
     */
    record DailyRate(String date, int remainingRooms, long nightlyRate, long taxAmount) {
    }
}
