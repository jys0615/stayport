package io.github.jys0615.stayport.adapter.supplierb;

import java.util.List;

/** Supplier B 응답 DTO — 이 패키지 밖으로 나가지 않는다. data는 성공 시에만 값이 있다. */
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

    /** @param totalPrice 기간 전체 총액(세금 포함) — 1박 요금이 아니다 */
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

    /** 날짜를 String으로 받는 이유는 A와 같다. */
    record Inventory(String date, int remainingRooms) {
    }
}
