package io.github.jys0615.stayport.application.port;

import io.github.jys0615.stayport.domain.SearchQuery;
import io.github.jys0615.stayport.domain.SupplierId;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 공급사 연동 포트. 새 공급사 추가 = 이 인터페이스 구현체 + 설정 등록 (도메인·API 무변경).
 *
 * <p>계약: 두 메서드 모두 예외를 던지지 않는다. 실패는 결과 타입의 Failure로 돌려준다.
 */
public interface SupplierAdapter {

    SupplierId supplier();

    /** 숙소·객실 타입 전체 목록 — 매핑 동기화용. */
    Mono<CatalogResult> fetchCatalog();

    /**
     * 재고·요금 조회. 한도 초과 코드 목록은 어댑터가 나눠 부른다.
     */
    Mono<SupplierResult> fetchOffers(SearchQuery query, List<String> stayCodes);
}
