package gunnu.coupang.controller;

import gunnu.coupang.dto.HtmlParseResponse;
import gunnu.coupang.dto.ProductListResponse;
import gunnu.coupang.service.HtmlParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/html")
@RequiredArgsConstructor
public class HtmlParserController {

    private final HtmlParserService htmlParserService;

    /**
     * HTML 텍스트를 text/plain으로 받아서 상품 리스트를 추출합니다.
     *
     * @param htmlText HTML 텍스트 (text/plain)
     * @return 추출된 상품 리스트
     */
    @PostMapping(value = "/extract-products", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductListResponse> extractProducts(@RequestBody String htmlText) {
        try {
            log.info("상품 리스트 추출 요청 받음. HTML 크기: {} bytes", htmlText.length());
            ProductListResponse response = htmlParserService.extractProductList(htmlText);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("잘못된 요청: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ProductListResponse.fail(e.getMessage()));
        } catch (Exception e) {
            log.error("서버 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProductListResponse.fail("서버 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * HTML 텍스트를 text/plain으로 받아서 section#contents 영역을 추출합니다.
     *
     * @param htmlText HTML 텍스트 (text/plain)
     * @return 추출된 HTML
     */
    @PostMapping(value = "/extract-section", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HtmlParseResponse> extractSection(@RequestBody String htmlText) {
        try {
            log.info("HTML section 추출 요청 받음");
            String extractedHtml = htmlParserService.extractContentsSection(htmlText);
            return ResponseEntity.ok(HtmlParseResponse.success(extractedHtml));
        } catch (IllegalArgumentException e) {
            log.warn("잘못된 요청: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(HtmlParseResponse.fail(e.getMessage()));
        } catch (Exception e) {
            log.error("서버 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(HtmlParseResponse.fail("서버 오류가 발생했습니다."));
        }
    }
}
