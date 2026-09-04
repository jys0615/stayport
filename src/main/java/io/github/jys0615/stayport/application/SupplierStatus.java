package io.github.jys0615.stayport.application;

/** 검색 응답의 공급사별 상태. */
public enum SupplierStatus {

    OK,

    /** 나눠 부른 것 중 일부만 받았다. 받은 상품은 결과에 포함. */
    PARTIAL,

    FAILED,

    /**
     * 물어볼 매핑이 없어 부르지 않았다. FAILED(공급사 문제)와 달리 우리 동기화 문제이고,
     * 클라이언트 재시도가 무의미하다 (design.md §7).
     */
    NO_MAPPING
}
