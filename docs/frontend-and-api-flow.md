# 화면과 API 흐름

## 현재 화면 구조

React 프론트엔드는 `FE/`에 있고 Vite로 빌드됩니다. 빌드 결과는 `src/main/resources/static`에 생성되며 Spring Boot가 `/`에서 `index.html`을 제공합니다.

| React Route | 역할 |
| --- | --- |
| `/` | 쿠팡 HTML 입력 |
| `/parse` | 파싱/저장 결과 표시 |
| `/today-result` | 오늘 저장된 데이터 결과 표시 |
| `/saved-data` | 저장된 날짜별 데이터 조회 |
| `/product-management` | 상품 마스터와 관심 상품 선택 |
| `/favorite-order-edit` | 관심 상품 표시 순서 편집 |

`src/main/resources`에는 `guide.html`, `example.html`, `contents_example.html`, `production_list.html`, `test.html` 같은 샘플 HTML도 있습니다.

## 메인 파싱 흐름

```text
React HomePage
  -> POST /api/app/parse
  -> AppApiController.parseHtml()
  -> HtmlParserService.extractProductList()
  -> ProductDataService.saveProductData()
  -> JSON ParseResultResponse
  -> React ResultPage
```

요청 형식:

```json
[
  "<html>...</html>",
  "<html>...</html>"
]
```

응답은 JSON입니다. React는 응답 데이터를 상태로 보관한 뒤 `/parse` 화면에서 표시합니다.

## 저장 데이터 조회 흐름

```text
GET /api/app/saved-data?date=2026-04-27
  -> AppApiController.savedData()
  -> ProductDataService 날짜/회사별 조회
  -> FavoriteProductService 선택 상품 조회
  -> JSON SavedDataResponse
  -> React SavedDataPage
```

주요 화면 기능:

- 날짜 선택 후 React가 `/api/app/saved-data?date=...`를 다시 호출
- 회사별 테이블 표시
- 검색어로 `itemId`, 상품명 필터링
- 광고/전체 필터링
- 관심 상품 탭 표시
- 관심 상품의 순위순/사용자 지정 순서 정렬
- 테이블 복사 기능

## 상품 관리 흐름

```text
GET /api/app/products
  -> 전체 ROUTER 상품 조회
  -> 회사별 분류
  -> favoriteProductIds 체크 상태 반영
```

사용자가 체크박스를 선택한 뒤 저장하면:

```text
React ProductManagementPage
  -> JS saveFavorites()
  -> POST /api/favorites
  -> FavoriteProductController.saveFavorites()
  -> FavoriteProductService.saveFavorites()
  -> favorite_products 전체 재생성
```

요청:

```json
{
  "productIds": [1, 2, 3]
}
```

## 관심 상품 순서 편집 흐름

```text
GET /api/app/favorites/full
  -> displayOrder 기준 관심 상품 목록 렌더링
  -> HTML5 drag-and-drop으로 DOM 순서 변경
  -> PUT /api/favorites/order
```

요청:

```json
{
  "orderMap": {
    "1": 1,
    "2": 2,
    "3": 3
  }
}
```

`Map<Long, Integer>`로 역직렬화됩니다. JSON 객체 key는 문자열이어도 Jackson이 Long key로 변환합니다.

## REST API 목록

### React App API

`POST /api/app/parse`

- Body: HTML 문자열 배열
- Response: 파싱 결과, 상품 분류 목록, 카운트

`GET /api/app/today-result`

- Response: 오늘 저장된 데이터를 파싱 결과와 같은 형태로 반환

`GET /api/app/products?category=ROUTER`

- Response: 상품 마스터 목록, 회사별 목록, 관심 상품 ID

`GET /api/app/saved-data?date=yyyy-MM-dd`

- Response: 날짜별 저장 데이터, 회사별 목록, 관심 상품 목록, 표시 순서 맵

`GET /api/app/favorites/full`

- Response: 관심 상품을 표시 순서대로 반환

### HTML API

`POST /api/html/extract-products`

- Consumes: `text/plain`
- Produces: `application/json`
- Body: HTML 문자열
- Response: `ProductListResponse`

`POST /api/html/extract-section`

- Consumes: `text/plain`
- Produces: `application/json`
- Body: HTML 문자열
- Response: `HtmlParseResponse`

### Favorite API

`POST /api/favorites`

```json
{
  "productIds": [1, 2, 3]
}
```

`PUT /api/favorites/order`

```json
{
  "orderMap": {
    "1": 1,
    "2": 2
  }
}
```

`GET /api/favorites/count`

```json
{
  "success": true,
  "count": 3
}
```

`GET /api/favorites/ordered`

```json
{
  "success": true,
  "productIds": [1, 2, 3]
}
```

### Health

`GET /health`

```json
{
  "status": "UP"
}
```

## 프론트엔드 구현 특징

- React 19와 Vite 기반입니다.
- 별도 라우터 라이브러리 없이 History API로 현재 경로를 관리합니다.
- API 호출은 브라우저 `fetch()`를 사용합니다.
- 개발 서버는 `FE`에서 `npm run dev`로 실행하고 `/api`를 `localhost:8080`으로 프록시합니다.
- 테이블 복사는 Clipboard API를 사용합니다.
- 드래그 정렬은 HTML5 draggable API를 사용합니다.

## 화면 수정 시 체크 포인트

- Thymeleaf model attribute 이름은 `ViewController`에서 직접 주입합니다.
- 회사별 섹션이 중복 코드로 작성되어 있어 회사 추가 시 여러 template 수정이 필요합니다.
- `saved-data.html`은 선택 상품 탭과 전체 상품 탭 모두 같은 관심 상품 저장 API를 사용합니다.
- `result.html`은 `/parse`와 `/today-result` 두 경로에서 함께 사용됩니다.
