package io.github.jys0615.stayport.api;

import io.github.jys0615.stayport.application.MappingSyncService;
import io.github.jys0615.stayport.application.SyncReport;
import io.github.jys0615.stayport.application.port.MappedRoomType;
import io.github.jys0615.stayport.application.port.MappingStore;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영용 엔드포인트 — 수동 재동기화(기동 시 실패의 복구 경로)와 매핑 조회. */
@RestController
@RequestMapping("/internal")
class InternalSyncController {

    private final MappingSyncService syncService;
    private final MappingStore mappingStore;

    InternalSyncController(MappingSyncService syncService, MappingStore mappingStore) {
        this.syncService = syncService;
        this.mappingStore = mappingStore;
    }

    @PostMapping("/sync")
    SyncReport sync() {
        return syncService.sync();
    }

    /** 저장된 매핑 조회. */
    @GetMapping("/mappings")
    List<MappedRoomType> mappings() {
        return mappingStore.findAll();
    }
}
