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
 * 공급사 객실 타입 코드 ↔ 내부 객실 타입 식별자.
 * 객실 코드는 숙소 안에서만 유일하므로 UNIQUE 키에 숙소 코드가 함께 들어간다.
 */
@Entity
@Table(
        name = "room_type_mapping",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_room_type_mapping",
                columnNames = {"supplier", "supplier_stay_code", "supplier_room_code"}))
public class RoomTypeMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private SupplierId supplier;

    @Column(name = "supplier_stay_code", nullable = false, length = 64)
    private String supplierStayCode;

    @Column(name = "supplier_room_code", nullable = false, length = 64)
    private String supplierRoomCode;

    /** 소속 숙소의 내부 식별자. */
    @Column(name = "internal_stay_id", nullable = false)
    private Long internalStayId;

    /** 동기화 시점의 객실 타입명 스냅샷. */
    @Column(name = "room_type_name", length = 200)
    private String roomTypeName;

    @Column(name = "max_occupancy", nullable = false)
    private int maxOccupancy;

    protected RoomTypeMapping() {
    }

    public RoomTypeMapping(
            SupplierId supplier,
            String supplierStayCode,
            String supplierRoomCode,
            Long internalStayId,
            String roomTypeName,
            int maxOccupancy) {
        this.supplier = supplier;
        this.supplierStayCode = supplierStayCode;
        this.supplierRoomCode = supplierRoomCode;
        this.internalStayId = internalStayId;
        this.roomTypeName = roomTypeName;
        this.maxOccupancy = maxOccupancy;
    }

    public void refresh(String newName, int newMaxOccupancy) {
        this.roomTypeName = newName;
        this.maxOccupancy = newMaxOccupancy;
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

    public String supplierRoomCode() {
        return supplierRoomCode;
    }

    public Long internalStayId() {
        return internalStayId;
    }

    public String roomTypeName() {
        return roomTypeName;
    }

    public int maxOccupancy() {
        return maxOccupancy;
    }
}
