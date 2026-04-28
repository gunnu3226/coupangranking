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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                // itemId와 productId가 없으면 스킵
                if (productInfo.getItemId() == null || productInfo.getProductId() == null) {
                    log.warn("itemId 또는 productId가 없어서 스킵: {}", productInfo.getProductName());
                    continue;
                }

                // 1. Product 조회 또는 생성
                Product product = productRepository.findByItemIdAndProductId(
                        productInfo.getItemId(),
                        productInfo.getProductId())
                        .orElseGet(() -> {
                            log.debug("새로운 상품 생성: itemId={}, productId={}",
                                    productInfo.getItemId(), productInfo.getProductId());

                            // 회사 판별
                            Company company = determineCompany(productInfo.getProductName());
                            // 카테고리는 기본값 ROUTER (추후 확장 가능)
                            Category category = Category.ROUTER;

                            Product newProduct = Product.builder()
                                    .itemId(productInfo.getItemId())
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

                // 3. ProductDailyData 생성 (중복도 모두 저장)
                // 같은 상품이 광고와 순위 양쪽에 있는 경우를 위해 항상 새로운 레코드 생성
                ProductDailyData dailyData = ProductDailyData.builder()
                        .product(product)
                        .date(today)
                        .displayPosition(productInfo.getDisplayPosition())
                        .build();

                newDailyDataCount++;

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
                dailyData.setDeliveryMethod(productInfo.getDeliveryMethod());

                // 가격에서 "원" 제거
                dailyData.setCurrentPrice(removeWon(productInfo.getCurrentPrice()));
                dailyData.setOriginalPrice(removeWon(productInfo.getOriginalPrice()));
                dailyData.setDiscountRate(productInfo.getDiscountRate());
                dailyData.setRating(productInfo.getRating());

                // 댓글 수에서 괄호 제거
                dailyData.setReviewCount(removeParentheses(productInfo.getReviewCount()));

                // 상품 URL 저장
                dailyData.setProductUrl(productInfo.getProductUrl());

                productDailyDataRepository.save(dailyData);

            } catch (Exception e) {
                log.error("상품 데이터 저장 실패: itemId={}, productId={}",
                        productInfo.getItemId(), productInfo.getProductId(), e);
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

        List<ProductDailyData> dailyDataList = productDailyDataRepository
                .findByDateAndProduct_CategoryAndProduct_Company(targetDate, category, company);

        return latestByItemIdAndAdState(dailyDataList).stream()
                .map(this::toSavedProductData)
                .sorted(savedProductRankingComparator())
                .collect(Collectors.toList());
    }

    /**
     * 저장된 날짜 목록 조회
     */
    @Transactional(readOnly = true)
    public List<LocalDate> getAvailableDates() {
        return productDailyDataRepository.findDistinctDatesOrderByDateDesc();
    }

    /**
     * 전체 상품 목록 조회 (날짜 무관)
     */
    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        log.info("전체 상품 목록 조회");
        return productRepository.findAll();
    }

    /**
     * 카테고리별 전체 상품 목록 조회
     */
    @Transactional(readOnly = true)
    public List<Product> getProductsByCategory(Category category) {
        log.info("카테고리별 상품 목록 조회 - 카테고리: {}", category);
        return productRepository.findAll().stream()
                .filter(product -> product.getCategory() == category)
                .sorted((a, b) -> {
                    // 회사별로 정렬 (null은 마지막으로)
                    if (a.getCompany() == null && b.getCompany() == null) {
                        return 0;
                    }
                    if (a.getCompany() == null) {
                        return 1; // null은 뒤로
                    }
                    if (b.getCompany() == null) {
                        return -1; // null은 뒤로
                    }
                    return a.getCompany().compareTo(b.getCompany());
                })
                .collect(Collectors.toList());
    }

    /**
     * 광고 포함 전체 저장 상품 조회 (회사 구분 없이)
     */
    @Transactional(readOnly = true)
    public List<SavedProductData> getAllSavedProductsByCategory(Category category, LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now(ZoneId.of("Asia/Seoul"));
        log.info("전체 저장 데이터 조회 - 날짜: {}, 카테고리: {}", targetDate, category);

        List<ProductDailyData> dailyDataList = productDailyDataRepository
                .findByDateAndProduct_Category(targetDate, category);

        return latestByItemIdAndAdState(dailyDataList).stream()
                .map(this::toSavedProductData)
                .sorted(savedProductRankingComparator())
                .collect(Collectors.toList());
    }

    /**
     * 광고를 제외한 전체 상품 조회 (회사 구분 없이)
     */
    @Transactional(readOnly = true)
    public List<SavedProductData> getAllNonAdProducts(Category category, LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now(ZoneId.of("Asia/Seoul"));
        log.info("광고 제외 전체 데이터 조회 - 날짜: {}, 카테고리: {}", targetDate, category);

        List<ProductDailyData> dailyDataList = productDailyDataRepository
                .findByDateAndProduct_CategoryAndIsAdFalse(targetDate, category);

        return latestByItemId(dailyDataList).stream()
                .map(this::toSavedProductData)
                .sorted(savedProductRankingComparator())
                .collect(Collectors.toList());
    }

    /**
     * 특정 날짜의 저장된 데이터를 ProductInfo 형식으로 조회
     */
    @Transactional(readOnly = true)
    public List<ProductInfo> getTodayProductsAsProductInfo(LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now(ZoneId.of("Asia/Seoul"));
        log.info("저장된 데이터를 ProductInfo 형식으로 조회 - 날짜: {}", targetDate);

        List<ProductDailyData> dailyDataList = productDailyDataRepository.findAll();

        return dailyDataList.stream()
                .filter(data -> data.getDate().equals(targetDate))
                .filter(data -> data.getProduct() != null)
                .map(data -> ProductInfo.builder()
                        .itemId(data.getProduct().getItemId())
                        .productId(data.getProduct().getProductId())
                        .productName(data.getProduct().getProductName())
                        .productUrl(data.getProductUrl())
                        .currentPrice(data.getCurrentPrice() != null ? data.getCurrentPrice() + "원" : null)
                        .originalPrice(data.getOriginalPrice() != null ? data.getOriginalPrice() + "원" : null)
                        .discountRate(data.getDiscountRate())
                        .rating(data.getRating())
                        .reviewCount(data.getReviewCount() != null ? "(" + data.getReviewCount() + ")" : null)
                        .isAd(data.getIsAd())
                        .ranking(data.getDisplay_ranking())
                        .displayPosition(data.getDisplayPosition())
                        .deliveryMethod(data.getDeliveryMethod())
                        .build())
                .sorted((a, b) -> {
                    // displayPosition으로 정렬 (HTML 순서 유지)
                    if (a.getDisplayPosition() != null && b.getDisplayPosition() != null) {
                        return a.getDisplayPosition().compareTo(b.getDisplayPosition());
                    }
                    return 0;
                })
                .collect(Collectors.toList());
    }

    /**
     * favorite 상품들의 데이터 조회 (데이터가 없는 상품도 포함)
     * @param favoriteProductIds favorite 상품 ID 목록
     * @param date 조회할 날짜
     * @return 상품 정보 리스트 (데이터가 없으면 Product 정보만 포함)
     */
    @Transactional(readOnly = true)
    public List<SavedProductData> getFavoriteProductsWithData(java.util.Set<Long> favoriteProductIds, LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now(ZoneId.of("Asia/Seoul"));
        log.info("favorite 상품 데이터 조회 - 날짜: {}, 상품 수: {}", targetDate, favoriteProductIds.size());

        // favorite 상품들 조회
        List<Product> favoriteProducts = productRepository.findAllById(favoriteProductIds);

        // 해당 날짜의 ProductDailyData 조회 (DB 조건 조회 + Product fetch)
        List<ProductDailyData> dailyDataList = favoriteProductIds.isEmpty()
                ? List.of()
                : productDailyDataRepository.findByDateAndProduct_IdIn(targetDate, favoriteProductIds);

        // Product ID -> 광고/일반 상태별 최신 ProductDailyData 매핑
        // 같은 상품이 광고 영역과 일반 순위 영역에 동시에 노출될 수 있으므로 둘 다 유지한다.
        Map<Long, List<ProductDailyData>> dailyDataMap = latestByProductIdAndAdState(dailyDataList).stream()
                .collect(Collectors.groupingBy(
                        data -> data.getProduct().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // 모든 favorite 상품에 대해 SavedProductData 생성
        return favoriteProducts.stream()
                .flatMap(product -> {
                    List<ProductDailyData> dailyData = dailyDataMap.get(product.getId());

                    if (dailyData == null || dailyData.isEmpty()) {
                        // 데이터가 없는 경우 - Product 정보만 포함
                        return java.util.stream.Stream.of(SavedProductData.builder()
                                .id(product.getId())
                                .itemId(product.getItemId())
                                .productId(product.getProductId())
                                .productName(product.getProductName())
                                .productUrl(null)
                                .company(product.getCompany())
                                .date(null)
                                .ranking(null)
                                .currentPrice(null)
                                .reviewCount(null)
                                .isAd(false)
                                .deliveryMethod(null)
                                .build());
                    }
                    // 데이터가 있는 경우 - 광고/일반 상태별 최신 row를 모두 포함
                    return dailyData.stream().map(this::toSavedProductData);
                })
                .collect(Collectors.toList());
    }

    private List<ProductDailyData> latestByItemId(Collection<ProductDailyData> dailyDataList) {
        Map<String, ProductDailyData> latestByItemId = new LinkedHashMap<>();
        for (ProductDailyData data : dailyDataList) {
            if (data == null || data.getProduct() == null) {
                continue;
            }
            String key = data.getProduct().getItemId();
            if (key == null) {
                key = "product:" + data.getProduct().getId();
            }
            latestByItemId.merge(key, data, this::newerDailyData);
        }
        return List.copyOf(latestByItemId.values());
    }

    private List<ProductDailyData> latestByItemIdAndAdState(Collection<ProductDailyData> dailyDataList) {
        Map<String, ProductDailyData> latestByItemIdAndAdState = new LinkedHashMap<>();
        for (ProductDailyData data : dailyDataList) {
            if (data == null || data.getProduct() == null) {
                continue;
            }
            latestByItemIdAndAdState.merge(itemIdAndAdStateKey(data), data, this::newerDailyData);
        }
        return List.copyOf(latestByItemIdAndAdState.values());
    }

    private List<ProductDailyData> latestByProductIdAndAdState(Collection<ProductDailyData> dailyDataList) {
        Map<String, ProductDailyData> latestByProductIdAndAdState = new LinkedHashMap<>();
        for (ProductDailyData data : dailyDataList) {
            if (data == null || data.getProduct() == null || data.getProduct().getId() == null) {
                continue;
            }
            latestByProductIdAndAdState.merge(productIdAndAdStateKey(data), data, this::newerDailyData);
        }
        return List.copyOf(latestByProductIdAndAdState.values());
    }

    private String itemIdAndAdStateKey(ProductDailyData data) {
        Product product = data.getProduct();
        String itemKey = product.getItemId();
        if (itemKey == null) {
            itemKey = "product:" + product.getId();
        }
        return itemKey + "|ad:" + Boolean.TRUE.equals(data.getIsAd());
    }

    private String productIdAndAdStateKey(ProductDailyData data) {
        return data.getProduct().getId() + "|ad:" + Boolean.TRUE.equals(data.getIsAd());
    }

    private ProductDailyData newerDailyData(ProductDailyData current, ProductDailyData candidate) {
        return compareRecency(candidate, current) > 0 ? candidate : current;
    }

    private int compareRecency(ProductDailyData left, ProductDailyData right) {
        int createdAtComparison = compareNullableCreatedAt(left.getCreatedAt(), right.getCreatedAt());
        if (createdAtComparison != 0) {
            return createdAtComparison;
        }
        return compareNullableLong(left.getId(), right.getId());
    }

    private int compareNullableCreatedAt(LocalDateTime left, LocalDateTime right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private int compareNullableLong(Long left, Long right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private SavedProductData toSavedProductData(ProductDailyData data) {
        Product product = data.getProduct();
        return SavedProductData.builder()
                .id(product.getId())
                .itemId(product.getItemId())
                .productId(product.getProductId())
                .productName(product.getProductName())
                .productUrl(data.getProductUrl())
                .company(product.getCompany())
                .date(data.getDate())
                .ranking(data.getRanking())
                .currentPrice(data.getCurrentPrice())
                .reviewCount(data.getReviewCount())
                .isAd(Boolean.TRUE.equals(data.getIsAd()))
                .deliveryMethod(data.getDeliveryMethod())
                .build();
    }

    private Comparator<SavedProductData> savedProductRankingComparator() {
        return Comparator
                .comparing((SavedProductData data) -> data.getRanking() == null)
                .thenComparing(SavedProductData::getRanking, Comparator.nullsLast(Integer::compareTo));
    }

}
