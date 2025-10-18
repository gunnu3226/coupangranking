package gunnu.coupang.controller;

import gunnu.coupang.dto.ProductInfo;
import gunnu.coupang.dto.ProductListResponse;
import gunnu.coupang.dto.SavedProductData;
import gunnu.coupang.entity.Category;
import gunnu.coupang.entity.Company;
import gunnu.coupang.service.HtmlParserService;
import gunnu.coupang.service.ProductDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ViewController {

    private final HtmlParserService htmlParserService;
    private final ProductDataService productDataService;

    /**
     * 메인 페이지 (HTML 입력 폼)
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /**
     * 저장된 데이터 보기 페이지 (공유기 카테고리)
     */
    @GetMapping("/saved-data")
    public String savedData(@RequestParam(value = "date", required = false) String dateStr, Model model) {
        // 날짜 파싱 (null이면 오늘 날짜)
        LocalDate selectedDate = null;
        if (dateStr != null && !dateStr.isEmpty()) {
            try {
                selectedDate = LocalDate.parse(dateStr);
            } catch (Exception e) {
                log.error("잘못된 날짜 형식: {}", dateStr, e);
            }
        }

        // 공유기 카테고리의 각 회사별 데이터 조회
        List<SavedProductData> iptimeProducts = productDataService
                .getProductDataByCategoryAndCompany(Category.ROUTER, Company.IPTIME, selectedDate);
        List<SavedProductData> tplinkProducts = productDataService
                .getProductDataByCategoryAndCompany(Category.ROUTER, Company.TPLINK, selectedDate);
        List<SavedProductData> netisProducts = productDataService
                .getProductDataByCategoryAndCompany(Category.ROUTER, Company.NETIS, selectedDate);
        List<SavedProductData> mercusysProducts = productDataService
                .getProductDataByCategoryAndCompany(Category.ROUTER, Company.MERCUSYS, selectedDate);
        List<SavedProductData> asusProducts = productDataService
                .getProductDataByCategoryAndCompany(Category.ROUTER, Company.ASUS, selectedDate);

        // 저장된 날짜 목록 조회
        List<LocalDate> availableDates = productDataService.getAvailableDates();

        model.addAttribute("iptimeProducts", iptimeProducts);
        model.addAttribute("tplinkProducts", tplinkProducts);
        model.addAttribute("netisProducts", netisProducts);
        model.addAttribute("mercusysProducts", mercusysProducts);
        model.addAttribute("asusProducts", asusProducts);
        model.addAttribute("availableDates", availableDates);
        model.addAttribute("selectedDate", selectedDate != null ? selectedDate : LocalDate.now(java.time.ZoneId.of("Asia/Seoul")));

        int totalCount = iptimeProducts.size() + tplinkProducts.size() + netisProducts.size()
                + mercusysProducts.size() + asusProducts.size();
        model.addAttribute("totalCount", totalCount);

        return "saved-data";
    }

    /**
     * HTML 파싱 결과 페이지 (여러 페이지 텍스트 입력)
     */
    @PostMapping(value = "/parse", consumes = "application/json")
    public String parseHtml(
            @RequestBody List<String> htmlPages,
            Model model) {
        try {
            List<String> htmlContents = new ArrayList<>();

            // 텍스트 입력 처리
            if (htmlPages != null && !htmlPages.isEmpty()) {
                log.info("텍스트 입력 요청 - 페이지 개수: {}", htmlPages.size());
                for (int i = 0; i < htmlPages.size(); i++) {
                    String htmlPage = htmlPages.get(i);
                    if (htmlPage != null && !htmlPage.trim().isEmpty()) {
                        log.info("페이지 {} 추가 - 크기: {} bytes", i + 1, htmlPage.length());
                        htmlContents.add(htmlPage);
                    } else {
                        log.debug("페이지 {} 스킵 (빈 내용)", i + 1);
                    }
                }
            } else {
                log.warn("htmlPages가 null 또는 empty");
            }

            // 입력이 없는 경우
            if (htmlContents.isEmpty()) {
                log.error("파싱할 HTML 컨텐츠가 없음");
                model.addAttribute("success", false);
                model.addAttribute("message", "HTML 텍스트를 입력해주세요.");
                return "result";
            }

            log.info("총 {}개 페이지의 HTML을 파싱합니다.", htmlContents.size());

            // 모든 페이지의 상품을 합침
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

            // 합쳐진 전체 상품 데이터를 데이터베이스에 저장 (순위는 전체 기준으로 재계산)
            if (!allProducts.isEmpty()) {
                productDataService.saveProductData(allProducts);
            }

            // 상품명 기준으로 정렬: 티피링크 -> ipTIME -> 나머지
            List<ProductInfo> sortedProducts = sortProductsByBrand(allProducts);

            // 브랜드별 분리
            List<ProductInfo> tpLinkProducts = filterByBrand(allProducts, "tplink");
            List<ProductInfo> ipTimeProducts = filterByBrand(allProducts, "iptime");

            // 타입별 분리
            List<ProductInfo> rankedProducts = allProducts.stream()
                    .filter(p -> p.getRanking() != null)
                    .collect(Collectors.toList());
            List<ProductInfo> adProducts = allProducts.stream()
                    .filter(ProductInfo::isAd)
                    .collect(Collectors.toList());
            List<ProductInfo> normalProducts = allProducts.stream()
                    .filter(p -> p.getRanking() == null && !p.isAd())
                    .collect(Collectors.toList());

            model.addAttribute("success", true);
            model.addAttribute("allProducts", sortedProducts);
            model.addAttribute("tpLinkProducts", tpLinkProducts);
            model.addAttribute("ipTimeProducts", ipTimeProducts);
            model.addAttribute("rankedProducts", rankedProducts);
            model.addAttribute("adProducts", adProducts);
            model.addAttribute("normalProducts", normalProducts);
            model.addAttribute("totalCount", allProducts.size());
            model.addAttribute("rankedCount", totalRankedCount);
            model.addAttribute("adCount", totalAdCount);
            model.addAttribute("normalCount", totalNormalCount);
            model.addAttribute("message", htmlContents.size() + "개 페이지에서 총 " + allProducts.size() + "개의 상품을 추출했습니다.");

            return "result";
        } catch (Exception e) {
            log.error("HTML 파싱 중 오류 발생", e);
            model.addAttribute("success", false);
            model.addAttribute("message", "오류 발생: " + e.getMessage());
            return "result";
        }
    }

    /**
     * 상품명 기준으로 정렬: 티피링크 -> ipTIME -> 나머지
     */
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

    /**
     * 특정 브랜드 상품만 필터링
     */
    private List<ProductInfo> filterByBrand(List<ProductInfo> products, String brand) {
        if (products == null || products.isEmpty()) {
            return new ArrayList<>();
        }

        List<ProductInfo> filteredProducts = new ArrayList<>();
        for (ProductInfo product : products) {
            String productName = product.getProductName();
            if (productName != null) {
                if (brand.equals("tplink")) {
                    if (productName.contains("티피링크") || productName.toLowerCase().contains("tp-link")
                        || productName.toLowerCase().contains("tplink")) {
                        filteredProducts.add(product);
                    }
                } else if (brand.equals("iptime")) {
                    if (productName.contains("ipTIME") || productName.toLowerCase().contains("iptime")) {
                        filteredProducts.add(product);
                    }
                }
            }
        }
        return filteredProducts;
    }
}
