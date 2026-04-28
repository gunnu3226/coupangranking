# 백엔드 구조

## 레이어 구성

```text
Controller -> Service -> Repository -> Entity -> PostgreSQL
```

화면 요청은 `ViewController`가 Thymeleaf model을 구성하고, 일부 비동기 요청은 REST controller가 JSON을 반환합니다.

## Controller

### ViewController

Thymeleaf 화면 렌더링과 주요 HTML 파싱 플로우를 담당합니다.

| 메서드 | URL | 역할 |
| --- | --- | --- |
| `index()` | `GET /` | HTML 입력 화면 |
| `favoriteOrderEdit()` | `GET /favorite-order-edit` | 선택 상품 표시 순서 편집 화면 |
| `productManagement()` | `GET /product-management` | 전체 상품/즐겨찾기 선택 관리 화면 |
| `savedData()` | `GET /saved-data?date=yyyy-MM-dd` | 날짜별 저장 데이터 조회 화면 |
| `todayResult()` | `GET /today-result` | 오늘 저장된 데이터를 파싱 결과 화면처럼 표시 |
| `parseHtml()` | `POST /parse` | 여러 HTML 페이지 파싱, DB 저장, 결과 화면 반환 |

특징:

- 회사별 목록 분리를 Controller에서 직접 수행합니다.
- 날짜 문자열 파싱 실패 시 로그만 남기고 오늘 날짜로 조회합니다.
- `parseHtml()`은 JSON 배열로 HTML 문자열 목록을 받습니다.

### HtmlParserController

파싱 기능을 JSON API로 제공합니다.

| 메서드 | URL | Content-Type | 역할 |
| --- | --- | --- | --- |
| `extractProducts()` | `POST /api/html/extract-products` | `text/plain` | HTML에서 상품 목록 추출 |
| `extractSection()` | `POST /api/html/extract-section` | `text/plain` | `section#contents` 추출 |

### FavoriteProductController

관심 상품 저장과 순서 변경 API를 제공합니다.

| 메서드 | URL | 역할 |
| --- | --- | --- |
| `saveFavorites()` | `POST /api/favorites` | 선택 상품 전체 저장 |
| `getFavoriteCount()` | `GET /api/favorites/count` | 선택 상품 개수 조회 |
| `updateDisplayOrder()` | `PUT /api/favorites/order` | 선택 상품 표시 순서 업데이트 |
| `getFavoritesOrdered()` | `GET /api/favorites/ordered` | 표시 순서 기준 상품 ID 목록 조회 |

### HealthController

`GET /health`로 `{ "status": "UP" }`를 반환합니다. Docker/ECS 헬스체크 용도입니다.

## Service

### HtmlParserService

Jsoup 기반 HTML 파싱 전담 서비스입니다.

주요 메서드:

- `extractContentsSection(String htmlText)`: `section#contents` 전체 HTML 반환
- `extractContentsSectionInnerHtml(String htmlText)`: `section#contents` 내부 HTML 반환
- `extractProductList(String htmlText)`: `ul#product-list` 아래 상품 `li`를 파싱해 `ProductListResponse` 반환
- `parseProductItem(Element item)`: 개별 상품 요소를 `ProductInfo`로 변환

파싱 결과는 전체/랭킹/광고/일반 상품으로 분류됩니다.

### ProductDataService

상품 저장, 날짜별 조회, 회사/카테고리 필터링, 순위 계산을 담당합니다.

주요 메서드:

- `saveProductData(List<ProductInfo> products)`
- `getProductDataByCategoryAndCompany(Category category, Company company, LocalDate date)`
- `getAvailableDates()`
- `getAllProducts()`
- `getProductsByCategory(Category category)`
- `getAllNonAdProducts(Category category, LocalDate date)`
- `getTodayProductsAsProductInfo(LocalDate date)`
- `getFavoriteProductsWithData(Set<Long> favoriteProductIds, LocalDate date)`

회사 판별은 `determineCompany()`에서 상품명 문자열에 포함된 키워드로 처리합니다.

### FavoriteProductService

관심 상품 목록과 표시 순서를 관리합니다.

주요 메서드:

- `saveFavorites(List<Long> productIds)`: 기존 선택 전체 삭제 후 재저장
- `getFavoriteProductIds()`
- `isFavorite(Product product)`
- `getFavoriteCount()`
- `updateDisplayOrder(Map<Long, Integer> orderMap)`
- `getFavoritesOrderedByDisplayOrder()`
- `getFavoriteProductIdsOrdered()`

## Repository

### ProductRepository

- `findByItemIdAndProductId(String itemId, String productId)`
- `findByProductId(String productId)`

### ProductDailyDataRepository

- `findByProductAndDate(Product product, LocalDate date)`
- `findByProductAndDateAndDisplayPosition(Product product, LocalDate date, Integer displayPosition)`

현재 저장 로직에서는 중복 방지를 위해 이 메서드를 사용하지 않습니다.

### FavoriteProductRepository

- `findByProduct(Product product)`
- `findAllByOrderByCreatedAtDesc()`
- `existsByProduct(Product product)`
- `deleteByProduct(Product product)`

## DTO

### ProductInfo

파싱 직후 화면/저장 양쪽에서 사용하는 상품 정보 DTO입니다. `itemId`, `productId`, 상품명, URL, 가격, 배송, 평점, 리뷰 수, 광고 여부, 랭킹, 표시 위치 등을 담습니다.

### ProductListResponse

상품 파싱 API 응답 DTO입니다. 전체 상품과 랭킹/광고/일반 분류 목록 및 카운트를 포함합니다.

### SavedProductData

DB에서 조회한 저장 데이터를 화면에 전달하기 위한 DTO입니다. `Product`와 `ProductDailyData`의 표시용 필드를 합칩니다.

### HtmlParseResponse

`section#contents` 추출 API 응답 DTO입니다.

### HtmlParseRequest

`htmlText` 필드를 가진 요청 DTO이지만 현재 controller에서는 직접 사용하지 않습니다.

## 현재 구조상 변경 시 주의할 점

- 회사 추가 시 enum, 회사 판별, Controller의 회사별 분류, 화면 테이블을 함께 수정해야 합니다.
- 카테고리 추가 시 저장 로직이 현재 `ROUTER` 고정이라 분류 입력/판별 로직을 먼저 설계해야 합니다.
- 파서 결과 필드 추가 시 `ProductInfo`, `ProductDailyData`, `ProductDataService.saveProductData()`, 화면 template을 함께 확인해야 합니다.
- 날짜별 조회가 대부분 `findAll()` 기반이라 데이터 증가 후 성능 문제가 생길 수 있습니다.
