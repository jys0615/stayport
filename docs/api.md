# API 명세

두 종류가 있습니다. 고객용 검색 API 하나와, 운영용 내부 엔드포인트 넷입니다.

이 문서는 **계약과 그 근거**를 설명합니다. 실행 중인 서버의 스키마는 SpringDoc이 생성하므로
`http://localhost:8080/swagger-ui.html`에서 직접 눌러 볼 수 있습니다.

## GET /api/v1/stays/search

날짜와 인원으로 보유 숙소 전체를 검색합니다. 지역·키워드 필터, 정렬, 페이징은 없습니다.

### 요청

| 파라미터 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `checkIn` | date (ISO) | ✔ | |
| `checkOut` | date (ISO) | ✔ | `checkIn`보다 뒤. 체크아웃일은 숙박일이 아닙니다 |
| `adults` | int | ✔ | 1 이상 |
| `children` | int | | 0 이상, 생략 시 0 |

```
GET /api/v1/stays/search?checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0
```

### 상태 코드 계약

| 상태 | 언제 |
|---|---|
| `200` | 요청이 성립하면 항상 — **공급사가 전부 실패해도 200입니다** |
| `400` | 요청 자체가 성립하지 않을 때만 (누락·형식 오류·값 검증 실패) |

공급사 실패를 5xx로 접지 않는 이유: 이 API의 실패는 공급사 단위로 부분적이라, 응답 전체의
상태 코드로는 "A는 됐고 B만 죽었다"를 표현할 수 없습니다. 재시도 판단은 상태 코드가 아니라
`suppliers[].status`로 내립니다. (근거: design.md §7)

400 본문은 실패 지점과 무관하게 한 가지입니다:

```json
{ "error": "INVALID_REQUEST", "message": "checkOut must be after checkIn" }
```

### 응답 (200)

```json
{
  "checkIn": "2026-09-01",
  "checkOut": "2026-09-04",
  "nights": 3,
  "adults": 2,
  "children": 0,
  "stays": [
    {
      "stayId": 2,
      "stayName": "Riverside Hotel Seoul",
      "roomTypeId": 2,
      "roomTypeName": "Deluxe Twin",
      "maxOccupancy": 2,
      "availableRooms": 1,
      "bookable": true,
      "breakfastIncluded": false,
      "supplier": "A",
      "price": {
        "totalAmount": 429000,
        "currency": "KRW",
        "dailyBreakdown": [
          { "date": "2026-09-01", "grossAmount": 132000, "netAmount": 120000, "taxAmount": 12000 },
          { "date": "2026-09-02", "grossAmount": 165000, "netAmount": 150000, "taxAmount": 15000 },
          { "date": "2026-09-03", "grossAmount": 132000, "netAmount": 120000, "taxAmount": 12000 }
        ]
      }
    }
  ],
  "suppliers": [
    { "supplier": "A", "status": "OK",     "returnedOffers": 2, "skippedItems": 0, "failedChunks": 0, "failures": {} },
    { "supplier": "B", "status": "FAILED", "returnedOffers": 0, "skippedItems": 0, "failedChunks": 0, "failures": { "SUPPLIER_ERROR": 1 } }
  ]
}
```

### 필드 해석 규칙

**stays[]** — 1건 = 숙소 × 객실 타입 × 검색 조건. 내부 식별자 오름차순으로 고정 정렬됩니다.

| 필드 | 규칙 |
|---|---|
| `stayId` / `roomTypeId` | 내부 식별자. 공급사 코드는 노출하지 않습니다. 같은 공급사 상품은 재검색해도 항상 같은 값 |
| `stayName` / `roomTypeName` | 동기화 시점의 스냅샷 — 갱신 주기는 동기화 주기를 따릅니다 |
| `availableRooms` | 요청 기간 전체를 예약할 수 있는 객실 수 = min(날짜별 잔여). `0`이면 예약 불가지만 **응답에서 빼지 않습니다** — "객실이 없다"와 "만실이다"는 다른 사실입니다 |
| `bookable` | `availableRooms > 0`. 판정 규칙을 클라이언트가 재구현하지 않도록 같이 줍니다 |
| `breakfastIncluded` | 같은 객실이라도 공급사마다 다를 수 있습니다. 가격 비교 시 이 값을 무시하면 조건이 다른 상품을 비교하게 됩니다 |
| `price.totalAmount` | **요청 기간 전체** 총액, 세금 포함(gross). 1박 요금이 아닙니다 |
| `price.dailyBreakdown` | 날짜별 분해. **주지 않는 공급사에서는 `null`** — 있으면 날짜별 gross 보장, `netAmount`·`taxAmount`는 그것을 주는 공급사에서만 채워집니다 |

같은 건물을 두 공급사가 팔면 **서로 다른 `stayId`로 두 번 나옵니다.** 병합하지 않는 이유는
docs/design.md §8과 duplicate-matching 문서를 참고해 주세요. 같은 숙소로 보이는 쌍은
`GET /internal/duplicates`로 조회할 수 있습니다.

**suppliers[]** — 공급사별로 어디까지 받았는지. 조회에 참여한(또는 참여했어야 할) 모든
공급사가 항상 들어 있습니다.

| status | 의미 | 클라이언트가 할 일 |
|---|---|---|
| `OK` | 물어본 것을 전부 받음 | — |
| `PARTIAL` | 나눠 부른 것 중 일부만 받음. 받은 상품은 `stays`에 포함 | 부족하면 해당 공급사만 재시도 |
| `FAILED` | 아무것도 받지 못함 | `failures`를 보고 재시도 여부 판단 |
| `NO_MAPPING` | 매핑이 없어 부르지 않음 (첫 동기화 전이거나 동기화 실패 직후) | 재시도 무의미 — 서버 쪽 복구 사안 |

| 필드 | 규칙 |
|---|---|
| `failures` | 실패 유형별 개수. `AUTH`·`INVALID_REQUEST`·`PARSE_ERROR`는 재시도해도 같고, `RATE_LIMIT`·`SUPPLIER_ERROR`·`TIMEOUT`은 시간이 지나면 달라질 수 있습니다 |
| `skippedItems` | 형태가 깨졌거나 매핑에 없어 버린 상품 수. 결과가 적은 이유가 재고인지 데이터 문제인지 구분하는 근거 |
| `failedChunks` | 나눠 부른 것 중 실패한 묶음 수 |

### 시간 계약

응답은 늦어도 검색 예산(기본 3.5초) 안에 옵니다. 공급사가 무응답이어도 그 공급사만
`TIMEOUT`으로 접고 나머지로 응답합니다. 값은 `application.yml`의 `stayport.search.total-budget`에
있습니다.

## 운영용 (내부)

외부 공개를 전제하지 않는 엔드포인트입니다. 실제 운영이라면 네트워크 정책이나 별도 포트로
접근을 제한할 대상이고, 이 저장소 범위에서는 경로 접두어(`/internal`)로만 구분합니다.

### POST /internal/sync

공급사 숙소 목록을 다시 읽어 매핑을 갱신합니다. 이미 실행 중이면 아무것도 하지 않고
`skipped: true`로 즉시 돌아옵니다. 기동 시 동기화가 실패했을 때의 복구 경로입니다.

```json
{
  "skipped": false,
  "suppliers": [
    { "supplier": "A", "status": "OK", "stays": 3, "roomTypes": 3, "failureType": null, "detail": null },
    { "supplier": "B", "status": "FAILED", "stays": 0, "roomTypes": 0, "failureType": "SUPPLIER_ERROR", "detail": "E503 TEMPORARILY_UNAVAILABLE" }
  ],
  "totalStays": 4,
  "totalRoomTypes": 4
}
```

한 공급사가 실패해도 나머지는 갱신되고, 기존 매핑은 지워지지 않습니다.

### GET /internal/mappings

저장된 매핑 전체. 동기화 결과를 눈으로 확인하는 용도입니다.

```json
[
  {
    "supplier": "A", "supplierStayCode": "A-10023", "supplierRoomCode": "DLX-TWN",
    "internalStayId": 2, "stayName": "Riverside Hotel Seoul",
    "internalRoomTypeId": 2, "roomTypeName": "Deluxe Twin", "maxOccupancy": 2
  }
]
```

### GET /internal/quarantine

정규화에서 버려져 격리된 상품들입니다. 사유와 공급사 원본이 그대로 남아 있어 추후 분석에
씁니다 (docs/design.md §10).

### GET /internal/duplicates

서로 다른 공급사가 같은 숙소를 파는 것으로 보이는 쌍입니다. 공급사 간 공통 키가 없어
숙소명 토큰과 객실 구성으로 추정하며, **자동 병합은 하지 않고 후보만 보여줍니다** —
판정 기준과 병합하지 않는 이유는 duplicate-matching 문서에 있습니다.

```json
[
  {
    "first":  { "supplier": "B", "supplierStayCode": "B77120", "internalStayId": 1, "stayName": "Riverside Hotel Seoul" },
    "second": { "supplier": "A", "supplierStayCode": "A-10023", "internalStayId": 2, "stayName": "Riverside Hotel Seoul" },
    "nameSimilarity": 1.0,
    "roomMatchRatio": 1.0
  }
]
```
