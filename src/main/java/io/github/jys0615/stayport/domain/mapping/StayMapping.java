package io.github.jys0615.stayport.domain.mapping;

import io.github.jys0615.stayport.domain.SupplierId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 공급사 숙소 코드 ↔ 내부 숙소 식별자.
 *
 * <p>이 행의 PK가 곧 내부 숙소 식별자다. 그래서 A의 숙소와 B의 숙소는 실제로 같은 건물이어도
 * 서로 다른 내부 식별자를 갖는다 — 두 공급사를 잇는 공통 키가 스펙에 없으므로 지금은
 * 합치지 않는 것이 맞다고 봤다.
 *
 * <p>UNIQUE 제약이 "같은 공급사 상품은 항상 같은 내부 식별자"를 DB 수준에서 보장한다.
 * 애플리케이션의 조회-후-삽입은 정상 경로이고, 이 제약은 그 전제가 깨졌을 때의 안전망이다.
 */
@Entity
@Table(
        name = "stay_mapping",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stay_mapping",
                columnNames = {"supplier", "supplier_stay_code"}))
public class StayMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private SupplierId supplier;

    @Column(name = "supplier_stay_code", nullable = false, length = 64)
    private String supplierStayCode;

    /**
     * 동기화 시점의 숙소명 스냅샷.
     *
     * <p>재고 조회 응답에도 숙소명이 오지만 검색 결과는 이 값을 쓴다. 표시 값의 출처가 두 곳이면
     * 같은 숙소가 경로에 따라 다른 이름으로 보이게 되므로, 갱신을 동기화의 책임으로 몰았다.
     * 대신 동기화 주기가 이름의 신선도 상한이 된다.
     */
    @Column(name = "stay_name", length = 200)
    private String stayName;

    protected StayMapping() {
    }

    public StayMapping(SupplierId supplier, String supplierStayCode, String stayName) {
        this.supplier = supplier;
        this.supplierStayCode = supplierStayCode;
        this.stayName = stayName;
    }

    public void renameTo(String newName) {
        this.stayName = newName;
    }

    public Long id() {
        return id;
    }

    public SupplierId supplier() {
        return supplier;
    }

    public String supplierStayCode() {
        return supplierStayCode;
    }

    public String stayName() {
        return stayName;
    }
}
