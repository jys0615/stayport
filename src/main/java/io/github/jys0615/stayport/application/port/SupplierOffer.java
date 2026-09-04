package io.github.jys0615.stayport.application.port;

import io.github.jys0615.stayport.domain.Price;

/**
 * 어댑터가 반환하는 상품 1건. 정규화는 끝났지만 아직 공급사 코드 기준이다 —
 * 어댑터는 매핑을 모르므로 내부 식별자 해석은 유스케이스가 한다(design.md §6).
 *
 * @param stayName        공급사가 준 숙소명. 표시에는 매핑 스냅샷을 쓰므로 참고용
 * @param availableRooms  min(날짜별 잔여) — 기간 전체 예약 가능 수
 */
public record SupplierOffer(
        String stayCode,
        String stayName,
        String roomCode,
        String roomTypeName,
        int maxOccupancy,
        int availableRooms,
        boolean breakfastIncluded,
        Price price) {
}
