package io.github.jys0615.stayport.application.port;

/**
 * 객실 타입 하나.
 *
 * @param code         숙소 안에서만 유일한 코드. 다른 숙소에 같은 코드가 있을 수 있으므로
 *                     매핑 키에는 반드시 숙소 코드가 함께 들어가야 한다
 * @param name         객실 타입명
 * @param maxOccupancy 최대 수용 인원 (성인+아동 합산)
 */
public record SupplierRoomType(String code, String name, int maxOccupancy) {
}
