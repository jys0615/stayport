package io.github.jys0615.stayport.application.port;

/**
 * 정규화에서 버린 상품 1건의 기록.
 *
 * @param reason  버린 이유 (형태 이상, 중복 등)
 * @param payload 원본 항목의 JSON. 직렬화가 안 되면 null — 기록이 정규화를 방해하면 안 된다
 */
public record SkippedOffer(String reason, String payload) {
}
