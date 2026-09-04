package io.github.jys0615.stayport.mock;

/**
 * 고정 응답 본문. 데이터에 심어둔 것: A-10023 ≡ B77120(공통 키 없음, 총액 429,000 vs 452,000,
 * 조식 차이), A-10044는 09-02 재고 0 (연박 min 판정 확인용).
 */
final class MockResponses {

    private MockResponses() {
    }

    static final String A_HOTELS = """
            {
              "items": [
                {
                  "hotelCode": "A-10023",
                  "hotelName": "Riverside Hotel Seoul",
                  "roomTypes": [
                    { "roomTypeCode": "DLX-TWN", "roomTypeName": "Deluxe Twin", "maxOccupancy": 2 }
                  ]
                },
                {
                  "hotelCode": "A-10044",
                  "hotelName": "Namsan Garden Stay",
                  "roomTypes": [
                    { "roomTypeCode": "STD-DBL", "roomTypeName": "Standard Double", "maxOccupancy": 2 }
                  ]
                }
              ]
            }
            """;

    static final String A_AVAILABILITY = """
            {
              "items": [
                {
                  "hotelCode": "A-10023",
                  "hotelName": "Riverside Hotel Seoul",
                  "roomTypeCode": "DLX-TWN",
                  "roomTypeName": "Deluxe Twin",
                  "maxOccupancy": 2,
                  "breakfastIncluded": false,
                  "currency": "KRW",
                  "dailyRates": [
                    { "date": "2026-09-01", "remainingRooms": 3, "nightlyRate": 120000, "taxAmount": 12000 },
                    { "date": "2026-09-02", "remainingRooms": 1, "nightlyRate": 150000, "taxAmount": 15000 },
                    { "date": "2026-09-03", "remainingRooms": 5, "nightlyRate": 120000, "taxAmount": 12000 }
                  ]
                },
                {
                  "hotelCode": "A-10044",
                  "hotelName": "Namsan Garden Stay",
                  "roomTypeCode": "STD-DBL",
                  "roomTypeName": "Standard Double",
                  "maxOccupancy": 2,
                  "breakfastIncluded": false,
                  "currency": "KRW",
                  "dailyRates": [
                    { "date": "2026-09-01", "remainingRooms": 2, "nightlyRate": 88000, "taxAmount": 8800 },
                    { "date": "2026-09-02", "remainingRooms": 0, "nightlyRate": 99000, "taxAmount": 9900 },
                    { "date": "2026-09-03", "remainingRooms": 4, "nightlyRate": 88000, "taxAmount": 8800 }
                  ]
                }
              ]
            }
            """;

    static final String B_PROPERTIES = """
            {
              "resultCode": "0000",
              "resultMessage": "SUCCESS",
              "data": {
                "items": [
                  {
                    "propertyId": "B77120",
                    "propertyName": "Riverside Hotel Seoul",
                    "rooms": [
                      { "roomId": "R-401", "roomName": "Deluxe Twin Room", "maxOccupancy": 2 }
                    ]
                  }
                ]
              }
            }
            """;

    static final String B_SEARCH = """
            {
              "resultCode": "0000",
              "resultMessage": "SUCCESS",
              "data": {
                "items": [
                  {
                    "propertyId": "B77120",
                    "propertyName": "Riverside Hotel Seoul",
                    "roomId": "R-401",
                    "roomName": "Deluxe Twin Room",
                    "maxOccupancy": 2,
                    "breakfastIncluded": true,
                    "currency": "KRW",
                    "totalPrice": 452000,
                    "taxIncluded": true,
                    "inventory": [
                      { "date": "2026-09-01", "remainingRooms": 3 },
                      { "date": "2026-09-02", "remainingRooms": 1 },
                      { "date": "2026-09-03", "remainingRooms": 5 }
                    ]
                  }
                ]
              }
            }
            """;

    // ── 실패 응답 ──

    static final String A_UNAVAILABLE = """
            { "error": "SERVICE_UNAVAILABLE", "message": "temporarily unavailable" }""";

    static final String A_UNAUTHORIZED = """
            { "error": "UNAUTHORIZED", "message": "missing or invalid X-Api-Key" }""";

    static final String A_TOO_MANY_CODES = """
            { "error": "TOO_MANY_HOTEL_CODES", "message": "hotelCodes must not exceed 50" }""";

    static final String B_UNAVAILABLE = """
            { "resultCode": "E503", "resultMessage": "TEMPORARILY_UNAVAILABLE", "data": null }""";

    static final String B_UNAUTHORIZED = """
            { "resultCode": "E401", "resultMessage": "UNAUTHORIZED", "data": null }""";

    static final String B_TOO_MANY_CODES = """
            { "resultCode": "E400", "resultMessage": "TOO_MANY_PROPERTY_IDS", "data": null }""";

    static final String EMPTY = "{}";
}
