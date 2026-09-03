package io.github.jys0615.stayport.application.port;

/**
 * 공급사 실패의 분류. 두 공급사의 실패 표현 방식이 서로 다르므로(A는 HTTP 상태, B는 본문
 * {@code resultCode}) 어댑터에서 이 하나의 축으로 접어 넘긴다. 판정 통일이 곧 이 열거형이다.
 *
 * <p><b>선언 순서가 보고 우선순위다.</b> 여러 실패가 섞였을 때 어느 것을 대표로 알릴지
 * 정해야 하는데, 기준은 "재시도해도 달라지지 않는 것을 먼저 알린다"다. 다시 불러서 해결될
 * 문제와 우리가 고쳐야 할 문제가 섞여 있으면, 후자가 묻히는 쪽이 더 위험하다.
 *
 * <p>순서에 의미를 둔 이상 항목을 중간에 끼워 넣을 때 우선순위를 다시 판단해야 한다.
 */
public enum FailureType {

    // ── 다시 불러도 같은 결과 — 우리가 고쳐야 한다 ──────────────────────────

    /** 인증 실패. 키 설정 문제다. */
    AUTH,

    /** 우리가 잘못 보냈다. */
    INVALID_REQUEST,

    /** 응답은 왔으나 우리가 아는 형태가 아니다. 계약이 어긋났다는 뜻이다. */
    PARSE_ERROR,

    // ── 시간이 지나면 달라질 수 있다 ────────────────────────────────────────

    /** 호출 한도 초과. 간격을 두면 풀린다. */
    RATE_LIMIT,

    /** 공급사 내부 오류 또는 일시적 장애. */
    SUPPLIER_ERROR,

    /** 예산 안에 응답이 오지 않았다. */
    TIMEOUT;

    /**
     * 섞인 실패들 중 대표로 알릴 하나를 고른다.
     *
     * <p>선언 순서에서 가장 앞선 것을 고르므로 호출 완료 순서와 무관하게 결과가 같다.
     * 병렬 호출은 끝나는 순서가 매번 다르기 때문에, 이 결정이 순서에 의존하면 같은 요청이
     * 실행마다 다른 분류를 내놓는다.
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
