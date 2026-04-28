# 변경 작업 가이드

## 새 회사를 추가할 때

수정 후보:

1. `src/main/java/gunnu/coupang/entity/Company.java`
2. `ProductDataService.determineCompany()`
3. `ViewController.productManagement()`
4. `ViewController.savedData()`
5. `src/main/resources/templates/product-management.html`
6. `src/main/resources/templates/saved-data.html`

현재 회사별 분류가 Controller와 template에 반복되어 있어 enum만 추가하면 화면에 자동 반영되지 않습니다.

## 새 카테고리를 제대로 지원할 때

수정 후보:

1. `src/main/java/gunnu/coupang/entity/Category.java`
2. `ProductDataService.saveProductData()`의 카테고리 지정 로직
3. HTML 입력 화면에서 카테고리 선택을 받을지 결정
4. `/saved-data` 조회 로직
5. `/product-management` 조회 로직
6. template 탭/테이블

현재 신규 상품 카테고리는 `ROUTER`로 고정됩니다. `CAMERA` enum은 있지만 실제 저장/조회 UI는 준비 중 상태에 가깝습니다.

## 파싱 필드를 추가할 때

예: 배송비, 이미지 URL, 단위 가격을 DB에 저장하고 싶을 때

수정 후보:

1. `ProductInfo`에 필드가 있는지 확인 또는 추가
2. `HtmlParserService.parseProductItem()`에서 Jsoup 셀렉터 추가
3. `ProductDailyData` 또는 `Product`에 저장 필드 추가
4. `ProductDataService.saveProductData()`에서 DTO -> Entity 매핑 추가
5. `SavedProductData`에 화면 표시 필드 추가
6. 관련 template에 컬럼 추가
7. 파서 단위 테스트 추가

현재 `ProductInfo`에는 `imageUrl`, `deliveryCost`, `cashbackAmount`, `pricePerUnit` 등이 있지만 `ProductDailyData`에는 해당 컬럼이 주석 처리되어 있거나 저장되지 않습니다.

## 랭킹 저장 정책을 바꿀 때

현재 정책:

- `display_ranking`: 쿠팡 HTML 원본 랭킹
- `ranking`: 광고 제외 후 순서대로 재계산한 순위
- 광고 상품: `ranking=null`

수정 위치:

- `ProductDataService.saveProductData()`
- `ProductDataService.getTodayProductsAsProductInfo()`
- `saved-data.html`의 정렬/표시 로직
- `result.html`의 표시 로직

중복 저장 정책도 함께 확인해야 합니다. 현재는 같은 상품/날짜도 항상 신규 `ProductDailyData`를 생성합니다.

## 즐겨찾기 저장 방식을 바꿀 때

현재 `POST /api/favorites`는 기존 데이터를 모두 삭제한 뒤 다시 저장합니다.

증분 추가/삭제 방식으로 바꾸려면:

1. `FavoriteProductController`에 추가/삭제 API 설계
2. `FavoriteProductService.saveFavorites()` replace 전략 변경 또는 새 메서드 추가
3. `FavoriteProductRepository`의 `existsByProduct`, `deleteByProduct` 활용
4. `product-management.html`, `saved-data.html`의 저장 버튼 UX 변경

## 조회 성능을 개선할 때

현재 병목 후보:

- `ProductDataService.getProductDataByCategoryAndCompany()`
- `getAvailableDates()`
- `getAllNonAdProducts()`
- `getTodayProductsAsProductInfo()`
- `getFavoriteProductsWithData()`

이 메서드들은 대체로 `findAll()` 후 Stream으로 필터링합니다. 데이터가 많아지면 repository query로 바꾸는 것이 좋습니다.

예상 repository 메서드:

```java
List<ProductDailyData> findByDate(LocalDate date);
List<ProductDailyData> findByDateAndProduct_CategoryAndProduct_Company(
    LocalDate date,
    Category category,
    Company company
);
List<ProductDailyData> findByDateAndProduct_CategoryAndIsAdFalse(
    LocalDate date,
    Category category
);
```

정렬까지 DB에서 처리하려면 `OrderByRankingAsc`, `OrderByDisplayPositionAsc` 또는 `@Query` 사용을 검토합니다.

## DB 스키마 변경 시

현재는 `ddl-auto: update`가 자동 반영하지만, 운영 DB라면 다음 절차가 더 안전합니다.

1. 엔티티 변경
2. migration SQL 작성
3. 로컬/스테이징 DB에서 migration 검증
4. 데이터 백필 필요 여부 확인
5. 운영 반영

특히 문자열 가격을 숫자로 바꾸거나 unique constraint를 추가할 때는 기존 데이터 정리가 필요할 수 있습니다.

## 보안 설정 변경 시

우선순위:

1. `application.yml`의 DB credential 제거
2. 환경 변수 기반 datasource 설정
3. 운영 profile 분리
4. `coupang_key.pem` 제거 또는 secret storage로 이동
5. 이미 노출된 DB 비밀번호 교체

## 기능 작업 전 빠른 체크리스트

- 변경 대상이 파서인지, 저장 정책인지, 화면 표시인지 먼저 분리합니다.
- entity 변경이면 DB 기존 데이터와 `ddl-auto` 영향을 확인합니다.
- 회사/카테고리 변경은 Controller와 template 중복 코드를 함께 찾습니다.
- 파서 변경은 최신 쿠팡 HTML 샘플을 확보한 뒤 테스트를 추가합니다.
- 즐겨찾기 변경은 replace 전략을 유지할지 먼저 결정합니다.
