package io.github.jys0615.stayport.adapter.suppliera;

import java.util.List;

/**
 * Supplier A의 응답 형태. 이 타입들은 이 패키지를 벗어나지 않는다.
 *
 * <p>{@code roomTypes}는 숙소 목록 API에만 있다.
 */
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
     * 날짜를 {@code String}으로 받는다.
     *
     * <p>코덱에 날짜 형식을 맡기면 공급사가 형식을 바꿀 때 역직렬화 단계에서 응답 전체가 깨진다.
     * 어댑터가 직접 파싱하면 그 한 건만 건너뛸 수 있고, 형식 변환이 경계에 남는다.
     *
     * <p>{@code nightlyRate}는 세금 별도(net)이고, 그날 고객이 낼 금액은
     * {@code nightlyRate + taxAmount}다.
     */
    record DailyRate(String date, int remainingRooms, long nightlyRate, long taxAmount) {
    }
}
