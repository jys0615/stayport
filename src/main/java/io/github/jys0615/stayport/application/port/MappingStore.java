package io.github.jys0615.stayport.application.port;

import io.github.jys0615.stayport.domain.SupplierId;
import java.util.List;

/**
 * 매핑 저장소 포트.
 *
 * <p>유스케이스는 "있으면 그대로, 없으면 만들어서 내부 식별자를 돌려달라"만 요구한다.
 * 그것이 JPA인지 무엇인지는 어댑터의 사정이다.
 */
public interface MappingStore {

    /**
     * 숙소 매핑을 넣거나 갱신하고 내부 숙소 식별자를 돌려준다.
     *
     * <p>같은 {@code (supplier, stayCode)}로 다시 불러도 항상 같은 값을 돌려줘야 한다.
     * 이름이 바뀌었으면 갱신한다.
     */
    long upsertStay(SupplierId supplier, String stayCode, String stayName);

    /** 객실 타입 매핑을 넣거나 갱신하고 내부 객실 타입 식별자를 돌려준다. */
    long upsertRoomType(
            SupplierId supplier,
            String stayCode,
            String roomCode,
            long internalStayId,
            String roomTypeName,
            int maxOccupancy);

    /**
     * 저장된 매핑 전체.
     *
     * <p>검색이 공급사에 물어볼 코드 목록을 여기서 얻고, 돌아온 응답을 내부 식별자로 되돌릴 때도
     * 같은 자료를 쓴다. 지금 규모(숙소 수백)에서는 전량을 읽어 메모리에서 묶는 것이 가장 단순하다.
     * 수천 규모가 되면 공급사별 조회로 좁히고, 그 다음은 사전 집계 캐시로 간다.
     */
    List<MappedRoomType> findAll();

    long countStays();

    long countRoomTypes();
}
