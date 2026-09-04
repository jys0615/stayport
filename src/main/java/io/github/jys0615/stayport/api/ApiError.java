package io.github.jys0615.stayport.api;

/**
 * 4xx 오류 응답의 단일 형태. 파라미터 바인딩 실패든 도메인 검증 실패든 같은 모양으로 나간다 —
 * 같은 종류의 오류가 두 가지 스키마로 나가면 클라이언트가 둘 다 파싱해야 한다.
 */
record ApiError(String error, String message) {

    static ApiError invalidRequest(String message) {
        return new ApiError("INVALID_REQUEST", message);
    }
}
