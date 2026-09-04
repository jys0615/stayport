package io.github.jys0615.stayport.application.port;

import io.github.jys0615.stayport.domain.SupplierId;
import java.util.List;

/** 매핑 저장소 포트. */
public interface MappingStore {

    /**
     * 숙소 매핑 upsert. 불변식: 같은 (supplier, stayCode)는 항상 같은 식별자를 돌려준다.
     */
    long upsertStay(SupplierId supplier, String stayCode, String stayName);

    /** 객실 타입 매핑 upsert. 불변식은 위와 같다. */
    long upsertRoomType(
            SupplierId supplier,
            String stayCode,
            String roomCode,
            long internalStayId,
            String roomTypeName,
            int maxOccupancy);

    /** 저장된 매핑 전체. 수천 규모부터는 공급사별 조회로 좁힌다(design.md §5). */
    List<MappedRoomType> findAll();

    long countStays();

    long countRoomTypes();
}
