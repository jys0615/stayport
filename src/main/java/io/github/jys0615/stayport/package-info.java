/**
 * 서로 다른 스펙의 숙박 공급사 API를 하나의 표준 모델로 통합하고,
 * 날짜·인원 기반 통합 검색을 제공하는 애플리케이션.
 *
 * <p>패키지 경계는 아래와 같이 나뉘며, 이 경계는 ArchUnit 테스트로 강제한다.
 *
 * <ul>
 *   <li>{@code api} — 인바운드 어댑터. HTTP 요청을 받아 유스케이스를 호출한다.
 *   <li>{@code domain} — 표준 숙박 상품 모델과 매핑. 공급사의 존재를 모른다.
 *   <li>{@code application} — 유스케이스와 아웃바운드 포트 정의.
 *   <li>{@code adapter} — 아웃바운드 어댑터. 공급사별 DTO와 변환이 여기서 끝난다.
 *   <li>{@code infra} — WebClient·설정 프로퍼티 등 기술 관심사.
 *   <li>{@code mock} — 공급사 흉내 서버. 채점 대상이 아니며 {@code mock} 프로파일에서만 뜬다.
 * </ul>
 */
package io.github.jys0615.stayport;
