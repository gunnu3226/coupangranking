package gunnu.coupang.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductListResponse {
    private boolean success;
    private List<ProductInfo> allProducts;          // 전체 상품 리스트
    private List<ProductInfo> rankedProducts;       // 랭킹이 있는 상품 리스트
    private List<ProductInfo> adProducts;           // 광고 상품 리스트
    private List<ProductInfo> normalProducts;       // 일반 상품 리스트 (랭킹 없고 광고 아님)
    private int totalCount;                         // 전체 상품 수
    private int rankedCount;                        // 랭킹 상품 수
    private int adCount;                            // 광고 상품 수
    private int normalCount;                        // 일반 상품 수
    private String message;

    public static ProductListResponse success(List<ProductInfo> allProducts,
                                             List<ProductInfo> rankedProducts,
                                             List<ProductInfo> adProducts,
                                             List<ProductInfo> normalProducts) {
        return ProductListResponse.builder()
                .success(true)
                .allProducts(allProducts)
                .rankedProducts(rankedProducts)
                .adProducts(adProducts)
                .normalProducts(normalProducts)
                .totalCount(allProducts.size())
                .rankedCount(rankedProducts.size())
                .adCount(adProducts.size())
                .normalCount(normalProducts.size())
                .message("상품 리스트 추출 성공")
                .build();
    }

    public static ProductListResponse fail(String message) {
        return ProductListResponse.builder()
                .success(false)
                .totalCount(0)
                .rankedCount(0)
                .adCount(0)
                .normalCount(0)
                .message(message)
                .build();
    }
}
