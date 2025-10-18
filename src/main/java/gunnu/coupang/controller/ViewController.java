package gunnu.coupang.controller;

import gunnu.coupang.dto.ProductListResponse;
import gunnu.coupang.service.HtmlParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ViewController {

    private final HtmlParserService htmlParserService;

    /**
     * 메인 페이지 (HTML 입력 폼)
     */
    @GetMapping("/")
    public String index() {
        return "index";
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

            model.addAttribute("success", response.isSuccess());
            model.addAttribute("allProducts", response.getAllProducts());
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
}
