package gunnu.coupang.controller;

import gunnu.coupang.dto.ProductInfo;
import gunnu.coupang.dto.ProductListResponse;
import gunnu.coupang.dto.SavedProductData;
import gunnu.coupang.entity.Category;
import gunnu.coupang.entity.Company;
import gunnu.coupang.entity.FavoriteProduct;
import gunnu.coupang.entity.Product;
import gunnu.coupang.service.FavoriteProductService;
import gunnu.coupang.service.HtmlParserService;
import gunnu.coupang.service.ProductDataService;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppApiController {

    private final HtmlParserService htmlParserService;
    private final ProductDataService productDataService;
    private final FavoriteProductService favoriteProductService;

    @PostMapping("/parse")
    public ResponseEntity<ParseResultResponse> parseHtml(@RequestBody List<String> htmlPages) {
        try {
            List<String> htmlContents = collectNonBlankPages(htmlPages);
            if (htmlContents.isEmpty()) {
                return ResponseEntity.badRequest().body(ParseResultResponse.fail("HTML 텍스트를 입력해주세요."));
            }

            List<ProductInfo> allProducts = new ArrayList<>();
            int totalRankedCount = 0;
            int totalAdCount = 0;
            int totalNormalCount = 0;

            for (int i = 0; i < htmlContents.size(); i++) {
                log.info("페이지 {} 파싱 중...", i + 1);
                ProductListResponse pageResponse = htmlParserService.extractProductList(htmlContents.get(i));
                if (pageResponse.isSuccess() && pageResponse.getAllProducts() != null) {
                    allProducts.addAll(pageResponse.getAllProducts());
                    totalRankedCount += pageResponse.getRankedCount();
                    totalAdCount += pageResponse.getAdCount();
                    totalNormalCount += pageResponse.getNormalCount();
                }
            }

            if (!allProducts.isEmpty()) {
                productDataService.saveProductData(allProducts);
            }

            return ResponseEntity.ok(buildParseResponse(
                    allProducts,
                    totalRankedCount,
                    totalAdCount,
                    totalNormalCount,
                    htmlContents.size() + "개 페이지에서 총 " + allProducts.size() + "개의 상품을 추출했습니다.",
                    false
            ));
        } catch (Exception e) {
            log.error("HTML 파싱 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ParseResultResponse.fail("오류 발생: " + e.getMessage()));
        }
    }

    @GetMapping("/today-result")
    public ResponseEntity<ParseResultResponse> todayResult() {
        try {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            List<ProductInfo> allProducts = productDataService.getTodayProductsAsProductInfo(today);

            if (allProducts.isEmpty()) {
                return ResponseEntity.ok(ParseResultResponse.fail("오늘 저장된 데이터가 없습니다."));
            }

            List<ProductInfo> rankedProducts = allProducts.stream()
                    .filter(product -> product.getRanking() != null)
                    .collect(Collectors.toList());
            List<ProductInfo> adProducts = allProducts.stream()
                    .filter(ProductInfo::isAd)
                    .collect(Collectors.toList());
            List<ProductInfo> normalProducts = allProducts.stream()
                    .filter(product -> product.getRanking() == null && !product.isAd())
                    .collect(Collectors.toList());

            return ResponseEntity.ok(buildParseResponse(
                    allProducts,
                    rankedProducts.size(),
                    adProducts.size(),
                    normalProducts.size(),
                    "오늘 저장된 총 " + allProducts.size() + "개의 상품 데이터를 표시합니다.",
                    true
            ));
        } catch (Exception e) {
            log.error("당일 데이터 조회 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ParseResultResponse.fail("오류 발생: " + e.getMessage()));
        }
    }

    @GetMapping("/products")
    public ProductManagementResponse products(@RequestParam(value = "category", defaultValue = "ROUTER") Category category) {
        List<Product> products = productDataService.getProductsByCategory(category);
        Set<Long> favoriteProductIds = favoriteProductService.getFavoriteProductIds();
        List<ProductSummary> summaries = products.stream()
                .map(this::toProductSummary)
                .collect(Collectors.toList());

        return ProductManagementResponse.builder()
                .allProducts(summaries)
                .productsByCompany(groupProductsByCompany(summaries))
                .favoriteProductIds(favoriteProductIds)
                .totalCount(summaries.size())
                .build();
    }

    @GetMapping("/saved-data")
    public SavedDataResponse savedData(@RequestParam(value = "date", required = false) String dateStr) {
        LocalDate selectedDate = parseDateOrNull(dateStr);
        LocalDate effectiveDate = selectedDate != null ? selectedDate : LocalDate.now(ZoneId.of("Asia/Seoul"));

        List<SavedProductData> allSavedProducts = productDataService
                .getAllSavedProductsByCategory(Category.ROUTER, selectedDate);
        List<SavedProductData> allNonAdProducts = productDataService
                .getAllNonAdProducts(Category.ROUTER, selectedDate);
        List<LocalDate> availableDates = productDataService.getAvailableDates();
        Set<Long> favoriteProductIds = favoriteProductService.getFavoriteProductIds();

        Map<Long, Integer> displayOrderMap = new HashMap<>();
        List<FavoriteProduct> favoriteProducts = favoriteProductService.getFavoritesOrderedByDisplayOrder();
        for (FavoriteProduct favorite : favoriteProducts) {
            if (favorite.getDisplayOrder() != null) {
                displayOrderMap.put(favorite.getProduct().getId(), favorite.getDisplayOrder());
            }
        }

        List<SavedProductData> favoriteAllProducts =
                productDataService.getFavoriteProductsWithData(favoriteProductIds, selectedDate);

        return SavedDataResponse.builder()
                .selectedDate(effectiveDate)
                .availableDates(availableDates)
                .allSavedProducts(allSavedProducts)
                .allNonAdProducts(allNonAdProducts)
                .productsByCompany(groupSavedDataByCompany(allSavedProducts))
                .favoriteAllProducts(favoriteAllProducts)
                .favoriteProductsByCompany(groupSavedDataByCompany(favoriteAllProducts))
                .favoriteProductIds(favoriteProductIds)
                .displayOrderMap(displayOrderMap)
                .totalCount(allSavedProducts.size())
                .build();
    }

    @GetMapping("/favorites/full")
    public FavoriteOrderResponse favoritesFull() {
        List<ProductSummary> products = favoriteProductService.getFavoritesOrderedByDisplayOrder().stream()
                .map(FavoriteProduct::getProduct)
                .map(this::toProductSummary)
                .collect(Collectors.toList());

        return FavoriteOrderResponse.builder()
                .products(products)
                .totalCount(products.size())
                .build();
    }

    private List<String> collectNonBlankPages(List<String> htmlPages) {
        List<String> htmlContents = new ArrayList<>();
        if (htmlPages == null) {
            return htmlContents;
        }
        for (String htmlPage : htmlPages) {
            if (htmlPage != null && !htmlPage.trim().isEmpty()) {
                htmlContents.add(htmlPage);
            }
        }
        return htmlContents;
    }

    private ParseResultResponse buildParseResponse(List<ProductInfo> allProducts,
                                                   int rankedCount,
                                                   int adCount,
                                                   int normalCount,
                                                   String message,
                                                   boolean today) {
        List<ProductInfo> sortedProducts = sortProductsByBrand(allProducts);
        List<ProductInfo> tpLinkProducts = filterByBrand(allProducts, "tplink");
        List<ProductInfo> ipTimeProducts = filterByBrand(allProducts, "iptime");
        List<ProductInfo> rankedProducts = allProducts.stream()
                .filter(product -> product.getRanking() != null)
                .collect(Collectors.toList());
        List<ProductInfo> adProducts = allProducts.stream()
                .filter(ProductInfo::isAd)
                .collect(Collectors.toList());
        List<ProductInfo> normalProducts = allProducts.stream()
                .filter(product -> product.getRanking() == null && !product.isAd())
                .collect(Collectors.toList());

        return ParseResultResponse.builder()
                .success(true)
                .allProducts(sortedProducts)
                .tpLinkProducts(tpLinkProducts)
                .ipTimeProducts(ipTimeProducts)
                .rankedProducts(rankedProducts)
                .adProducts(adProducts)
                .normalProducts(normalProducts)
                .totalCount(allProducts.size())
                .rankedCount(rankedCount)
                .adCount(adCount)
                .normalCount(normalCount)
                .message(message)
                .today(today)
                .build();
    }

    private ProductSummary toProductSummary(Product product) {
        return ProductSummary.builder()
                .id(product.getId())
                .itemId(product.getItemId())
                .productId(product.getProductId())
                .productName(product.getProductName())
                .modelCode(product.getModelCode())
                .category(product.getCategory())
                .company(product.getCompany())
                .displayOrder(product.getDisplayOrder())
                .build();
    }

    private Map<Company, List<ProductSummary>> groupProductsByCompany(List<ProductSummary> products) {
        Map<Company, List<ProductSummary>> grouped = new EnumMap<>(Company.class);
        for (Company company : Company.values()) {
            grouped.put(company, products.stream()
                    .filter(product -> product.getCompany() == company)
                    .collect(Collectors.toList()));
        }
        return grouped;
    }

    private Map<Company, List<SavedProductData>> groupSavedDataByCompany(List<SavedProductData> products) {
        Map<Company, List<SavedProductData>> grouped = new EnumMap<>(Company.class);
        for (Company company : Company.values()) {
            grouped.put(company, products.stream()
                    .filter(product -> product.getCompany() == company)
                    .collect(Collectors.toList()));
        }
        return grouped;
    }

    private LocalDate parseDateOrNull(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            log.warn("잘못된 날짜 형식: {}", dateStr, e);
            return null;
        }
    }

    private List<ProductInfo> sortProductsByBrand(List<ProductInfo> products) {
        if (products == null || products.isEmpty()) {
            return products;
        }

        List<ProductInfo> tpLinkProducts = new ArrayList<>();
        List<ProductInfo> ipTimeProducts = new ArrayList<>();
        List<ProductInfo> otherProducts = new ArrayList<>();

        for (ProductInfo product : products) {
            String productName = product.getProductName();
            if (productName != null) {
                if (productName.contains("티피링크") || productName.toLowerCase().contains("tp-link")
                        || productName.toLowerCase().contains("tplink")) {
                    tpLinkProducts.add(product);
                } else if (productName.contains("ipTIME") || productName.toLowerCase().contains("iptime")) {
                    ipTimeProducts.add(product);
                } else {
                    otherProducts.add(product);
                }
            } else {
                otherProducts.add(product);
            }
        }

        List<ProductInfo> sortedProducts = new ArrayList<>();
        sortedProducts.addAll(tpLinkProducts);
        sortedProducts.addAll(ipTimeProducts);
        sortedProducts.addAll(otherProducts);
        return sortedProducts;
    }

    private List<ProductInfo> filterByBrand(List<ProductInfo> products, String brand) {
        if (products == null || products.isEmpty()) {
            return new ArrayList<>();
        }

        List<ProductInfo> filteredProducts = new ArrayList<>();
        for (ProductInfo product : products) {
            String productName = product.getProductName();
            if (productName == null) {
                continue;
            }
            String lowerName = productName.toLowerCase();
            if ("tplink".equals(brand)
                    && (productName.contains("티피링크") || lowerName.contains("tp-link") || lowerName.contains("tplink"))) {
                filteredProducts.add(product);
            } else if ("iptime".equals(brand)
                    && (productName.contains("ipTIME") || lowerName.contains("iptime"))) {
                filteredProducts.add(product);
            }
        }
        return filteredProducts;
    }

    @Getter
    @Builder
    public static class ParseResultResponse {
        private boolean success;
        private List<ProductInfo> allProducts;
        private List<ProductInfo> tpLinkProducts;
        private List<ProductInfo> ipTimeProducts;
        private List<ProductInfo> rankedProducts;
        private List<ProductInfo> adProducts;
        private List<ProductInfo> normalProducts;
        private int totalCount;
        private int rankedCount;
        private int adCount;
        private int normalCount;
        private String message;
        private boolean today;

        public static ParseResultResponse fail(String message) {
            return ParseResultResponse.builder()
                    .success(false)
                    .allProducts(List.of())
                    .tpLinkProducts(List.of())
                    .ipTimeProducts(List.of())
                    .rankedProducts(List.of())
                    .adProducts(List.of())
                    .normalProducts(List.of())
                    .message(message)
                    .build();
        }
    }

    @Getter
    @Builder
    public static class ProductManagementResponse {
        private List<ProductSummary> allProducts;
        private Map<Company, List<ProductSummary>> productsByCompany;
        private Set<Long> favoriteProductIds;
        private int totalCount;
    }

    @Getter
    @Builder
    public static class SavedDataResponse {
        private LocalDate selectedDate;
        private List<LocalDate> availableDates;
        private List<SavedProductData> allSavedProducts;
        private List<SavedProductData> allNonAdProducts;
        private Map<Company, List<SavedProductData>> productsByCompany;
        private List<SavedProductData> favoriteAllProducts;
        private Map<Company, List<SavedProductData>> favoriteProductsByCompany;
        private Set<Long> favoriteProductIds;
        private Map<Long, Integer> displayOrderMap;
        private int totalCount;
    }

    @Getter
    @Builder
    public static class FavoriteOrderResponse {
        private List<ProductSummary> products;
        private int totalCount;
    }

    @Getter
    @Builder
    public static class ProductSummary {
        private Long id;
        private String itemId;
        private String productId;
        private String productName;
        private String modelCode;
        private Category category;
        private Company company;
        private Integer displayOrder;
    }
}
