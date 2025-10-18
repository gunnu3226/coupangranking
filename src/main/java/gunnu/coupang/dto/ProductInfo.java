package gunnu.coupang.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductInfo {
    private String productId;           // data-id 속성
    private String productName;         // 상품명
    private String productUrl;          // 상품 링크
    private String imageUrl;            // 이미지 URL
    private String originalPrice;       // 원가
    private String discountRate;        // 할인율
    private String currentPrice;        // 현재가
    private String pricePerUnit;        // 개당 가격
    private String deliveryInfo;        // 배송 정보 (예: 내일(일) 도착 보장)
    private String deliveryCost;        // 배송비 정보 (예: 무료배송)
    private String rating;              // 평점
    private String reviewCount;         // 리뷰 수
    private String cashbackAmount;      // 적립금 정보
    private boolean isAd;               // 광고 상품 여부
    private String rocketDelivery;      // 로켓 배송 여부
    private Integer ranking;            // 랭킹 (1, 2, 3... 없으면 null)
}
