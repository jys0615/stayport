package io.github.jys0615.stayport.application.port;

import io.github.jys0615.stayport.domain.SupplierId;

/** 매핑 한 줄 — 객실 타입과 소속 숙소의 내부 식별자·이름 스냅샷. */
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
