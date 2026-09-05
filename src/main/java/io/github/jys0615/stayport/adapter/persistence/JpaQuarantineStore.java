package io.github.jys0615.stayport.adapter.persistence;

import io.github.jys0615.stayport.application.port.QuarantineStore;
import io.github.jys0615.stayport.domain.SupplierId;
import io.github.jys0615.stayport.domain.quarantine.QuarantinedOffer;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class JpaQuarantineStore implements QuarantineStore {

    private static final Logger log = LoggerFactory.getLogger(JpaQuarantineStore.class);

    private final QuarantinedOfferRepository repository;

    JpaQuarantineStore(QuarantinedOfferRepository repository) {
        this.repository = repository;
    }

    @Override
    public void keep(SupplierId supplier, String reason, String payload) {
        try {
            repository.save(new QuarantinedOffer(supplier, reason, payload));
        } catch (RuntimeException e) {
            // 격리는 분석용 부가 기록이다. 이것 때문에 검색이 죽으면 주객전도.
            log.warn("격리 저장 실패 (supplier {}, reason {})", supplier, reason, e);
        }
    }

    @Override
    public List<QuarantinedOffer> findAll() {
        return repository.findAll();
    }
}
