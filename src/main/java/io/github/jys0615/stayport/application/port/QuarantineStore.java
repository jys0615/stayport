package io.github.jys0615.stayport.application.port;

import io.github.jys0615.stayport.domain.SupplierId;
import io.github.jys0615.stayport.domain.quarantine.QuarantinedOffer;
import java.util.List;

/**
 * 격리 저장소 포트. 기록 실패가 본 흐름을 깨면 안 되므로 구현은 예외를 삼키고 로그만 남긴다.
 */
public interface QuarantineStore {

    void keep(SupplierId supplier, String reason, String payload);

    List<QuarantinedOffer> findAll();
}
