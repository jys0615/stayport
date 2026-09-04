/**
 * 이기종 숙박 공급사 API를 표준 모델로 통합하는 검색 백엔드.
 *
 * <p>경계: {@code api}(인바운드) / {@code domain}(공급사를 모름) / {@code application}(유스케이스·포트)
 * / {@code adapter}(공급사 DTO는 여기서 끝) / {@code infra} / {@code mock}(mock 프로파일 전용).
 * 아키텍처 근거는 docs/design.md §3.
 */
package io.github.jys0615.stayport;
