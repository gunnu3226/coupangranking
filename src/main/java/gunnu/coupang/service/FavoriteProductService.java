package gunnu.coupang.service;

import gunnu.coupang.dto.SavedProductData;
import gunnu.coupang.entity.Category;
import gunnu.coupang.entity.Company;
import gunnu.coupang.entity.FavoriteProduct;
import gunnu.coupang.entity.Product;
import gunnu.coupang.repository.FavoriteProductRepository;
import gunnu.coupang.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteProductService {

    private final FavoriteProductRepository favoriteProductRepository;
    private final ProductRepository productRepository;

    /**
     * 선택한 상품들을 저장
     */
    @Transactional
    public void saveFavorites(List<Long> productIds) {
        log.info("선택한 상품 저장 시작 - 상품 수: {}", productIds.size());

        // 기존 선택 항목 모두 삭제
        favoriteProductRepository.deleteAll();
        log.info("기존 선택 항목 삭제 완료");

        // 새로운 선택 항목 저장
        for (Long productId : productIds) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + productId));

            FavoriteProduct favorite = FavoriteProduct.builder()
                    .product(product)
                    .build();

            favoriteProductRepository.save(favorite);
        }

        log.info("선택한 상품 저장 완료 - 저장된 수: {}", productIds.size());
    }

    /**
     * 선택된 상품 ID 목록 조회
     */
    @Transactional(readOnly = true)
    public Set<Long> getFavoriteProductIds() {
        List<FavoriteProduct> favorites = favoriteProductRepository.findAll();
        return favorites.stream()
                .map(fav -> fav.getProduct().getId())
                .collect(Collectors.toSet());
    }

    /**
     * 특정 상품이 선택되었는지 확인
     */
    @Transactional(readOnly = true)
    public boolean isFavorite(Product product) {
        return favoriteProductRepository.existsByProduct(product);
    }

    /**
     * 선택된 상품 개수 조회
     */
    @Transactional(readOnly = true)
    public long getFavoriteCount() {
        return favoriteProductRepository.count();
    }

    /**
     * 선택한 상품들의 표시 순서 업데이트
     * @param orderMap Product ID -> displayOrder 매핑
     */
    @Transactional
    public void updateDisplayOrder(Map<Long, Integer> orderMap) {
        log.info("표시 순서 업데이트 시작 - 상품 수: {}", orderMap.size());

        for (Map.Entry<Long, Integer> entry : orderMap.entrySet()) {
            Long productId = entry.getKey();
            Integer order = entry.getValue();

            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + productId));

            FavoriteProduct favorite = favoriteProductRepository.findByProduct(product)
                    .orElseThrow(() -> new IllegalArgumentException("선택한 상품이 아닙니다: " + productId));

            favorite.setDisplayOrder(order);
            favoriteProductRepository.save(favorite);
        }

        log.info("표시 순서 업데이트 완료");
    }

    /**
     * 선택된 상품 목록 조회 (displayOrder 기준 정렬)
     */
    @Transactional(readOnly = true)
    public List<FavoriteProduct> getFavoritesOrderedByDisplayOrder() {
        List<FavoriteProduct> favorites = favoriteProductRepository.findAll();
        return favorites.stream()
                .sorted((a, b) -> {
                    if (a.getDisplayOrder() == null && b.getDisplayOrder() == null) {
                        return 0;
                    } else if (a.getDisplayOrder() == null) {
                        return 1; // null은 뒤로
                    } else if (b.getDisplayOrder() == null) {
                        return -1; // null은 뒤로
                    }
                    return a.getDisplayOrder().compareTo(b.getDisplayOrder());
                })
                .collect(Collectors.toList());
    }

    /**
     * 선택된 상품 목록 조회 (Product ID 목록)
     */
    @Transactional(readOnly = true)
    public List<Long> getFavoriteProductIdsOrdered() {
        return getFavoritesOrderedByDisplayOrder().stream()
                .map(fav -> fav.getProduct().getId())
                .collect(Collectors.toList());
    }
}
