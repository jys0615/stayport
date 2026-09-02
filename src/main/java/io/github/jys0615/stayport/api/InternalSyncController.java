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

/**
 * 운영용 엔드포인트.
 *
 * <p>주기 스케줄러는 두지 않았다. 숙소 목록이 정적인데 주기를 정할 근거가 없고, 근거 없이 박아둔
 * 주기는 나중에 아무도 못 바꾼다. 대신 필요할 때 부를 수 있는 문을 하나 열어 뒀다 —
 * 기동 시 동기화가 실패했을 때의 복구 경로이기도 하다.
 */
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

    /** 저장된 매핑을 그대로 보여준다. 매핑이 맞았는지 눈으로 확인할 창구가 하나는 필요하다. */
    @GetMapping("/mappings")
    List<MappedRoomType> mappings() {
        return mappingStore.findAll();
    }
}
