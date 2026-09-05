package io.github.jys0615.stayport.domain.quarantine;

import io.github.jys0615.stayport.domain.SupplierId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 변환할 수 없어 응답에서 뺀 상품의 격리 보관.
 *
 * <p>버린 것을 개수(skippedItems)로만 남기면 무엇이 왜 버려졌는지가 사라진다. 원본을 남겨야
 * 공급사가 형식을 바꿨는지 우리 파서가 틀렸는지를 나중에 판별할 수 있다.
 * 메시징 인프라가 있다면 DLQ 토픽이 맡을 자리 — 단일 인스턴스에서는 테이블이 같은 역할을 한다.
 */
@Entity
@Table(name = "quarantined_offer")
public class QuarantinedOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private SupplierId supplier;

    @Column(nullable = false, length = 100)
    private String reason;

    /** 원본 항목 JSON. 직렬화 실패 시 null. */
    @Lob
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected QuarantinedOffer() {
    }

    public QuarantinedOffer(SupplierId supplier, String reason, String payload) {
        this.supplier = supplier;
        this.reason = reason;
        this.payload = payload;
        this.occurredAt = Instant.now();
    }

    public Long id() {
        return id;
    }

    public SupplierId supplier() {
        return supplier;
    }

    public String reason() {
        return reason;
    }

    public String payload() {
        return payload;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}
