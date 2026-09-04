package io.github.jys0615.stayport.domain;

/**
 * 통합 검색 결과 1건 = 숙소 × 객실 타입 × 검색 조건. 공급사 코드는 담지 않는다.
 *
 * @param stayName       매핑 동기화 시점의 스냅샷
 * @param availableRooms min(날짜별 잔여). 0이면 예약 불가
 */
public record StayOffer(
        long stayId,
        String stayName,
        long roomTypeId,
        String roomTypeName,
        int maxOccupancy,
        int availableRooms,
        boolean breakfastIncluded,
        Price price,
        SupplierId supplier) {

    /** 재고 0인 상품도 응답에 남기고 이 값으로 구분한다. */
    public boolean bookable() {
        return availableRooms > 0;
    }
}
