package io.github.jys0615.stayport.application.port;

/**
 * 공급사 실패 분류. A의 HTTP 상태와 B의 resultCode가 어댑터에서 이 한 축으로 변환된다.
 *
 * <p><b>선언 순서 = 보고 우선순위.</b> 기준은 "재시도해도 달라지지 않는 것 먼저"
 * (근거: design.md §6). 항목을 끼워 넣을 때 순서를 다시 판단할 것.
 */
public enum FailureType {

    // 재시도해도 같은 결과 (우리 쪽 문제)
    AUTH,
    INVALID_REQUEST,
    /** 응답이 왔으나 아는 형태가 아니다 — 계약 불일치. */
    PARSE_ERROR,

    // 시간이 지나면 달라질 수 있음
    RATE_LIMIT,
    SUPPLIER_ERROR,
    TIMEOUT,

    /**
     * 서킷이 열려 있어 아예 부르지 않았다. 원인은 직전의 다른 실패들이므로 대표값 경쟁에서는
     * 가장 뒤 — 같은 응답에 TIMEOUT이 섞였다면 그쪽이 더 설명력 있다.
     */
    CIRCUIT_OPEN;

    /**
     * 섞인 실패 중 대표 하나. 선언 순서 기준이므로 호출 완료 순서와 무관하게 결정적이다.
     */
    public static FailureType mostSignificant(Iterable<FailureType> types) {
        FailureType chosen = null;
        for (FailureType type : types) {
            if (chosen == null || type.ordinal() < chosen.ordinal()) {
                chosen = type;
            }
        }
        if (chosen == null) {
            throw new IllegalArgumentException("실패가 하나도 없다");
        }
        return chosen;
    }
}
