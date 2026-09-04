package io.github.jys0615.stayport.application;

import io.github.jys0615.stayport.application.port.CatalogResult;
import io.github.jys0615.stayport.application.port.MappingStore;
import io.github.jys0615.stayport.application.port.SupplierAdapter;
import io.github.jys0615.stayport.application.port.SupplierRoomType;
import io.github.jys0615.stayport.application.port.SupplierStay;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 공급사 숙소 목록으로 내부 식별자 매핑을 갱신한다. 재고·요금은 저장하지 않는다(design.md §4).
 *
 * <p>동시 실행은 허용하지 않는다 — 이미 실행 중이면 즉시 skipped로 반환. 기동 훅과
 * POST /internal/sync가 같은 진입점을 쓴다. 단일 인스턴스 전제.
 */
@Service
public class MappingSyncService {

    private static final Logger log = LoggerFactory.getLogger(MappingSyncService.class);

    /** 동기화 전체 대기 상한. 요청 경로가 아니므로 검색 예산과 무관. */
    private static final Duration SYNC_TIMEOUT = Duration.ofSeconds(30);

    private final List<SupplierAdapter> adapters;
    private final MappingStore store;
    private final AtomicBoolean running = new AtomicBoolean(false);

    MappingSyncService(List<SupplierAdapter> adapters, MappingStore store) {
        this.adapters = List.copyOf(adapters);
        this.store = store;
    }

    public SyncReport sync() {
        if (!running.compareAndSet(false, true)) {
            log.info("동기화가 이미 실행 중이다. 이번 호출은 건너뛴다.");
            return SyncReport.alreadyRunning();
        }
        try {
            return runSync();
        } finally {
            running.set(false);
        }
    }

    private SyncReport runSync() {
        List<CatalogResult> results = Flux.fromIterable(adapters)
                .flatMap(SupplierAdapter::fetchCatalog)
                .collectList()
                .block(SYNC_TIMEOUT);

        if (results == null) {
            log.error("동기화가 {}초 안에 끝나지 않았다", SYNC_TIMEOUT.toSeconds());
            results = List.of();
        }

        List<SyncReport.SupplierSync> outcomes = new ArrayList<>();
        for (CatalogResult result : results) {
            outcomes.add(switch (result) {
                case CatalogResult.Success success -> apply(success);
                case CatalogResult.Failure failure -> {
                    log.error("supplier {} 숙소 목록 동기화 실패: {} ({}). 기존 매핑으로 계속 서비스. "
                                    + "복구: POST /internal/sync",
                            failure.supplier(), failure.type(), failure.detail());
                    yield SyncReport.SupplierSync.failed(failure.supplier(), failure.type(), failure.detail());
                }
            });
        }

        SyncReport report = new SyncReport(false, outcomes, store.countStays(), store.countRoomTypes());
        log.info("동기화 완료: 숙소 매핑 {}건, 객실 타입 매핑 {}건", report.totalStays(), report.totalRoomTypes());
        return report;
    }

    private SyncReport.SupplierSync apply(CatalogResult.Success success) {
        int roomTypeCount = 0;
        for (SupplierStay stay : success.stays()) {
            long internalStayId = store.upsertStay(success.supplier(), stay.code(), stay.name());
            for (SupplierRoomType roomType : stay.roomTypes()) {
                store.upsertRoomType(
                        success.supplier(),
                        stay.code(),
                        roomType.code(),
                        internalStayId,
                        roomType.name(),
                        roomType.maxOccupancy());
                roomTypeCount++;
            }
        }
        log.info("supplier {} 동기화: 숙소 {}건, 객실 타입 {}건",
                success.supplier(), success.stays().size(), roomTypeCount);
        return SyncReport.SupplierSync.ok(success.supplier(), success.stays().size(), roomTypeCount);
    }
}
