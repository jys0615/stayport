package io.github.jys0615.stayport.api;

import io.github.jys0615.stayport.application.DuplicateCandidateService;
import io.github.jys0615.stayport.application.DuplicateCandidateService.DuplicateCandidate;
import io.github.jys0615.stayport.application.MappingSyncService;
import io.github.jys0615.stayport.application.SyncReport;
import io.github.jys0615.stayport.application.port.MappedRoomType;
import io.github.jys0615.stayport.application.port.MappingStore;
import io.github.jys0615.stayport.application.port.QuarantineStore;
import io.github.jys0615.stayport.domain.quarantine.QuarantinedOffer;
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
    private final QuarantineStore quarantineStore;
    private final DuplicateCandidateService duplicateCandidates;

    InternalSyncController(MappingSyncService syncService, MappingStore mappingStore,
            QuarantineStore quarantineStore, DuplicateCandidateService duplicateCandidates) {
        this.syncService = syncService;
        this.mappingStore = mappingStore;
        this.quarantineStore = quarantineStore;
        this.duplicateCandidates = duplicateCandidates;
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

    /** 정규화에서 버려져 격리된 상품들 — 추후 분석용. */
    @GetMapping("/quarantine")
    List<QuarantinedOffer> quarantine() {
        return quarantineStore.findAll();
    }

    /** 서로 다른 공급사가 같은 숙소를 파는 것으로 보이는 쌍 — 병합하지 않고 후보만 보여준다. */
    @GetMapping("/duplicates")
    List<DuplicateCandidate> duplicates() {
        return duplicateCandidates.findCandidates();
    }
}
