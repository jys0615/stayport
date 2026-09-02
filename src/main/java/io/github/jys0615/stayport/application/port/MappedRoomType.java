package io.github.jys0615.stayport.application.port;

import io.github.jys0615.stayport.domain.SupplierId;

/**
 * 매핑 한 줄을 평평하게 펼친 형태 — 객실 타입 하나와 그것이 속한 숙소의 내부 식별자·이름.
 *
 * <p>검색은 "우리가 가진 숙소를 공급사별 코드로 묶어" 물어본 뒤, 돌아온 응답을 내부 식별자로
 * 되돌려야 한다. 양방향에 필요한 정보가 이 한 줄에 다 들어 있다.
 */
public record MappedRoomType(
        SupplierId supplier,
        String supplierStayCode,
        String supplierRoomCode,
        long internalStayId,
        String stayName,
        long internalRoomTypeId,
        String roomTypeName,
        int maxOccupancy) {
}
