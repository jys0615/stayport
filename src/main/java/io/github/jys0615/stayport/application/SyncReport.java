package io.github.jys0615.stayport.application;

import io.github.jys0615.stayport.application.port.FailureType;
import io.github.jys0615.stayport.domain.SupplierId;
import java.util.List;

/**
 * 동기화 한 번의 결과.
 *
 * <p>공급사별로 성패를 따로 담는다. 한쪽이 실패했다는 사실이 전체 실패로 뭉개지면 어디를
 * 다시 불러야 하는지 알 수 없다.
 *
 * @param skipped     이미 실행 중이어서 이번 요청은 아무것도 하지 않았다는 뜻
 * @param suppliers   공급사별 결과
 * @param totalStays  동기화 후 저장된 전체 숙소 매핑 수
 * @param totalRoomTypes 동기화 후 저장된 전체 객실 타입 매핑 수
 */
public record SyncReport(
        boolean skipped,
        List<SupplierSync> suppliers,
        long totalStays,
        long totalRoomTypes) {

    public SyncReport {
        suppliers = List.copyOf(suppliers);
    }

    /** 이미 동기화가 돌고 있어서 아무것도 하지 않았다. */
    public static SyncReport alreadyRunning() {
        return new SyncReport(true, List.of(), 0, 0);
    }

    /**
     * @param status      OK 또는 FAILED
     * @param stays       이번에 확인한 숙소 수
     * @param roomTypes   이번에 확인한 객실 타입 수
     * @param failureType 실패했을 때만 값이 있다
     * @param detail      실패 사유 요약. 공급사 원문 메시지를 그대로 흘리지 않는다
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
