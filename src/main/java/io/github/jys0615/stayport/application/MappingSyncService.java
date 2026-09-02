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
 * 공급사 숙소 목록을 읽어 내부 식별자 매핑을 맞춘다.
 *
 * <p>이 애플리케이션이 영속화하는 유일한 대상이다. 재고와 요금은 저장하지 않는다 — 숙소 목록은
 * 거의 안 바뀌고 재고·요금은 매 순간 바뀐다. 휘발성 데이터를 저장하면 그 순간부터 "언제
 * 무효화하나"를 떠안는데, 재고 오차는 이 도메인에서 비싼 버그다.
 *
 * <p><b>진입점은 여기 하나다.</b> 기동 시 훅과 수동 재동기화 엔드포인트가 같은 메서드를 부르고,
 * 이미 실행 중이면 두 번째 호출은 즉시 돌아간다. 쓰는 주체를 하나로 만들면 매핑 삽입에
 * 락이 필요할 이유가 없어진다.
 */
@Service
public class MappingSyncService {

    private static final Logger log = LoggerFactory.getLogger(MappingSyncService.class);

    /**
     * 전체 동기화 대기 상한. 검색 API의 예산(3.5s)과 무관하다 — 동기화는 요청 경로가 아니고,
     * 여기서 서둘러 끊으면 매핑이 반쯤 맞은 상태로 남는다.
     */
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
                    // 한 공급사가 실패해도 나머지는 계속 맞춘다. 기존 매핑은 그대로 살아 있다.
                    log.error("supplier {} 숙소 목록 동기화 실패: {} ({}). 기존 매핑으로 계속 서비스한다. "
                                    + "복구는 POST /internal/sync",
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
