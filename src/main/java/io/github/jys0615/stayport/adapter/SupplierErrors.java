package io.github.jys0615.stayport.adapter;

import io.github.jys0615.stayport.application.port.FailureType;
import io.netty.channel.ConnectTimeoutException;
import java.util.concurrent.TimeoutException;
import org.springframework.core.codec.CodecException;
import org.springframework.http.HttpStatusCode;

/**
 * 어댑터 공통 실패 분류.
 *
 * <p>공급사가 실패를 알리는 방식(A는 HTTP 상태, B는 본문 코드)은 각 어댑터가 알지만,
 * "타임아웃이냐 파싱 실패냐"는 두 어댑터가 똑같이 판단한다. 그 부분만 여기 모았다.
 */
public final class SupplierErrors {

    /** 예외 원인 사슬을 따라갈 깊이 상한. 순환 참조에 걸려 무한히 돌지 않도록 둔다. */
    private static final int MAX_CAUSE_DEPTH = 10;

    private SupplierErrors() {
    }

    /**
     * 호출 중 던져진 예외를 분류한다. 여기서 걸러지지 않는 것은 전부 공급사 쪽 문제로 본다 —
     * 우리가 원인을 모르는 실패를 "요청이 잘못됐다"로 적으면 재시도 판단이 틀어진다.
     */
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

    /** HTTP 상태 코드로 실패를 알리는 공급사용. */
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
