package io.github.jys0615.stayport.application;

import io.github.jys0615.stayport.infra.StayportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 기동 직후 동기화 1회. 실패해도 예외를 던지지 않는다 — 앱은 뜨고, 복구는
 * POST /internal/sync (design.md §4).
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
