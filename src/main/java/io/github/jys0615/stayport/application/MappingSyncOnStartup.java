package io.github.jys0615.stayport.application;

import io.github.jys0615.stayport.infra.StayportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 기동 직후 매핑을 한 번 맞춘다.
 *
 * <p><b>실패해도 예외를 밖으로 던지지 않는다.</b> 공급사 한 곳의 장애로 서비스 전체가 기동조차
 * 못 하는 건 과한 결합이고, 기존 매핑이 이미 있으면 그것으로 계속 서비스할 수 있다.
 *
 * <p>첫 실행과 공급사 장애가 겹치면 매핑이 빈 상태로 남는다. 그때 검색은 빈 결과를 주는 대신
 * 해당 공급사를 실패로 표시한다 — "물어볼 게 없어서 없다"와 "물어봤는데 없다"는 다른 사실이다.
 */
@Component
class MappingSyncOnStartup {

    private static final Logger log = LoggerFactory.getLogger(MappingSyncOnStartup.class);

    private final MappingSyncService syncService;
    private final boolean enabled;

    MappingSyncOnStartup(MappingSyncService syncService, StayportProperties properties) {
        this.syncService = syncService;
        this.enabled = properties.sync().onStartup();
    }

    @EventListener(ApplicationReadyEvent.class)
    void syncOnStartup() {
        if (!enabled) {
            log.info("기동 시 동기화가 꺼져 있다 (stayport.sync.on-startup=false)");
            return;
        }
        try {
            syncService.sync();
        } catch (RuntimeException e) {
            log.error("기동 시 동기화가 실패했다. 앱은 계속 뜬다. 복구는 POST /internal/sync", e);
        }
    }
}
