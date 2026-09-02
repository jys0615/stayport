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
}
