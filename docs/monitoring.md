# 연동 모니터링 — 무엇을 보고, 무엇을 알아채는가

부하 측정(load-test.md)의 결론은 "공급사 하나가 느려지면 죽는 건 우리 쪽"이었습니다.
그 상황을 운영에서 알아채는 데 필요한 지표를 설계에 그치지 않고 실제로 노출합니다.
전부 `GET /actuator/metrics/{이름}`으로 확인할 수 있습니다.

## 노출되는 지표

**연동 지표 (직접 구현 — `SearchMetrics`)**

| 지표 | 종류 | 태그 | 뜻 |
|---|---|---|---|
| `stayport.supplier.call` | timer | `supplier`, `outcome`(ok·partial·failed) | 응답이 도착한 공급사 호출의 소요 시간 |
| `stayport.supplier.failure` | counter | `supplier`, `type`(FailureType) | 실패 유형별 발생 횟수 |
| `stayport.supplier.skipped` | counter | `supplier` | 형태 불량·매핑 없음으로 응답에서 뺀 상품 수 |
| `stayport.supplier.circuit.open` | gauge | `supplier` | 서킷이 열려 있는지 (1=열림, 부르지 않는 상태) |

타이머는 응답이 도착한 호출만 잽니다 — 검색 예산(3.5초)을 넘겨 버려진 공급사는 소요 시간이
정의되지 않으므로 `failure{type=TIMEOUT}` 카운터로만 남습니다. 성공률은 별도 지표가 아니라
`call{outcome}` 횟수의 비율로 계산합니다 — 같은 사실을 두 지표로 들고 있으면 어긋날 수 있습니다.

**스프링·톰캣 기본 지표 중 함께 보는 것**

| 지표 | 뜻 |
|---|---|
| `tomcat.threads.busy` / `tomcat.threads.config.max` | 서블릿 스레드 사용량 / 상한 (`server.tomcat.mbeanregistry.enabled=true`로 노출) |
| `http.server.requests` | 엔드포인트별 처리 시간 — 공급사와 무관한 URI의 지연 상승을 볼 때 |

## 시나리오별 해석

**① 공급사가 느려진다** — `stayport.supplier.call{supplier=A}`의 MAX가 호출 제한(3초)에
가까워지면 처리량 천장이 내려가는 중입니다. 부하 측정에서 확인한 관계식이 그대로 적용됩니다:
최악 처리량 ≈ 스레드 수 ÷ 호출 지연.

**② 공급사 지연이 전체로 번진다** — `tomcat.threads.busy`가 `config.max`에 붙은 채
유지되고, `http.server.requests`에서 공급사와 무관한 URI(`/internal/mappings` 등)의 지연까지
오르면 스레드 인질 상황입니다. 이때 원인은 트래픽 증가가 아니라 ①이므로, 대응은 스케일아웃이
아니라 해당 공급사 격리(호출 제한 하향 등)입니다.

**③ 조용한 장애** — B는 죽어도 HTTP 200을 주므로 게이트웨이·LB의 상태 코드 지표에는
아무것도 안 보입니다. `stayport.supplier.failure{supplier=B, type=SUPPLIER_ERROR}`가 오르는데
5xx는 0인 상태가 바로 그 장애입니다. 본문 판정을 지표로 만들어 둔 이유입니다.

**④ 부르는 것을 멈춘 상태** — `circuit.open`이 1이면 그 공급사는 반복 실패로 차단된 상태입니다.
이때 `failure{type=CIRCUIT_OPEN}`만 오르고 `call` 타이머는 멈춥니다. 실패 카운터가 줄었다고
회복으로 읽으면 안 되는 구간이라, 게이지를 함께 봐야 "실패가 멈춘 것"과 "부르는 것을 멈춘 것"이
구분됩니다.

**⑤ 데이터 품질 문제** — `stayport.supplier.skipped`가 오르면 검색 결과가 줄어든 원인이
재고가 아니라 매핑·형식 문제라는 신호입니다. 무엇을 버렸는지는 `GET /internal/quarantine`에
원본째 남아 있습니다.

## 경보를 단다면

이 저장소 범위에서는 경보 시스템을 붙이지 않았지만, 붙인다면 우선순위는 세 개입니다:

1. **공급사별 실패율** — `failure` 증가율 ÷ `call` 횟수. 유형별로 임계가 달라야 합니다:
   `AUTH`는 1건도 즉시(재시도해도 안 바뀌는 실패), `TIMEOUT`은 비율로(일시적일 수 있음)
2. **공급사별 지연 p95** — 호출 제한(3초)의 2/3 지점을 경계로. 제한에 닿기 전에 알아야
   대응할 시간이 생깁니다
3. **`tomcat.threads.busy / config.max`** — 지속적으로 80%를 넘으면 ②로 번지기 직전입니다
4. **`circuit.open`이 1로 유지** — 자동 복구(반열림)가 계속 실패한다는 뜻이라 사람이 봐야 합니다

## 한계

- 레지스트리가 인메모리(`SimpleMeterRegistry`)라 앱을 재시작하면 값이 사라집니다. 운영이라면
  `micrometer-registry-prometheus` 의존성 하나로 스크레이프 대상이 됩니다 — 이 저장소에서
  붙이지 않은 것은 수집기 없이는 검증할 수 없는 코드가 되기 때문입니다
- 히스토그램(percentile) 노출은 켜지 않았습니다. 태그 조합 수가 지표 저장 비용을 정하는데,
  로컬 확인 용도에서는 COUNT·TOTAL·MAX로 충분합니다
