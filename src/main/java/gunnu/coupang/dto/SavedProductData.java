package gunnu.coupang.dto;

import gunnu.coupang.entity.DeliveryMethod;
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
    private Long id;  // Product의 PK
    private String itemId;
    private String productId;
    private String productName;
    private String productUrl;
    private LocalDate date;
    private Integer ranking;
    private String currentPrice;
    private String reviewCount;
    private boolean isAd;
    private DeliveryMethod deliveryMethod;
}
