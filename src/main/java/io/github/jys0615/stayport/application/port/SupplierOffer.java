package io.github.jys0615.stayport.application.port;

import io.github.jys0615.stayport.domain.Price;

/**
 * 어댑터가 내놓는 상품 1건 — 아직 공급사 코드로 표현된 상태다.
 *
 * <p>어댑터는 내부 식별자를 모른다. 매핑을 읽는 것은 유스케이스의 일이고, 어댑터에 매핑을
 * 넘겨주면 공급사 연동이 우리 저장소를 알게 된다. 그래서 경계에서 한 번 갈아탄다 —
 * 어댑터는 공급사 코드로 내놓고, 유스케이스가 그것을 내부 식별자로 바꿔 최종 응답을 만든다.
 *
 * <p>정규화(요금 합산, 재고 min, 조식 보존)는 여기서 이미 끝나 있다. 공급사별 차이를 흡수하는
 * 것이 어댑터의 일이기 때문이다. 남은 것은 식별자 해석뿐이다.
 *
 * @param stayCode        공급사 숙소 코드
 * @param stayName        공급사가 준 숙소명. 표시에는 쓰지 않는다 — 매핑에 저장된 스냅샷이 정본이다
 * @param roomCode        공급사 객실 타입 코드
 * @param roomTypeName    공급사가 준 객실 타입명. 위와 같다
 * @param maxOccupancy    최대 수용 인원
 * @param availableRooms  기간 전체에 걸쳐 예약 가능한 객실 수 = min(날짜별 잔여)
 * @param breakfastIncluded 조식 포함 여부
 * @param price           표준 요금
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
