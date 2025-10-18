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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
     * HTML 파싱 결과 페이지 (텍스트 또는 파일 업로드)
     */
    @PostMapping("/parse")
    public String parseHtml(
            @RequestParam(value = "htmlText", required = false) String htmlText,
            @RequestParam(value = "htmlFile", required = false) MultipartFile htmlFile,
            Model model) {
        try {
            String htmlContent = null;

            // 1. 파일이 업로드된 경우
            if (htmlFile != null && !htmlFile.isEmpty()) {
                log.info("파일 업로드로 HTML 파싱 요청 받음. 파일명: {}, 크기: {} bytes",
                        htmlFile.getOriginalFilename(), htmlFile.getSize());
                htmlContent = new String(htmlFile.getBytes(), StandardCharsets.UTF_8);
            }
            // 2. 텍스트가 입력된 경우
            else if (htmlText != null && !htmlText.trim().isEmpty()) {
                log.info("텍스트 입력으로 HTML 파싱 요청 받음. 크기: {} bytes", htmlText.length());
                htmlContent = htmlText;
            }
            // 3. 둘 다 없는 경우
            else {
                model.addAttribute("success", false);
                model.addAttribute("message", "HTML 텍스트를 입력하거나 파일을 업로드해주세요.");
                return "result";
            }

            ProductListResponse response = htmlParserService.extractProductList(htmlContent);

            // 상품 데이터를 데이터베이스에 저장
            if (response.isSuccess() && response.getAllProducts() != null) {
                productDataService.saveProductData(response.getAllProducts());
            }

            // 상품명 기준으로 정렬: 티피링크 -> ipTIME -> 나머지
            List<ProductInfo> sortedProducts = sortProductsByBrand(response.getAllProducts());

            // 브랜드별 분리
            List<ProductInfo> tpLinkProducts = filterByBrand(response.getAllProducts(), "tplink");
            List<ProductInfo> ipTimeProducts = filterByBrand(response.getAllProducts(), "iptime");

            model.addAttribute("success", response.isSuccess());
            model.addAttribute("allProducts", sortedProducts);
            model.addAttribute("tpLinkProducts", tpLinkProducts);
            model.addAttribute("ipTimeProducts", ipTimeProducts);
            model.addAttribute("rankedProducts", response.getRankedProducts());
            model.addAttribute("adProducts", response.getAdProducts());
            model.addAttribute("normalProducts", response.getNormalProducts());
            model.addAttribute("totalCount", response.getTotalCount());
            model.addAttribute("rankedCount", response.getRankedCount());
            model.addAttribute("adCount", response.getAdCount());
            model.addAttribute("normalCount", response.getNormalCount());
            model.addAttribute("message", response.getMessage());

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
