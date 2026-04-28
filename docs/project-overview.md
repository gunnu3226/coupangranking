# 프로젝트 개요

## 목적

쿠팡 검색/랭킹 페이지 HTML을 직접 입력받아 상품 목록을 추출하고, 날짜별 가격/랭킹/리뷰/광고 여부를 PostgreSQL에 저장하는 애플리케이션입니다. 저장된 데이터는 회사별, 날짜별, 즐겨찾기 상품별로 조회할 수 있습니다.

## 기술 스택

- Java 17
- Spring Boot 3.5.6
- Spring Web MVC
- Spring Data JPA / Hibernate
- React 19
- Vite
- PostgreSQL JDBC Driver
- Jsoup 1.17.2
- Lombok
- Gradle
- Docker

## 실행 단위

Spring Boot 백엔드와 `FE/` React 프론트엔드가 같은 저장소에 있습니다. Gradle 빌드 시 React 앱을 Vite로 빌드하고 결과물을 `src/main/resources/static`에 생성해 Spring Boot가 정적 리소스로 서빙합니다.

## 패키지 구조

```text
src/main/java/gunnu/coupang
├── CoupangApplication.java
├── controller
│   ├── FavoriteProductController.java
│   ├── HealthController.java
│   ├── HtmlParserController.java
│   ├── AppApiController.java
│   └── SpaController.java
├── dto
│   ├── HtmlParseRequest.java
│   ├── HtmlParseResponse.java
│   ├── ProductInfo.java
│   ├── ProductListResponse.java
│   └── SavedProductData.java
├── entity
│   ├── Category.java
│   ├── Company.java
│   ├── DeliveryMethod.java
│   ├── FavoriteProduct.java
│   ├── Product.java
│   └── ProductDailyData.java
├── repository
│   ├── FavoriteProductRepository.java
│   ├── ProductDailyDataRepository.java
│   └── ProductRepository.java
└── service
    ├── FavoriteProductService.java
    ├── HtmlParserService.java
    └── ProductDataService.java
```

## 핵심 처리 흐름

1. 사용자가 React `/` 화면에서 쿠팡 HTML을 1개 이상 붙여 넣습니다.
2. 브라우저 JS가 HTML 문자열 배열을 `POST /api/app/parse`로 전송합니다.
3. `AppApiController.parseHtml()`이 페이지별 HTML을 받습니다.
4. `HtmlParserService.extractProductList()`가 Jsoup으로 상품 정보를 추출합니다.
5. `ProductDataService.saveProductData()`가 상품 마스터와 일자별 데이터를 저장합니다.
6. React `/parse` 화면이 이번 파싱 결과를 보여줍니다.
7. `/saved-data`에서 날짜별 저장 데이터를 조회합니다.
8. `/product-management`와 `/favorite-order-edit`에서 관심 상품과 표시 순서를 관리합니다.

## 현재 도메인 범위

- 카테고리 enum에는 `CAMERA`, `ROUTER`가 있지만 저장 로직은 현재 모든 신규 상품을 `ROUTER`로 저장합니다.
- 회사 enum은 `IPTIME`, `TPLINK`, `NETIS`, `MERCUSYS`, `ASUS`, `MI`입니다.
- 화면의 회사별 분류는 대부분 `IPTIME`, `TPLINK`, `NETIS`, `MERCUSYS`, `ASUS` 중심입니다. `MI`는 enum에는 있지만 주요 화면 분류에는 아직 반영되지 않았습니다.

## 개발 관점의 특징

- 화면은 React SPA이며, 백엔드는 JSON API와 정적 파일 서빙을 담당합니다.
- 데이터 저장은 JPA 엔티티 기반이며 별도 마이그레이션 도구는 없습니다.
- 일자별 데이터는 같은 상품/날짜라도 중복 저장될 수 있게 되어 있습니다.
- 파싱은 쿠팡 HTML의 CSS class명에 강하게 의존합니다.
