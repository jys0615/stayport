package io.github.jys0615.stayport.api;

/**
 * 검색 조건이 성립하지 않는 요청. IllegalArgumentException을 그대로 advice에서 받으면
 * 무관한 IAE까지 400으로 가려지므로 API 계층 전용 예외로 감싼다.
 */
class InvalidSearchRequestException extends RuntimeException {

    InvalidSearchRequestException(String message) {
        super(message);
    }
}
