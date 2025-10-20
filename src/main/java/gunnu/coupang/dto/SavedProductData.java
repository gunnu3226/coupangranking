package gunnu.coupang.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedProductData {
    private String productId;
    private String productName;
    private LocalDate date;
    private Integer ranking;
    private String currentPrice;
    private String reviewCount;
    private boolean isAd;
}
