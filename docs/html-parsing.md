# HTML 파싱 구현

## 담당 클래스

`HtmlParserService`가 Jsoup 기반 파싱을 전담합니다.

주요 진입점:

- `extractContentsSection(String htmlText)`
- `extractContentsSectionInnerHtml(String htmlText)`
- `extractProductList(String htmlText)`

## 입력 HTML 전제

상품 목록은 다음 구조를 기대합니다.

```text
section#contents
└── ul#product-list
    └── li.ProductUnit_productUnit__Qd6sv
```

현재 실제 상품 파싱은 `section#contents`를 먼저 잘라내지 않아도 됩니다. `extractProductList()`는 입력 전체 HTML에서 직접 `ul#product-list`를 찾습니다.

## 상품 목록 추출

`extractProductList()` 처리 순서:

1. 입력 문자열 null/blank 검사
2. `Jsoup.parse(htmlText)`
3. `ul#product-list` 검색
4. `li.ProductUnit_productUnit__Qd6sv` 목록 검색
5. 각 `li`를 `parseProductItem()`으로 변환
6. `displayPosition`을 HTML 내 순서로 1부터 부여
7. `ranking != null`이면 랭킹 상품, 아니면 광고/일반으로 분류
8. `ProductListResponse.success()` 반환

## 현재 Jsoup 셀렉터

| 필드 | 셀렉터/추출 방식 |
| --- | --- |
| 상품 목록 | `ul#product-list` |
| 상품 item | `li.ProductUnit_productUnit__Qd6sv` |
| vendorItemId | `li`의 `data-id` |
| 링크 | 첫 번째 `a` |
| productId | href의 `/products/{productId}` |
| itemId | href query의 `itemId=` |
| 이미지 | `figure.ProductUnit_productImage__Mqcg1 img` |
| 상품명 | `div.ProductUnit_productNameV2__cV9cw` |
| 가격 영역 | `div.PriceArea_priceArea__NntJz` |
| 원가 | 가격 영역 안의 `del` |
| 할인율 | `div.fw-mr-\[2px\]` |
| 현재가 | `div.fw-text-\[20px\]\/\[24px\]` |
| 단위 가격 | `span.fw-text-\[12px\]` |
| 배송 영역 | `div.fw-text-\[14px\] > div` |
| 평점 영역 | `div.ProductRating_productRating__jjf7W` |
| 별점 width | `div.ProductRating_star__RGSlV`의 `style` |
| 리뷰 수 | `span.ProductRating_ratingCount__R0Vhz`, fallback `span.fw-inline-block` |
| 적립금 | `div.BenefitBadge_cash-benefit__SmkrN span` |
| 광고 여부 | `div.AdMark_adMark__KPMsC` 존재 여부 |
| 원본 랭킹 | `span[class^=RankMark_rank]` |

## 배송 방식 판별

기본값은 `NORMAL_DELIVERY`입니다.

`ROCKET_MERCHANT` 판별 조건:

- `img[src*=logoRocketMerchantLargeV3R3]`
- `img[src*=badge_199559e56f7]`
- `img[data-badge-id=ROCKET_MERCHANT]`
- `img[src*=logo_rocket_merchant_medium]`

`ROCKET_DELIVERY` 판별 조건:

- `img[src*=logo_rocket_large]`
- `img[src*=badge_1998ab96bf7]`
- `img[data-badge-id=ROCKET]`
- `img[src*=logo_rocket_filter_medium]`

## URL 파싱 규칙

상품 URL 예시:

```text
/vp/products/8586147830?itemId=24915072273&vendorItemId=...
```

저장용 URL은 `https://www.coupang.com` prefix를 붙여 생성합니다.

## 랭킹 분류

`HtmlParserService`는 HTML에서 직접 읽은 랭킹을 `ProductInfo.ranking`에 담습니다. 이후 `ProductDataService.saveProductData()`에서 저장 시 두 종류의 랭킹으로 나뉩니다.

- `display_ranking`: HTML에서 추출한 원본 랭킹
- `ranking`: 광고 상품을 제외한 상품 순서. 광고 상품은 null

## 파싱 실패 처리

- 전체 입력이 비었거나 `ul#product-list`가 없으면 예외를 던집니다.
- 개별 상품 파싱 실패는 로그를 남기고 해당 상품만 스킵합니다.
- 개별 상품 파싱 실패 시에도 `displayPosition`은 증가합니다.

## 변경 시 주의점

- 쿠팡 class명은 자주 바뀔 수 있으므로 파서 변경 시 샘플 HTML과 실제 HTML을 모두 확인해야 합니다.
- `CLAUDE.md`의 파서 셀렉터 설명은 현재 코드와 다를 수 있습니다. 실제 기준은 `HtmlParserService`입니다.
- 광고 여부는 현재 `div.AdMark_adMark__KPMsC`만 봅니다. 다른 광고 표시 방식이 생기면 추가해야 합니다.
- 리뷰 수는 저장 전에 `ProductDataService.removeParentheses()`에서도 한 번 더 정리됩니다.
- 가격은 문자열로 저장되므로 숫자 비교/정렬을 하려면 별도 정규화가 필요합니다.

## 파싱 테스트 절차

1. 새 쿠팡 HTML을 `src/main/resources`의 샘플 파일로 보관합니다.
2. `HtmlParserService.extractProductList()`에 대한 단위 테스트를 추가합니다.
3. 최소 확인 필드: `itemId`, `productId`, `productName`, `currentPrice`, `isAd`, `ranking`, `deliveryMethod`.
4. `/` 화면에서 실제 입력 후 `result.html`과 DB 저장 결과를 확인합니다.
