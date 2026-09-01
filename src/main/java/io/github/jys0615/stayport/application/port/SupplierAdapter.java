package io.github.jys0615.stayport.application.port;

import io.github.jys0615.stayport.domain.SearchQuery;
import io.github.jys0615.stayport.domain.SupplierId;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 공급사 연동의 유일한 통로.
 *
 * <p>새 공급사를 붙일 때 손대는 곳은 이 인터페이스의 구현체와 설정 등록뿐이다.
 * 도메인과 API 계층은 바뀌지 않는다.
 *
 * <p>두 메서드 모두 예외를 던지지 않고 결과 타입으로 실패를 돌려준다. 호출부가
 * try-catch 대신 값으로 부분 실패를 다루게 하기 위해서다.
 */
public interface SupplierAdapter {

    SupplierId supplier();

    /** 숙소·객실 타입 전체 목록. 매핑 동기화에서만 쓴다. */
    Mono<CatalogResult> fetchCatalog();

    /**
     * 재고·요금 조회.
     *
     * @param query      검색 조건
     * @param stayCodes  조회할 공급사 숙소 코드. 공급사 1회 호출 한도(50개) 이하로 잘라서 넘긴다
     */
    Mono<SupplierResult> fetchOffers(SearchQuery query, List<String> stayCodes);
}
