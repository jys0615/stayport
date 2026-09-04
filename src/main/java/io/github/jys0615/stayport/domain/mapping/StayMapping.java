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
 * 공급사 숙소 코드 ↔ 내부 숙소 식별자. PK가 곧 내부 식별자다.
 * 불변식(UNIQUE): 같은 공급사 상품은 항상 같은 내부 식별자. 공급사 간 병합은 하지 않는다(design.md §4).
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

    /** 동기화 시점의 숙소명 스냅샷 — 검색 응답의 표시 이름은 이 값이다(design.md §4). */
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
