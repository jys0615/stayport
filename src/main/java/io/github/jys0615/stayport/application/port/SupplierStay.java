package io.github.jys0615.stayport.application.port;

import java.util.List;

/**
 * 공급사가 취급하는 숙소 하나. 공급사별 필드명 차이는 어댑터에서 이미 흡수된 뒤의 형태다.
 *
 * @param code      공급사 안에서 유일한 숙소 코드
 * @param name      숙소명
 * @param roomTypes 이 숙소의 객실 타입 목록
 */
public record SupplierStay(String code, String name, List<SupplierRoomType> roomTypes) {

    public SupplierStay {
        roomTypes = List.copyOf(roomTypes);
    }
}
