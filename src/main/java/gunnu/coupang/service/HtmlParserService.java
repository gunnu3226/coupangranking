package gunnu.coupang.service;

import gunnu.coupang.dto.ProductInfo;
import gunnu.coupang.dto.ProductListResponse;
import gunnu.coupang.entity.DeliveryMethod;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class HtmlParserService {

    private static final String TARGET_SECTION_ID = "contents";
    private static final String TARGET_SECTION_CLASS = "fw-w-full max-[600px]:fw-overflow-hidden";
    private static final String PRODUCT_LIST_ID = "product-list";

    /**
     * HTML 텍스트에서 특정 section 영역을 추출합니다.
     * <section id="contents" class="fw-w-full max-[600px]:fw-overflow-hidden"> 영역을 찾아 반환합니다.
     *
     * @param htmlText 전체 HTML 텍스트
     * @return 추출된 section HTML 문자열
     */
    public String extractContentsSection(String htmlText) {
        if (htmlText == null || htmlText.trim().isEmpty()) {
            log.warn("입력된 HTML 텍스트가 비어있습니다.");
            throw new IllegalArgumentException("HTML 텍스트가 비어있습니다.");
        }

        try {
            // Jsoup으로 HTML 파싱
            Document document = Jsoup.parse(htmlText);

            // id="contents"인 section 태그를 찾음
            Element contentsSection = document.selectFirst("section#" + TARGET_SECTION_ID);

            if (contentsSection == null) {
                log.warn("section#contents 요소를 찾을 수 없습니다.");
                throw new IllegalArgumentException("해당 section 요소를 찾을 수 없습니다.");
            }

            // 추출된 section의 outerHtml 반환 (section 태그 포함)
            String extractedHtml = contentsSection.outerHtml();
            log.info("HTML 추출 완료. 길이: {} characters", extractedHtml.length());

            return extractedHtml;

        } catch (Exception e) {
            log.error("HTML 파싱 중 오류 발생", e);
            throw new RuntimeException("HTML 파싱 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 추출된 HTML에서 innerHTML만 반환합니다 (section 태그 제외)
     *
     * @param htmlText 전체 HTML 텍스트
     * @return 추출된 section의 내부 HTML 문자열
     */
    public String extractContentsSectionInnerHtml(String htmlText) {
        if (htmlText == null || htmlText.trim().isEmpty()) {
            log.warn("입력된 HTML 텍스트가 비어있습니다.");
            throw new IllegalArgumentException("HTML 텍스트가 비어있습니다.");
        }

        try {
            Document document = Jsoup.parse(htmlText);
            Element contentsSection = document.selectFirst("section#" + TARGET_SECTION_ID);

            if (contentsSection == null) {
                log.warn("section#contents 요소를 찾을 수 없습니다.");
                throw new IllegalArgumentException("해당 section 요소를 찾을 수 없습니다.");
            }

            // innerHTML 반환 (section 태그 제외)
            String extractedHtml = contentsSection.html();
            log.info("HTML 추출 완료 (innerHTML). 길이: {} characters", extractedHtml.length());

            return extractedHtml;

        } catch (Exception e) {
            log.error("HTML 파싱 중 오류 발생", e);
            throw new RuntimeException("HTML 파싱 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * HTML 텍스트에서 상품 리스트를 추출합니다.
     * <ul id="product-list"> 하위의 모든 <li> 요소에서 상품 정보를 파싱합니다.
     *
     * @param htmlText 전체 HTML 텍스트
     * @return 상품 리스트 응답 객체
     */
    public ProductListResponse extractProductList(String htmlText) {
        if (htmlText == null || htmlText.trim().isEmpty()) {
            log.warn("입력된 HTML 텍스트가 비어있습니다.");
            throw new IllegalArgumentException("HTML 텍스트가 비어있습니다.");
        }

        try {
            log.info("HTML 파싱 시작. HTML 길이: {} bytes", htmlText.length());
            Document document = Jsoup.parse(htmlText);

            // ul#product-list 찾기
            Element productListUl = document.selectFirst("ul#" + PRODUCT_LIST_ID);

            if (productListUl == null) {
                log.error("ul#product-list 요소를 찾을 수 없습니다.");

                // 디버깅: 다른 셀렉터로 시도
                Elements allUls = document.select("ul");
                log.info("전체 ul 태그 개수: {}", allUls.size());
                for (Element ul : allUls) {
                    log.info("ul 태그 발견 - id: '{}', class: '{}'", ul.id(), ul.className());
                }

                // id에 'product'가 포함된 요소 찾기
                Elements productElements = document.select("[id*=product]");
                log.info("id에 'product' 포함된 요소 개수: {}", productElements.size());
                for (Element el : productElements) {
                    log.info("product 관련 요소 - tag: {}, id: '{}', class: '{}'",
                        el.tagName(), el.id(), el.className());
                }

                throw new IllegalArgumentException("상품 리스트를 찾을 수 없습니다. HTML 구조를 확인해주세요.");
            }

            log.info("ul#product-list 찾음");

            // li 요소들 추출
            Elements productItems = productListUl.select("li.ProductUnit_productUnit__Qd6sv");
            log.info("li.ProductUnit_productUnit__Qd6sv 상품 개수: {}", productItems.size());

            // li 클래스가 없는 경우도 확인
            if (productItems.isEmpty()) {
                Elements allLis = productListUl.select("li");
                log.info("전체 li 개수: {}", allLis.size());
                for (Element li : allLis) {
                    log.info("li 태그 - class: '{}'", li.className());
                }
            }

            List<ProductInfo> allProducts = new ArrayList<>();
            List<ProductInfo> rankedProducts = new ArrayList<>();
            List<ProductInfo> adProducts = new ArrayList<>();
            List<ProductInfo> normalProducts = new ArrayList<>();

            int displayPosition = 1;  // HTML에서의 실제 위치 (1부터 시작)

            for (Element item : productItems) {
                try {
                    ProductInfo productInfo = parseProductItem(item);
                    productInfo.setDisplayPosition(displayPosition);  // displayPosition 설정
                    displayPosition++;

                    allProducts.add(productInfo);

                    // 상품 분류
                    // 1. 랭킹이 있는 상품
                    if (productInfo.getRanking() != null) {
                        rankedProducts.add(productInfo);
                    }
                    // 2. 광고 상품
                    else if (productInfo.isAd()) {
                        adProducts.add(productInfo);
                    }
                    // 3. 일반 상품 (랭킹 없고 광고도 아님)
                    else {
                        normalProducts.add(productInfo);
                    }
                } catch (Exception e) {
                    log.warn("상품 파싱 실패: {}", e.getMessage());
                    displayPosition++;  // 실패한 상품도 위치는 증가
                    // 개별 상품 파싱 실패 시 스킵하고 계속 진행
                }
            }

            log.info("상품 리스트 추출 완료. 전체: {} 개, 랭킹: {} 개, 광고: {} 개, 일반: {} 개",
                    allProducts.size(), rankedProducts.size(), adProducts.size(), normalProducts.size());
            return ProductListResponse.success(allProducts, rankedProducts, adProducts, normalProducts);

        } catch (Exception e) {
            log.error("상품 리스트 추출 중 오류 발생", e);
            throw new RuntimeException("상품 리스트 추출 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 개별 상품 li 요소에서 상품 정보를 파싱합니다.
     *
     * @param item li 요소
     * @return 파싱된 상품 정보
     */
    private ProductInfo parseProductItem(Element item) {
        ProductInfo.ProductInfoBuilder builder = ProductInfo.builder();

        // 1. vendorItemId (data-id)
        String vendorItemId = item.attr("data-id");
        builder.vendorItemId(vendorItemId);

        // 2. 링크 정보와 itemId, productId 추출
        Element linkElement = item.selectFirst("a");
        if (linkElement != null) {
            String href = linkElement.attr("href");
            builder.productUrl("https://www.coupang.com" + href);

            // URL에서 itemId와 productId 추출
            // URL 형식: /vp/products/8586147830?itemId=24915072273&vendorItemId=...
            try {
                // productId 추출 (URL 경로에서)
                if (href.contains("/products/")) {
                    String[] parts = href.split("/products/");
                    if (parts.length > 1) {
                        String productIdPart = parts[1].split("\\?")[0];
                        builder.productId(productIdPart);
                    }
                }

                // itemId 추출 (쿼리 파라미터에서)
                if (href.contains("itemId=")) {
                    String[] params = href.split("itemId=");
                    if (params.length > 1) {
                        String itemId = params[1].split("&")[0];
                        builder.itemId(itemId);
                    }
                }
            } catch (Exception e) {
                log.warn("URL에서 productId 또는 itemId 추출 실패: {}", href, e);
            }
        }

        // 3. 이미지 URL
        Element imgElement = item.selectFirst("figure.ProductUnit_productImage__Mqcg1 img");
        if (imgElement != null) {
            builder.imageUrl(imgElement.attr("src"));
        }

        // 4. 상품명
        Element nameElement = item.selectFirst("div.ProductUnit_productNameV2__cV9cw");
        if (nameElement != null) {
            builder.productName(nameElement.text());
        }

        // 5. 가격 정보
        Element priceArea = item.selectFirst("div.PriceArea_priceArea__NntJz");
        if (priceArea != null) {
            // 원가
            Element originalPriceElement = priceArea.selectFirst("del");
            if (originalPriceElement != null) {
                builder.originalPrice(originalPriceElement.text());
            }

            // 할인율
            Element discountElement = priceArea.selectFirst("div.fw-mr-\\[2px\\]");
            if (discountElement != null) {
                builder.discountRate(discountElement.text());
            }

            // 현재가
            Element currentPriceElement = priceArea.selectFirst("div.fw-text-\\[20px\\]\\/\\[24px\\]");
            if (currentPriceElement != null) {
                builder.currentPrice(currentPriceElement.text());
            }

            // 개당 가격
            Element unitPriceElement = priceArea.selectFirst("span.fw-text-\\[12px\\]");
            if (unitPriceElement != null) {
                builder.pricePerUnit(unitPriceElement.text());
            }
        }

        // 6. 배송 정보 및 판매방법
        Elements deliveryElements = item.select("div.fw-text-\\[14px\\] > div");
        DeliveryMethod deliveryMethod = DeliveryMethod.NORMAL_DELIVERY; // 기본값: 일반배송

        if (deliveryElements.size() > 0) {
            // 배송 도착일
            Element deliveryDateElement = deliveryElements.get(0);
            if (deliveryDateElement != null) {
                String deliveryText = deliveryDateElement.text();
                builder.deliveryInfo(deliveryText);

                // 로켓 배송 확인
                Element rocketBadge = deliveryDateElement.selectFirst("img[alt*=rocket]");
                builder.rocketDelivery(rocketBadge != null ? "Y" : "N");

                // 판매방법 판별
                Element rocketMerchantImg = deliveryDateElement.selectFirst("img[src*=logoRocketMerchantLargeV3R3]");
                Element rocketDeliveryImg = deliveryDateElement.selectFirst("img[src*=logo_rocket_large]");

                if (rocketMerchantImg != null) {
                    deliveryMethod = DeliveryMethod.ROCKET_MERCHANT;
                    log.debug("판매방법: 판매자로켓");
                } else if (rocketDeliveryImg != null) {
                    deliveryMethod = DeliveryMethod.ROCKET_DELIVERY;
                    log.debug("판매방법: 로켓배송");
                } else {
                    deliveryMethod = DeliveryMethod.NORMAL_DELIVERY;
                    log.debug("판매방법: 일반배송");
                }
            }

            // 배송비
            if (deliveryElements.size() > 1) {
                Element deliveryCostElement = deliveryElements.get(1);
                if (deliveryCostElement != null) {
                    builder.deliveryCost(deliveryCostElement.text());
                }
            }
        }

        builder.deliveryMethod(deliveryMethod);

        // 7. 평점 및 리뷰 수
        Element ratingElement = item.selectFirst("div.ProductRating_productRating__jjf7W");
        if (ratingElement != null) {
            // 평점
            Element starElement = ratingElement.selectFirst("div.ProductRating_star__RGSlV");
            if (starElement != null) {
                builder.rating(starElement.attr("style").replaceAll(".*width:(\\d+)%.*", "$1") + "%");
            }

            // 리뷰 수
            Element reviewCountElement = ratingElement.selectFirst("span.ProductRating_ratingCount__R0Vhz");
            if (reviewCountElement != null) {
                builder.reviewCount(reviewCountElement.text());
            }
        }

        // 8. 적립금 정보
        Element cashbackElement = item.selectFirst("div.BenefitBadge_cash-benefit__SmkrN span");
        if (cashbackElement != null) {
            builder.cashbackAmount(cashbackElement.text());
        }

        // 9. 광고 여부
        Element adElement = item.selectFirst("div.AdMark_adMark__KPMsC");
        builder.isAd(adElement != null);

        // 10. 랭킹 추출 (RankMark_rank로 시작하는 class를 가진 span 요소)
        Element rankElement = item.selectFirst("span[class^=RankMark_rank]");
        if (rankElement != null) {
            try {
                String rankText = rankElement.text().trim();
                Integer rank = Integer.parseInt(rankText);
                builder.ranking(rank);
                log.debug("랭킹 추출: {}", rank);
            } catch (NumberFormatException e) {
                log.warn("랭킹 파싱 실패: {}", rankElement.text());
                builder.ranking(null);
            }
        } else {
            builder.ranking(null);
        }

        return builder.build();
    }
}
