package io.github.jys0615.stayport.application.port;

/**
 * 공급사 실패의 분류. 두 공급사의 실패 표현 방식이 서로 다르므로(A는 HTTP 상태, B는 본문
 * {@code resultCode}) 어댑터에서 이 하나의 축으로 접어 넘긴다. 판정 통일이 곧 이 열거형이다.
 */
public enum FailureType {

    /** 우리가 잘못 보냈다. 재시도해도 같은 결과다. */
    INVALID_REQUEST,

    /** 인증 실패. 키 설정 문제이므로 재시도 대상이 아니다. */
    AUTH,

    /** 호출 한도 초과. */
    RATE_LIMIT,

    /** 공급사 내부 오류 또는 일시적 장애. */
    SUPPLIER_ERROR,

    /** 예산 안에 응답이 오지 않았다. */
    TIMEOUT,

    /** 응답은 왔으나 우리가 아는 형태가 아니다. */
    PARSE_ERROR
}
