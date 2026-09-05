# stayport

서로 다른 스펙의 외부 숙박 공급사(Supplier A·B) API를 하나의 표준 상품 모델로 통합하고,
날짜·인원 기반 통합 검색을 제공하는 백엔드다. 공급사마다 요금 표현(날짜별 net+세금 vs 기간
총액 gross)과 실패 통보 방식(HTTP 상태 vs 본문 코드)이 다른데, 그 차이를 어댑터 계층에서
흡수해서 클라이언트는 어느 공급사에서 온 상품인지와 무관하게 같은 형태의 결과를 받는다.

```
[사전] 공급사 숙소 목록 조회 → 내부 식별자 매핑 저장 (기동 시 1회 + 수동)
[검색] GET /api/v1/stays/search?checkIn&checkOut&adults&children
   → 매핑에서 보유 숙소를 공급사별 코드로 수집 (50개씩 분할)
   → 공급사 병렬 조회 → 표준 모델로 정규화 → 내부 식별자로 해석
   → 병합: 한쪽이 실패해도 나머지로 응답 + suppliers[]에 실패 사실 노출
```

- Java 21 · Spring Boot 4.1 · MVC + WebClient · H2/JPA · Gradle(Kotlin DSL)
- 외부 공급사는 실제로 존재하지 않으므로 스펙대로 만든 흉내 서버(9090)를 같이 띄운다

## 실행

JDK 21이 필요하다. 그 외 설치할 것은 없다 (DB는 내장 H2).

```bash
# 터미널 1 — 공급사 흉내 서버 (9090)
./gradlew bootRun --args='--spring.profiles.active=mock'

# 터미널 2 — 본 앱 (8080). 기동하며 매핑 동기화가 한 번 돈다
./gradlew bootRun
```

검색:

```bash
curl "http://localhost:8080/api/v1/stays/search?checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0"
```

정상이면 상품 3건이 나온다. 그중 둘(내부 stayId 1·2)은 실제로는 같은 호텔인데 공급사가 달라
별개 상품이다 — B는 452,000원에 조식 포함, A는 429,000원에 조식 없음. 하나로 합치지 않은
판단은 docs/design.md §8 참고.

테스트는 흉내 서버를 테스트 JVM 안에 직접 띄우므로 아무것도 미리 실행할 필요가 없다:

```bash
./gradlew test
```

## 장애를 직접 내보기

이 시스템의 핵심은 정상 경로가 아니라 실패 경로다. 흉내 서버에 고장 스위치가 있으니
직접 꺼뜨려 보면서 확인하는 것이 가장 빠르다.

**① A 장애 (HTTP 503) — B의 결과만으로 응답한다**

```bash
curl -X POST 'http://localhost:9090/control/a/mode?value=error'
curl "http://localhost:8080/api/v1/stays/search?checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0"
# stays에는 B 상품 1건만, suppliers에는
#   A: FAILED, failures={SUPPLIER_ERROR:1} / B: OK
```

**② B 장애 — HTTP 200인데 실패다**

```bash
curl -X POST 'http://localhost:9090/control/a/mode?value=normal'
curl -X POST 'http://localhost:9090/control/b/mode?value=error'
```

B는 장애 상황에서도 HTTP 200에 `resultCode: E503`을 준다. 본문 코드를 확인하지 않으면 이
장애가 "검색 결과 0건"으로 조용히 처리된다. 위 검색을 다시 부르면 A 상품 2건과 함께
B가 FAILED로 표시되는 것을 볼 수 있다.

**③ 무응답 — 3초에 끊고 나머지로 응답한다**

```bash
curl -X POST 'http://localhost:9090/control/b/mode?value=normal'
curl -X POST 'http://localhost:9090/control/a/mode?value=no-response'
time curl "http://localhost:8080/api/v1/stays/search?checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0"
# 흉내 서버는 30초를 버티지만 응답은 3초 언저리에 온다. A는 FAILED(TIMEOUT), B는 OK
```

**④ 빈 응답 본문 — 성공으로 처리하면 안 되는 경우**

```bash
curl -X POST 'http://localhost:9090/control/a/mode?value=empty-body'
# 다시 검색하면 A는 FAILED(PARSE_ERROR). 상태 200에 빈 본문이 오는 프로토콜 위반을
# "재고 0건"으로 오해하지 않는지 확인하는 스위치다
```

**⑤ 복구**

```bash
curl -X POST 'http://localhost:9090/control/a/mode?value=normal'
```

**⑥ 매핑이 없는 상태와 공급사 장애의 구분**

흉내 서버를 장애로 둔 채 본 앱을 처음 기동하면(또는 `data/`를 지우고 재기동) 매핑이 빈 채로
뜬다 — 동기화 실패로 앱이 죽지는 않는다. 이때 검색하면 공급사 상태가 `FAILED`가 아니라
`NO_MAPPING`으로 나온다. 공급사가 죽은 것과 우리가 아직 물어볼 목록을 못 만든 것은 원인도,
클라이언트가 할 일도 다르기 때문에 구분한다. 복구는:

```bash
curl -X POST http://localhost:8080/internal/sync
curl http://localhost:8080/internal/mappings   # 저장된 매핑 확인
```

위 시나리오들은 전부 자동 테스트로도 고정되어 있다 — `StaySearchTest`,
`SearchFailureIsolationTest`, `SupplierOffersTest`, `ChunkSplitTest` (총 36개).

## 설계에서 정한 것들

상세한 논증은 [docs/design.md](docs/design.md)에 있다. 여기서는 결정과 이유의 요지만.

**요금 모델 — 기간 총액(gross) 하나를 공통으로, 날짜별 분해는 optional**
두 공급사가 주는 정보량이 다르다. A는 날짜별 net+세금, B는 기간 총액뿐. 양쪽에서 산출
가능한 값이 "기간 전체·세금 포함 총액"뿐이라 그것을 공통 필드로 삼았다. A만 주는 날짜별
분해는 버리는 대신 `dailyBreakdown` 선택 필드로 보존했다 — 요금 분해는 한 번 버리면 역산이
안 된다. 세금을 최상위 필드로 두는 안은 기각: B에서 영원히 null이 되는 필드는 세금을
표시하는 클라이언트를 조용히 틀리게 만든다.

**예약 불가 상품 — 응답에 남기고 availableRooms=0**
연박 재고는 날짜별 잔여의 최솟값으로 판정한다(하루라도 0이면 예약 불가). 재고 0을 응답에서
빼면 클라이언트가 "그런 객실이 없다"와 "만실이다"를 구분할 수 없어서, 남기고 `bookable`
플래그를 같이 준다.

**타임아웃 — 연결 1s / 공급사 호출 3s / 검색 전체 3.5s**
공급사 호출은 병렬이므로 전체 예산은 max(3s)에 병합·직렬화 여유 0.5s를 더한 값이다. MVC라
요청 하나가 응답까지 서블릿 스레드를 점유하므로, 이 값은 스레드 점유 시간의 상한이기도 하다.
호출 제한은 Reactor `.timeout()` 하나로만 건다 — read timeout과 겹치면 어느 쪽이 발동했는지
로그로 구분할 수 없다. 값은 전부 `application.yml`에 있다.

**MVC + WebClient**
검색 한 건이 부르는 외부 API가 공급사당 한두 번이라 전면 리액티브는 복잡도 대비 이득이 없고,
순차 호출은 지연이 그대로 더해진다. 외부 I/O 구간만 논블로킹으로 병렬화하고 컨트롤러 경계에서
동기로 돌려준다. 가상 스레드도 검토했지만 병렬 구조가 코드에 드러나는 쪽을 골랐다.

**저장 대상은 매핑뿐**
숙소 목록은 거의 안 바뀌고 재고·요금은 매 순간 바뀐다. 정적인 것만 저장하고 휘발성은 매 요청
실시간 조회한다. 동기화는 기동 시 1회 + `POST /internal/sync` 수동 — 목록이 정적인데 주기를
정할 근거가 없어서 주기 스케줄러는 두지 않았다. 같은 공급사 상품이 항상 같은 내부 식별자를
갖는 것은 DB UNIQUE 제약으로 보장한다. 객실 타입 코드는 숙소 안에서만 유일하므로 객실 매핑
키에는 숙소 코드가 함께 들어간다.

**50개 제한과 수천 숙소 스케일**
공급사가 한 번에 받는 숙소 코드는 50개라 어댑터가 목록을 나눠 병렬로 부르고, 일부 묶음만
실패하면 받은 것은 버리지 않고 `PARTIAL`로 표시한다. 숙소가 수천 개가 되면 실시간 전량
조회 자체가 예산을 넘으므로 사전 집계 캐시로 전환해야 한다 — 로드맵은 design.md §5.

**HTTP 계약 — 요청 검증 실패만 400, 나머지는 200**
두 공급사가 모두 죽어도 `200 + suppliers:[FAILED, FAILED]`다. 이 API의 실패는 공급사
단위로 부분적이라, 응답 전체를 5xx로 만들면 "A는 됐고 B만 죽었다"를 표현할 자리가 없다.
재시도 판단은 상태 코드가 아니라 `suppliers[].status`로 내린다.

**신규 공급사 추가 절차**
`SupplierAdapter` 구현체 하나(응답 DTO·변환·실패 판정 포함)를 새 패키지에 만들고
`application.yml`의 `stayport.suppliers`에 항목을 추가하면 끝이다. 도메인·검색 유스케이스·
API 계층은 바뀌지 않는다. 공급사 DTO가 어댑터 패키지 밖으로 나가지 않는 것이 이 주장의
전제이고, 경계 규칙 5개가 ArchUnit 테스트(`ArchitectureTest`)로 빌드에서 강제된다.

## 문서

| 문서 | 내용 |
|---|---|
| [docs/design.md](docs/design.md) | 설계 결정과 근거, 버린 대안, 선택 항목 판단, 운영 전환 로드맵 |
| [docs/api.md](docs/api.md) | 검색 API 명세 — 상태 코드 계약, 필드 해석 규칙, 운영 엔드포인트 |
| [JOURNAL.md](JOURNAL.md) | 일자별 작업 기록과 시행착오 |

## 구조

```
io.github.jys0615.stayport
├─ api/           검색 API, 응답 DTO (인바운드)
├─ domain/        표준 상품 모델, 매핑 엔티티 — 공급사를 모른다
├─ application/   검색·동기화 유스케이스, 아웃바운드 포트
├─ adapter/
│  ├─ suppliera/  A 전용 DTO·변환·실패 판정 (HTTP 상태)
│  ├─ supplierb/  B 전용 DTO·변환·실패 판정 (200 + resultCode)
│  └─ persistence/ 매핑 저장소 JPA 구현
├─ infra/         WebClient 구성, 설정 프로퍼티
└─ mock/          공급사 흉내 서버 (mock 프로파일 전용 — 운영 코드가 아니다)
```
