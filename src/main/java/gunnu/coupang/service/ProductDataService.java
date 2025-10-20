package gunnu.coupang.service;

import gunnu.coupang.dto.ProductInfo;
import gunnu.coupang.dto.SavedProductData;
import gunnu.coupang.entity.Category;
import gunnu.coupang.entity.Company;
import gunnu.coupang.entity.Product;
import gunnu.coupang.entity.ProductDailyData;
import gunnu.coupang.repository.ProductDailyDataRepository;
import gunnu.coupang.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductDataService {

    private final ProductRepository productRepository;
    private final ProductDailyDataRepository productDailyDataRepository;

    /**
     * 상품 리스트를 데이터베이스에 저장
     * - 한국 시간 기준 오늘 날짜로 저장
     * - productId가 없으면 새로 생성, 있으면 기존 사용
     * - 같은 날짜에 이미 데이터가 있으면 업데이트
     * - display_ranking: 추출된 원본 ranking 값
     * - ranking: 광고 제외 상품들의 HTML 순서 (1부터 시작)
     */
    @Transactional
    public void saveProductData(List<ProductInfo> products) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        log.info("상품 데이터 저장 시작 - 날짜: {}, 상품 수: {}", today, products.size());

        int newProductCount = 0;
        int updatedCount = 0;
        int newDailyDataCount = 0;

        // 광고가 아닌 상품들의 순서를 계산하기 위한 카운터
        int nonAdRanking = 1;

        for (ProductInfo productInfo : products) {
            try {
                // 1. Product 조회 또는 생성
                Product product = productRepository.findByProductId(productInfo.getProductId())
                        .orElseGet(() -> {
                            log.debug("새로운 상품 생성: {}", productInfo.getProductId());

                            // 회사 판별
                            Company company = determineCompany(productInfo.getProductName());
                            // 카테고리는 기본값 ROUTER (추후 확장 가능)
                            Category category = Category.ROUTER;

                            Product newProduct = Product.builder()
                                    .productId(productInfo.getProductId())
                                    .productName(productInfo.getProductName())
                                    .company(company)
                                    .category(category)
                                    .build();
                            return productRepository.save(newProduct);
                        });

                if (product.getCreatedAt() == product.getUpdatedAt()) {
                    newProductCount++;
                }

                // 2. 상품명 업데이트 (변경된 경우)
                if (!product.getProductName().equals(productInfo.getProductName())) {
                    product.setProductName(productInfo.getProductName());
                    productRepository.save(product);
                    updatedCount++;
                }

                // 3. ProductDailyData 조회 또는 생성
                ProductDailyData dailyData = productDailyDataRepository
                        .findByProductAndDate(product, today)
                        .orElseGet(() -> {
                            log.debug("새로운 일일 데이터 생성: {} - {}", productInfo.getProductId(), today);
                            return ProductDailyData.builder()
                                    .product(product)
                                    .date(today)
                                    .build();
                        });

                if (dailyData.getId() == null) {
                    newDailyDataCount++;
                }

                // 4. 일일 데이터 업데이트
                // display_ranking: 원본 ranking 값 저장
                dailyData.setDisplay_ranking(productInfo.getRanking());

                // ranking: 광고가 아닌 경우에만 순서대로 저장
                if (!productInfo.isAd()) {
                    dailyData.setRanking(nonAdRanking);
                    nonAdRanking++;
                } else {
                    dailyData.setRanking(null);
                }

                dailyData.setIsAd(productInfo.isAd());

                // 가격에서 "원" 제거
                dailyData.setCurrentPrice(removeWon(productInfo.getCurrentPrice()));
                dailyData.setOriginalPrice(removeWon(productInfo.getOriginalPrice()));
                dailyData.setDiscountRate(productInfo.getDiscountRate());
                dailyData.setRating(productInfo.getRating());

                // 댓글 수에서 괄호 제거
                dailyData.setReviewCount(removeParentheses(productInfo.getReviewCount()));

                productDailyDataRepository.save(dailyData);

            } catch (Exception e) {
                log.error("상품 데이터 저장 실패: {}", productInfo.getProductId(), e);
            }
        }

        log.info("상품 데이터 저장 완료 - 신규 상품: {}, 업데이트: {}, 신규 일일 데이터: {}",
                newProductCount, updatedCount, newDailyDataCount);
    }

    /**
     * 가격에서 "원" 제거
     */
    private String removeWon(String price) {
        if (price == null) {
            return null;
        }
        return price.replace("원", "").trim();
    }

    /**
     * 댓글 수에서 괄호 제거
     */
    private String removeParentheses(String reviewCount) {
        if (reviewCount == null) {
            return null;
        }
        return reviewCount.replace("(", "").replace(")", "").trim();
    }

    /**
     * 상품명으로 회사 판별 (대소문자 구분 없음)
     */
    private Company determineCompany(String productName) {
        if (productName == null) {
            return null;
        }

        String lowerName = productName.toLowerCase();

        if (lowerName.contains("iptime")) {
            return Company.IPTIME;
        } else if (lowerName.contains("네티스") || lowerName.contains("netis")) {
            return Company.NETIS;
        } else if (lowerName.contains("티피링크") || lowerName.contains("tp-link") || lowerName.contains("tplink")) {
            return Company.TPLINK;
        } else if (lowerName.contains("머큐시스") || lowerName.contains("mercusys")) {
            return Company.MERCUSYS;
        } else if (lowerName.contains("에이수스") || lowerName.contains("asus")) {
            return Company.ASUS;
        }

        return null;
    }

    /**
     * 특정 날짜의 저장된 데이터 조회 (특정 카테고리와 회사)
     */
    @Transactional(readOnly = true)
    public List<SavedProductData> getProductDataByCategoryAndCompany(Category category, Company company, LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now(ZoneId.of("Asia/Seoul"));
        log.info("저장된 데이터 조회 - 날짜: {}, 카테고리: {}, 회사: {}", targetDate, category, company);

        List<ProductDailyData> dailyDataList = productDailyDataRepository.findAll();

        return dailyDataList.stream()
                .filter(data -> data.getDate().equals(targetDate))
                .filter(data -> data.getProduct().getCategory() == category)
                .filter(data -> data.getProduct().getCompany() == company)
                .map(data -> SavedProductData.builder()
                        .productId(data.getProduct().getProductId())
                        .productName(data.getProduct().getProductName())
                        .date(data.getDate())
                        .ranking(data.getRanking())
                        .currentPrice(data.getCurrentPrice())
                        .reviewCount(data.getReviewCount())
                        .isAd(data.getIsAd())
                        .build())
                .sorted((a, b) -> {
                    // ranking이 있는 것 우선 정렬
                    if (a.getRanking() != null && b.getRanking() != null) {
                        return a.getRanking().compareTo(b.getRanking());
                    } else if (a.getRanking() != null) {
                        return -1;
                    } else if (b.getRanking() != null) {
                        return 1;
                    }
                    return 0;
                })
                .collect(Collectors.toList());
    }

    /**
     * 저장된 날짜 목록 조회
     */
    @Transactional(readOnly = true)
    public List<LocalDate> getAvailableDates() {
        List<ProductDailyData> dailyDataList = productDailyDataRepository.findAll();
        return dailyDataList.stream()
                .map(ProductDailyData::getDate)
                .distinct()
                .sorted((a, b) -> b.compareTo(a)) // 최신 날짜 우선
                .collect(Collectors.toList());
    }

    /**
     * 광고를 제외한 전체 상품 조회 (회사 구분 없이)
     */
    @Transactional(readOnly = true)
    public List<SavedProductData> getAllNonAdProducts(Category category, LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now(ZoneId.of("Asia/Seoul"));
        log.info("광고 제외 전체 데이터 조회 - 날짜: {}, 카테고리: {}", targetDate, category);

        List<ProductDailyData> dailyDataList = productDailyDataRepository.findAll();

        return dailyDataList.stream()
                .filter(data -> data.getDate().equals(targetDate))
                .filter(data -> data.getProduct().getCategory() == category)
                .filter(data -> !data.getIsAd()) // 광고 제외
                .map(data -> SavedProductData.builder()
                        .productId(data.getProduct().getProductId())
                        .productName(data.getProduct().getProductName())
                        .date(data.getDate())
                        .ranking(data.getRanking())
                        .currentPrice(data.getCurrentPrice())
                        .reviewCount(data.getReviewCount())
                        .isAd(data.getIsAd())
                        .build())
                .sorted((a, b) -> {
                    // ranking으로 정렬
                    if (a.getRanking() != null && b.getRanking() != null) {
                        return a.getRanking().compareTo(b.getRanking());
                    } else if (a.getRanking() != null) {
                        return -1;
                    } else if (b.getRanking() != null) {
                        return 1;
                    }
                    return 0;
                })
                .collect(Collectors.toList());
    }
}
