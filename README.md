# stayport

![Java 21](https://img.shields.io/badge/Java-21-437291?logo=openjdk&logoColor=white)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![WebClient](https://img.shields.io/badge/Spring-WebClient-6DB33F?logo=spring&logoColor=white)
![JPA](https://img.shields.io/badge/JPA-H2-59666C?logo=hibernate&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-Kotlin%20DSL-02303A?logo=gradle&logoColor=white)
![Build](https://github.com/jys0615/stayport/actions/workflows/test.yml/badge.svg)
![Tests](https://img.shields.io/badge/tests-62_passing-brightgreen)
![ArchUnit](https://img.shields.io/badge/ArchUnit-5_rules-eb6c36)
![OpenAPI](https://img.shields.io/badge/OpenAPI-SpringDoc-85EA2D?logo=swagger&logoColor=black)

서로 다른 스펙의 외부 숙박 공급사(Supplier A·B) API를 하나의 표준 상품 모델로 통합하고,
날짜·인원 기반 통합 검색을 제공하는 백엔드입니다.

공급사마다 요금 표현(날짜별 net+세금 vs 기간 총액 gross)도 실패 통보 방식(HTTP 상태 vs 본문
코드)도 제각각인데, 그 차이는 어댑터 계층에서 전부 흡수합니다. 클라이언트는 어느 공급사에서 온
상품인지 몰라도 같은 형태의 결과를 받습니다.

```
[사전] 공급사 숙소 목록 조회 → 내부 식별자 매핑 저장 (기동 시 1회 + 수동)
[검색] GET /api/v1/stays/search?checkIn&checkOut&adults&children
   → 매핑에서 보유 숙소를 공급사별 코드로 수집 (50개씩 분할)
   → 공급사 병렬 조회 → 표준 모델로 정규화 → 내부 식별자로 해석
   → 병합: 한쪽이 실패해도 나머지로 응답 + suppliers[]에 실패 사실 노출
```

| 🧪 테스트 | ⏱ 응답 예산 | 🧨 장애 재현 | 📈 부하 실측 |
|:---:|:---:|:---:|:---:|
| 62개 전부 green | 어떤 장애에도 **3.5초 안에 200** | 고장 스위치 [7종](#-장애를-직접-내보기) | [3,484 → 9.9 req/s](docs/load-test.md)<br>스레드 천장 증명 |

## 🛠 기술 스택

| 스택 | 이렇게 골랐습니다 |
|---|---|
| **Java 21** | record·sealed interface로 "실패도 값이다"를 타입으로 강제합니다 (`SupplierResult`) |
| **Spring Boot 4.1 · MVC** | 전면 리액티브 대신 외부 I/O 구간만 논블로킹으로 뒀습니다. 병렬 구조가 코드에 드러나는 쪽을 택했습니다 |
| **Spring WebClient** | 공급사 병렬 호출과 타임아웃·부분 실패 제어의 중심입니다 |
| **JPA · H2** | 매핑·격리 도메인 모델을 관계형으로 다룹니다. 같은 상품=같은 식별자는 UNIQUE 제약으로 보장합니다 |
| **Resilience4j** | 반복 실패하는 공급사를 잠시 끊습니다. 스타터 없이 브레이커 모듈만 씁니다 |
| **SpringDoc** | 실행 중인 서버의 스키마를 `/swagger-ui.html`로 노출합니다 |
| **ArchUnit** | 패키지 경계 5규칙을 빌드 실패로 지킵니다(문서 약속으로 두지 않았습니다) |
| **Gradle (Kotlin DSL)** | — |

외부 공급사는 실제로 존재하지 않으므로 스펙대로 만든 흉내 서버(9090)를 같이 띄웁니다.

## ⚡ 실행

JDK 21이 필요합니다. 그 외 설치할 것은 없습니다 (DB는 내장 H2).

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

API를 눌러 보려면 `http://localhost:8080/swagger-ui.html`을 열면 됩니다.

정상이면 상품 3건이 나옵니다. 그중 둘(내부 stayId 1·2)은 실제로는 같은 호텔인데 공급사가 달라
별개 상품으로 잡히고 B는 452,000원에 조식 포함, A는 429,000원에 조식 없음으로 나옵니다. 하나로
합치지 않은 판단은 docs/duplicate-matching.md에 있습니다.

테스트는 흉내 서버를 테스트 JVM 안에 직접 띄우므로 아무것도 미리 실행할 필요가 없습니다:

```bash
./gradlew test
```

## 🧨 장애를 직접 내보기

이 시스템에서 봐야 할 곳은 정상 경로가 아닙니다. 실패 경로가 핵심입니다. 한 공급사가 실패하거나
늦어도 이미 받은 다른 공급사의 결과는 버리지 않습니다. 공급사별 응답 제한은 3초, 전체 검색은
최대 3.5초 안에 200으로 마무리됩니다:

![장애 격리 타임라인 — 두 공급사를 동시에 조회하고 3초 안에 응답하지 않는 공급사만 TIMEOUT으로 처리한 채 3.5초 안에 200으로 응답합니다](docs/images/failure-timeline.png)

흉내 서버에 고장 스위치가 있으니 직접 꺼뜨려 보면서 확인하는 것이 가장 빠릅니다.

**① A 장애 (HTTP 503) — B의 결과만으로 응답합니다**

```bash
curl -X POST 'http://localhost:9090/control/a/mode?value=error'
curl "http://localhost:8080/api/v1/stays/search?checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0"
# stays에는 B 상품 1건만, suppliers에는
#   A: FAILED, failures={SUPPLIER_ERROR:1} / B: OK
```

**② B 장애 — HTTP 200인데 실패입니다**

```bash
curl -X POST 'http://localhost:9090/control/a/mode?value=normal'
curl -X POST 'http://localhost:9090/control/b/mode?value=error'
```

B는 장애 상황에서도 HTTP 200에 `resultCode: E503`을 줍니다. 본문 코드를 확인하지 않으면 이
장애가 "검색 결과 0건"으로 조용히 처리됩니다. 위 검색을 다시 부르면 A 상품 2건과 함께 B가
B가 FAILED로 표시되는 것을 볼 수 있습니다.

**③ 무응답이면 3초에 끊고 나머지로 응답합니다**

```bash
curl -X POST 'http://localhost:9090/control/b/mode?value=normal'
curl -X POST 'http://localhost:9090/control/a/mode?value=no-response'
time curl "http://localhost:8080/api/v1/stays/search?checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0"
# 흉내 서버는 30초를 버티지만 응답은 3초 언저리에 온다. A는 FAILED(TIMEOUT), B는 OK
```

**④ 빈 응답 본문을 성공으로 처리하면 안 되는 경우**

```bash
curl -X POST 'http://localhost:9090/control/a/mode?value=empty-body'
# 다시 검색하면 A는 FAILED(PARSE_ERROR). 상태 200에 빈 본문이 오는 프로토콜 위반을
# "재고 0건"으로 오해하지 않는지 확인하는 스위치다
```

**⑤ 반복 실패하면 서킷이 열려 아예 부르지 않는다**

```bash
# A를 장애로 둔 채 여러 번 검색하면 최근 10건 중 절반이 실패한 시점에 서킷이 열린다
curl -X POST 'http://localhost:9090/control/a/mode?value=error'
for i in $(seq 1 6); do curl -s -o /dev/null "http://localhost:8080/api/v1/stays/search?checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0"; done
curl -s 'http://localhost:8080/actuator/metrics/stayport.supplier.circuit.open?tag=supplier:A'
# 열린 뒤로는 A의 실패 사유가 SUPPLIER_ERROR가 아니라 CIRCUIT_OPEN이다 —
# 불러서 실패한 것과 아예 부르지 않은 것은 다른 사실이라 구분한다
```

**⑥ 복구**

```bash
curl -X POST 'http://localhost:9090/control/a/mode?value=normal'
# 열린 서킷은 10초 뒤 시험 호출을 통과시키고 닫힌다
```

**⑦ 매핑이 없는 상태와 공급사 장애의 구분**

흉내 서버를 장애로 둔 채 본 앱을 처음 기동하면(또는 `data/`를 지우고 재기동) 매핑이 빈 채로
뜹니다. 동기화가 실패해도 앱이 죽지는 않습니다. 이때 검색하면 공급사 상태가 `FAILED`가 아니라
`NO_MAPPING`으로 나옵니다. 공급사가 죽은 것과 우리가 아직 물어볼 목록을 못 만든 것은 원인도
다르고 클라이언트가 할 일도 달라서 구분합니다. 복구는:

```bash
curl -X POST http://localhost:8080/internal/sync
curl http://localhost:8080/internal/mappings   # 저장된 매핑 확인
```

위 시나리오는 전부 자동 테스트로도 고정돼 있습니다(`StaySearchTest`,
`SearchFailureIsolationTest`, `SearchCircuitBreakerTest`, `SupplierOffersTest`, `ChunkSplitTest`).

## 🧭 설계에서 정한 것들

상세한 논증은 [docs/design.md](docs/design.md)에 있습니다. 여기서는 결정과 이유의 요지만
적습니다.

**요금 모델 — 기간 총액(gross) 하나를 공통으로, 날짜별 분해는 optional**
두 공급사가 주는 정보량이 다릅니다. A는 날짜별 net+세금, B는 기간 총액뿐입니다. 양쪽에서 산출
가능한 값이 "기간 전체·세금 포함 총액"뿐이라 그것을 공통 필드로 삼았습니다. A만 주는 날짜별
분해는 버리지 않고 `dailyBreakdown` 선택 필드로 보존했습니다. 요금 분해는 한 번 버리면 역산이
안 되기 때문입니다. 세금을 최상위 필드로 두는 안은 기각했습니다. B에서 영원히 null이 되는 필드는
세금을 표시하는 클라이언트를 조용히 틀리게 만듭니다.

**예약 불가 상품도 응답에 남기고 availableRooms=0**
연박 재고는 날짜별 잔여의 최솟값으로 판정합니다(하루라도 0이면 예약 불가). 재고 0을 응답에서
빼버리면 클라이언트 입장에서 "그런 객실이 없다"는 경우와 "만실이다"는 경우가 한 덩어리가 됩니다.
그래서 남기고 `bookable` 플래그를 같이 줍니다.

**타임아웃 — 연결 1s / 공급사 호출 3s / 검색 전체 3.5s**
공급사 호출은 병렬이므로 전체 예산은 max(3s)에 병합·직렬화 여유 0.5s를 더한 값입니다. MVC라
요청 하나가 응답까지 서블릿 스레드를 점유하므로, 이 값은 스레드 점유 시간의 상한이기도 합니다.
호출 제한은 Reactor `.timeout()` 하나로만 겁니다. read timeout과 겹치면 어느 쪽이 발동했는지
로그로 구분할 수 없기 때문입니다. 값은 전부 `application.yml`에 있습니다.

**MVC + WebClient**
검색 한 건이 부르는 외부 API가 공급사당 한두 번이라 전면 리액티브는 복잡도 대비 이득이 없고,
순차 호출은 지연이 그대로 더해집니다. 외부 I/O 구간만 논블로킹으로 병렬화하고 컨트롤러 경계에서
동기로 돌려줍니다. 가상 스레드도 검토했지만 병렬 구조가 코드에 드러나는 쪽을 골랐습니다.

**저장 대상은 매핑뿐**
숙소 목록은 거의 안 바뀌고 재고·요금은 매 순간 바뀝니다. 정적인 것만 저장하고 휘발성은 매 요청
실시간 조회합니다. 동기화는 기동 시 1회 + `POST /internal/sync` 수동입니다. 목록이 정적인데
주기를 정할 근거가 없어서 주기 스케줄러는 두지 않았습니다. 같은 공급사 상품이 항상 같은 내부
식별자를 갖는 것은 DB UNIQUE 제약으로 보장합니다. 객실 타입 코드는 숙소 안에서만 유일하므로
객실 매핑 키에는 숙소 코드가 함께 들어갑니다.

**50개 제한과 수천 숙소 스케일**
공급사가 한 번에 받는 숙소 코드는 50개라 어댑터가 목록을 나눠 병렬로 부르고, 일부 묶음만
실패하면 받은 것은 버리지 않고 `PARTIAL`로 표시합니다. 숙소가 수천 개가 되면 실시간 전량 조회
자체가 예산을 넘으므로 사전 집계 캐시로 전환해야 합니다(로드맵은 design.md §5).

**HTTP 계약은 요청 검증 실패만 400, 나머지는 전부 200입니다**
두 공급사가 모두 죽어도 `200 + suppliers:[FAILED, FAILED]`입니다. 이 API의 실패는 공급사
단위로 부분적이라, 응답 전체를 5xx로 만들면 "A는 됐고 B만 죽었다"를 표현할 자리가 없습니다.
재시도 판단은 상태 코드가 아니라 `suppliers[].status`로 내립니다.

**신규 공급사 추가 절차**
바뀌는 곳은 정확히 세 군데입니다: 공급사 식별자(`SupplierId`) 상수 한 줄, `SupplierAdapter`
구현체 하나(응답 DTO·변환·실패 판정 포함), `application.yml`의 `stayport.suppliers` 항목 하나.
검색 유스케이스·병합·API 계층의 로직은 바뀌지 않습니다. 검색은 등록된 어댑터 목록을 순회할 뿐
공급사 수를 모릅니다. 공급사 DTO가 어댑터 패키지 밖으로 나가지 않는 것이 이 주장의 전제이고,
경계 규칙 5개가 ArchUnit 테스트(`ArchitectureTest`)로 빌드에서 강제됩니다.

## 📚 문서

| 문서 | 내용 |
|---|---|
| [docs/design.md](docs/design.md) | 설계 결정과 근거, 버린 대안, 선택 항목 판단, 운영 전환 로드맵 |
| [docs/api.md](docs/api.md) | 검색 API 명세 — 상태 코드 계약, 필드 해석 규칙, 운영 엔드포인트 |
| [docs/load-test.md](docs/load-test.md) | 부하 측정 — 공급사 지연이 만드는 처리량 천장 (3,484 → 9.9 req/s), 재현 절차 |
| [docs/monitoring.md](docs/monitoring.md) | 연동 지표 — 노출되는 메트릭과 시나리오별 해석, 경보 설계 |
| [docs/duplicate-matching.md](docs/duplicate-matching.md) | 중복 숙소 — 합치지 않은 판단과 근거, 후보 탐지 방식 |
| [JOURNAL.md](JOURNAL.md) | 일자별 작업 기록과 시행착오 |

## 🏗 아키텍처

포트와 어댑터 구조입니다. 코어가 포트(주황 소켓)를 선언하고 어댑터가 바깥에서 꽂힙니다:

![포트·어댑터 구조 — 애플리케이션 코어가 공급사 조회·매핑 저장·격리 저장 포트를 정의하고 Supplier A·B 어댑터와 DB 저장 어댑터가 각 포트에 꽂혀 Supplier A, Supplier B, DB로 나갑니다](docs/images/architecture-core.png)

그림에서 먼저 볼 것은 포트의 위치입니다. 포트는 코어 쪽에 선언돼 있습니다. 어댑터 쪽에 두는
배치도 있지만 그러면 유스케이스가 공급사 사정을 알아야 합니다. 여기서는 유스케이스가 "무엇이
필요한가"만 선언하고 공급사 사정은 어댑터에서 끝납니다. A는 실패를 HTTP 상태로, B는 200 속 응답
코드로 말하지만 그 차이는 어댑터 밖으로 새지 않습니다.

공급사 호출은 병렬이고 각각 3초 제한이 걸립니다. 한쪽 실패는 그 공급사의 상태로만 남습니다.

DB에는 매핑과 격리 데이터만 있습니다. 가격·객실 정보는 저장하지 않고 매번 실시간으로 조회합니다.
이 경계들은 ArchUnit 규칙 5개로 빌드에서 강제됩니다(`ArchitectureTest`).

패키지 배치:

```
io.github.jys0615.stayport
├─ api/           검색 API, 응답 DTO, 단일 오류 스키마 (인바운드)
├─ domain/        표준 상품 모델, 매핑·격리 엔티티 — 공급사를 모른다
├─ application/   검색·동기화 유스케이스, 아웃바운드 포트
├─ adapter/
│  ├─ suppliera/  A 전용 DTO·변환·실패 판정 (HTTP 상태)
│  ├─ supplierb/  B 전용 DTO·변환·실패 판정 (200 + resultCode)
│  └─ persistence/ 매핑·격리 저장소 JPA 구현
├─ infra/         WebClient 구성, 설정 프로퍼티
└─ mock/          공급사 흉내 서버 (mock 프로파일 전용 — 운영 코드가 아니다)
```

저장하는 것은 매핑뿐입니다. 테이블 세 개에 요금과 재고 컬럼이 하나도 없는 것이 이 스키마의
요지입니다.

![매핑 스키마 — stay_mapping은 (공급사, 숙소 코드)로, room_type_mapping은 (공급사, 숙소 코드, 객실 코드)로 유일합니다. quarantined_offer는 변환하지 못한 응답을 원문째 보관합니다](docs/images/db-schema.png)

객실 코드는 숙소 안에서만 유일합니다. `A-10023`과 `A-10077`이 둘 다 `DLX-TWN`을 쓰기 때문에,
객실 매핑의 유일 키에는 숙소 코드가 함께 들어가야 서로 다른 내부 식별자를 받습니다. 같은 공급사
상품이 다시 조회될 때 항상 같은 내부 식별자로 돌아오는 것도 이 제약이 보장합니다.
