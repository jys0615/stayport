package io.github.jys0615.stayport.adapter;

import io.github.jys0615.stayport.application.port.FailureType;
import io.netty.channel.ConnectTimeoutException;
import java.util.concurrent.TimeoutException;
import org.springframework.core.codec.CodecException;
import org.springframework.http.HttpStatusCode;

/** 어댑터 공통 실패 분류 — 예외·HTTP 상태를 FailureType으로. */
public final class SupplierErrors {

    /** 원인 사슬 순회 깊이 상한 (순환 참조 방어). */
    private static final int MAX_CAUSE_DEPTH = 10;

    private SupplierErrors() {
    }

    /** 예외 분류. 모르는 실패는 SUPPLIER_ERROR. */
    public static FailureType classify(Throwable error) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (cause instanceof TimeoutException || cause instanceof ConnectTimeoutException) {
                return FailureType.TIMEOUT;
            }
            if (cause instanceof CodecException) {
                return FailureType.PARSE_ERROR;
            }
            if (cause == cause.getCause()) {
                break;
            }
            cause = cause.getCause();
        }
        return FailureType.SUPPLIER_ERROR;
    }

    /** HTTP 상태 코드 분류. */
    public static FailureType classify(HttpStatusCode status) {
        int code = status.value();
        if (code == 401 || code == 403) {
            return FailureType.AUTH;
        }
        if (code == 429) {
            return FailureType.RATE_LIMIT;
        }
        if (code >= 400 && code < 500) {
            return FailureType.INVALID_REQUEST;
        }
        return FailureType.SUPPLIER_ERROR;
    }
}
