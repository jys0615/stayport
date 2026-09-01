package io.github.jys0615.stayport.domain;

/**
 * 통합 검색 결과 1건 = 숙소 × 객실 타입 × 검색 조건.
 *
 * <p>공급사 코드는 여기 없다. 외부로 나가는 것은 매핑된 내부 식별자뿐이다.
 *
 * @param stayId            내부 숙소 식별자
 * @param stayName          숙소명 (매핑 동기화 시점의 스냅샷)
 * @param roomTypeId        내부 객실 타입 식별자
 * @param roomTypeName      객실 타입명 (매핑 동기화 시점의 스냅샷)
 * @param maxOccupancy      객실 1실의 최대 수용 인원 (성인+아동 합산)
 * @param availableRooms    기간 전체에 걸쳐 예약 가능한 객실 수 = min(날짜별 잔여). 0이면 예약 불가
 * @param breakfastIncluded 조식 포함 여부. 같은 객실도 공급사마다 다를 수 있어 상품 속성으로 유지한다
 * @param price             표준 요금
 * @param supplier          이 상품의 출처
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

    /** 예약 가능 여부. 재고가 0인 상품도 응답에서 빼지 않고 이 값으로 구분한다. */
    public boolean bookable() {
        return availableRooms > 0;
    }
}
