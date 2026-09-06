package io.github.jys0615.stayport.api;

import io.github.jys0615.stayport.domain.quarantine.QuarantinedOffer;
import io.github.jys0615.stayport.domain.SupplierId;
import java.time.Instant;

/**
 * 격리 기록의 응답 형태. 엔티티를 그대로 내보내면 두 가지가 깨진다 — 접근자가 {@code getX()}가
 * 아니라 직렬화에서 필드가 통째로 빠지고, 저장 구조를 바꿀 때 API 계약까지 같이 바뀐다.
 */
record QuarantinedOfferView(
        Long id, SupplierId supplier, String reason, String payload, Instant occurredAt) {

    static QuarantinedOfferView of(QuarantinedOffer offer) {
        return new QuarantinedOfferView(
                offer.id(), offer.supplier(), offer.reason(), offer.payload(), offer.occurredAt());
    }
}
