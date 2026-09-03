package io.github.jys0615.stayport.adapter.supplierb;

import java.util.List;

/**
 * Supplier B의 응답 형태. 이 타입들은 이 패키지를 벗어나지 않는다.
 *
 * <p>B는 실패해도 HTTP 200을 주고 {@code resultCode}로만 알린다. 그래서 모든 응답이
 * {@code resultCode}로 감싸여 있고 {@code data}는 성공했을 때만 값이 있다.
 */
final class SupplierBResponses {

    private SupplierBResponses() {
    }

    record Properties(String resultCode, String resultMessage, PropertyData data) {
    }

    record PropertyData(List<Property> items) {
    }

    record Property(String propertyId, String propertyName, List<Room> rooms) {
    }

    record Room(String roomId, String roomName, int maxOccupancy) {
    }

    record Search(String resultCode, String resultMessage, SearchData data) {
    }

    record SearchData(List<Item> items) {
    }

    /**
     * @param totalPrice  <b>요청한 숙박 기간 전체의 총액</b>이며 세금이 포함되어 있다.
     *                    1박 요금으로 오해하면 기간 곱하기만큼 틀린다
     * @param taxIncluded 항상 true다. 세금 금액은 따로 주지 않는다
     */
    record Item(
            String propertyId,
            String propertyName,
            String roomId,
            String roomName,
            int maxOccupancy,
            boolean breakfastIncluded,
            String currency,
            long totalPrice,
            boolean taxIncluded,
            List<Inventory> inventory) {
    }

    /** 날짜를 {@code String}으로 받는 이유는 A의 {@code DailyRate}와 같다. */
    record Inventory(String date, int remainingRooms) {
    }
}
