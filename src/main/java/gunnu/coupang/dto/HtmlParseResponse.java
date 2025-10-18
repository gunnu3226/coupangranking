package gunnu.coupang.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HtmlParseResponse {
    private boolean success;
    private String extractedHtml;
    private String message;

    public static HtmlParseResponse success(String extractedHtml) {
        return new HtmlParseResponse(true, extractedHtml, "HTML 추출 성공");
    }

    public static HtmlParseResponse fail(String message) {
        return new HtmlParseResponse(false, null, message);
    }
}
