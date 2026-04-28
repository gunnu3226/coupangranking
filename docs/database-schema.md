# DB 구조

## DB와 JPA 설정

- DBMS: PostgreSQL
- ORM: Spring Data JPA / Hibernate
- 물리 네이밍 전략: `PhysicalNamingStrategyStandardImpl`
- DDL 설정: `spring.jpa.hibernate.ddl-auto=update`
- 기준 날짜: `Asia/Seoul` 타임존의 현재 날짜

주의: `application.yml`에 실제 DB 접속 정보가 직접 들어 있습니다. 문서에는 값을 옮기지 않지만, 운영 안정성과 보안을 위해 환경 변수 또는 외부 secret 주입 방식으로 바꿔야 합니다.

## 실제 DB 스키마 기준

아래 내용은 전달받은 운영/실제 DB DDL 기준입니다. 엔티티와 대부분 일치하지만, DB 레벨 `NOT NULL`이 거의 없고 unique index도 없습니다. 애플리케이션 코드에서 필수값으로 다루는 필드라도 DB는 null을 허용할 수 있습니다.

전체 DDL은 [actual-db-schema.sql](./actual-db-schema.sql)에 별도로 보관했습니다.

## ERD 요약

```text
products 1 ── N product_daily_data
products 1 ── N favorite_products
```

`ProductDailyData.product`와 `FavoriteProduct.product`는 모두 `Product`를 참조하는 `@ManyToOne(fetch = LAZY)` 관계입니다.

## products

엔티티: `Product`

| 컬럼 | Java 필드 | 타입 | 설명 |
| --- | --- | --- | --- |
| `id` | `id` | `Long` | PK, identity |
| `item_id` | `itemId` | `String` | 쿠팡 URL query의 `itemId`, 실제 DB는 null 허용 |
| `product_id` | `productId` | `String` | 쿠팡 URL path의 product id, 실제 DB는 null 허용 |
| `product_name` | `productName` | `String(500)` | 상품명 |
| `model_code` | `modelCode` | `String` | 모델 코드, 현재 저장 로직에서는 사용하지 않음 |
| `category` | `category` | `Category` enum | check constraint 존재. 현재 신규 저장 기본값은 `ROUTER` |
| `company` | `company` | `Company` enum | check constraint 존재. 상품명 키워드 기반 자동 판별 |
| `displayorder` | `displayOrder` | `Integer` | 실제 DB 컬럼명은 소문자 `displayorder`. 현재 주요 로직에서는 거의 사용하지 않음 |
| `created_at` | `createdAt` | `LocalDateTime` | `@PrePersist`에서 설정. 실제 DB는 null 허용 |
| `updated_at` | `updatedAt` | `LocalDateTime` | 생성/수정 시 설정 |

상품 식별 기준은 `itemId + productId` 조합입니다. 리포지토리에는 `findByItemIdAndProductId()`가 있습니다. 단, DB 레벨 unique constraint는 엔티티에 선언되어 있지 않습니다.

실제 check constraint:

- `products_category_check`: `CAMERA`, `ROUTER`
- `products_company_check`: `IPTIME`, `TPLINK`, `NETIS`, `MERCUSYS`, `ASUS`, `MI`

## product_daily_data

엔티티: `ProductDailyData`

| 컬럼 | Java 필드 | 타입 | 설명 |
| --- | --- | --- | --- |
| `id` | `id` | `Long` | PK, identity |
| `product_id` | `product` | `Product` | `products.id` FK. 실제 DB는 null 허용 |
| `date` | `date` | `LocalDate` | 수집 날짜. 실제 DB는 null 허용 |
| `display_position` | `displayPosition` | `Integer` | HTML 목록 내 실제 순서 |
| `display_ranking` | `display_ranking` | `Integer` | 쿠팡 HTML에서 추출한 원본 랭킹 |
| `ranking` | `ranking` | `Integer` | 광고 제외 후 재계산한 순위 |
| `is_ad` | `isAd` | `Boolean` | 광고 상품 여부 |
| `delivery_method` | `deliveryMethod` | `DeliveryMethod` enum | check constraint 존재. 배송/판매 방식 |
| `current_price` | `currentPrice` | `String` | 현재가, 저장 전 `원` 제거 |
| `original_price` | `originalPrice` | `String` | 원가, 저장 전 `원` 제거 |
| `discount_rate` | `discountRate` | `String` | 할인율 |
| `rating` | `rating` | `String` | 평점 표현. 현재 파서는 별점 width 비율 문자열을 저장 |
| `review_count` | `reviewCount` | `String` | 리뷰 수, 괄호 제거 |
| `product_url` | `productUrl` | `String(1000)` | 상품 URL |
| `created_at` | `createdAt` | `LocalDateTime` | `@PrePersist`에서 설정. 실제 DB는 null 허용 |

현재 저장 로직은 같은 상품/날짜/위치의 기존 데이터를 업데이트하지 않고 항상 새 `ProductDailyData`를 생성합니다. 엔티티와 리포지토리에는 `findByProductAndDate...` 메서드가 있지만 `saveProductData()`에서는 사용하지 않습니다.

실제 check constraint:

- `product_daily_data_delivery_method_check`: `ROCKET_DELIVERY`, `ROCKET_MERCHANT`, `NORMAL_DELIVERY`

## favorite_products

엔티티: `FavoriteProduct`

| 컬럼 | Java 필드 | 타입 | 설명 |
| --- | --- | --- | --- |
| `id` | `id` | `Long` | PK, identity |
| `product_id` | `product` | `Product` | `products.id` FK. 실제 DB는 null 허용 |
| `display_order` | `displayOrder` | `Integer` | 사용자가 지정한 표시 순서 |
| `created_at` | `createdAt` | `LocalDateTime` | `@PrePersist`에서 설정. 실제 DB는 null 허용 |

즐겨찾기 저장은 replace 전략입니다. `FavoriteProductService.saveFavorites()`는 기존 `favorite_products`를 모두 삭제한 뒤 전달받은 상품 ID 순서대로 다시 저장합니다.

## Enum 값

`Category`

```text
CAMERA
ROUTER
```

`Company`

```text
IPTIME
TPLINK
NETIS
MERCUSYS
ASUS
MI
```

`DeliveryMethod`

```text
ROCKET_DELIVERY
ROCKET_MERCHANT
NORMAL_DELIVERY
```

## 저장 규칙

- 수집 날짜는 `LocalDate.now(ZoneId.of("Asia/Seoul"))`입니다.
- `Product`는 `itemId + productId`로 조회 후 없으면 생성합니다.
- 상품명이 바뀌면 기존 `Product.productName`을 업데이트합니다.
- 신규 상품의 회사는 상품명 키워드로 판별합니다.
- 신규 상품의 카테고리는 현재 무조건 `ROUTER`입니다.
- 광고 상품은 `ranking=null`로 저장합니다.
- 비광고 상품은 입력된 전체 상품 목록 순서 기준으로 `ranking`을 1부터 부여합니다.
- 원본 쿠팡 랭킹은 `display_ranking`에 별도로 저장합니다.

## 성능/무결성 개선 후보

- `products(item_id, product_id)` unique index 추가
- `product_daily_data(product_id, date, display_position)` 또는 수집 회차 개념 추가
- 코드의 `nullable = false`와 실제 DB null 허용 상태 정합성 맞추기
- 날짜/카테고리/회사 조회를 `findAll()` 후 Stream 필터링이 아니라 DB query로 변경
- `ProductDailyData.display_ranking` 필드명을 Java convention인 `displayRanking`으로 정리
- 금액/리뷰 수를 문자열 대신 숫자 타입으로 정규화할지 검토
