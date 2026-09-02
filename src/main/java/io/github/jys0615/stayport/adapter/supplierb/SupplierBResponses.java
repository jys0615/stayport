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
}
