package io.github.jys0615.stayport.application;

import io.github.jys0615.stayport.application.port.FailureType;
import io.github.jys0615.stayport.domain.SupplierId;
import java.util.List;

/**
 * 동기화 한 번의 결과 — 공급사별 성패를 따로 담는다.
 *
 * @param skipped 이미 실행 중이어서 아무것도 하지 않았다는 뜻
 */
public record SyncReport(
        boolean skipped,
        List<SupplierSync> suppliers,
        long totalStays,
        long totalRoomTypes) {

    public SyncReport {
        suppliers = List.copyOf(suppliers);
    }

    /** 이미 실행 중 — 아무것도 하지 않음. */
    public static SyncReport alreadyRunning() {
        return new SyncReport(true, List.of(), 0, 0);
    }

    /**
     * @param failureType 실패했을 때만 값이 있다
     */
    public record SupplierSync(
            SupplierId supplier,
            String status,
            int stays,
            int roomTypes,
            FailureType failureType,
            String detail) {

        public static SupplierSync ok(SupplierId supplier, int stays, int roomTypes) {
            return new SupplierSync(supplier, "OK", stays, roomTypes, null, null);
        }

        public static SupplierSync failed(SupplierId supplier, FailureType type, String detail) {
            return new SupplierSync(supplier, "FAILED", 0, 0, type, detail);
        }
    }
}
