package io.github.jys0615.stayport.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 요청이 성립하지 않는 경우들을 한 가지 400 형태로 모은다.
 *
 * <p>스프링 기본 동작은 바인딩 단계 실패(파라미터 누락·형식 오류)와 우리 검증 실패가 서로 다른
 * 본문으로 나가게 한다. 클라이언트 입장에선 전부 "요청이 잘못됐다"인데 스키마가 갈라질 이유가 없다.
 */
@RestControllerAdvice
class ApiErrorHandler {

    /** 필수 파라미터 누락 (예: adults 없음). */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError missingParameter(MissingServletRequestParameterException e) {
        return ApiError.invalidRequest("필수 파라미터가 없다: " + e.getParameterName());
    }

    /** 형식이 맞지 않는 파라미터 (예: checkIn=2026-13-99). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError typeMismatch(MethodArgumentTypeMismatchException e) {
        return ApiError.invalidRequest("파라미터 형식이 맞지 않는다: " + e.getName());
    }

    /** 값은 읽었지만 조건이 성립하지 않는 요청 (예: checkOut <= checkIn). */
    @ExceptionHandler(InvalidSearchRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError invalidSearch(InvalidSearchRequestException e) {
        return ApiError.invalidRequest(e.getMessage());
    }
}
