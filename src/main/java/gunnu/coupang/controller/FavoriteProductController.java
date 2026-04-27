package gunnu.coupang.controller;

import gunnu.coupang.service.FavoriteProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteProductController {

    private final FavoriteProductService favoriteProductService;

    /**
     * 선택한 상품 저장
     */
    @PostMapping
    public ResponseEntity<?> saveFavorites(@RequestBody Map<String, List<Long>> request) {
        try {
            List<Long> productIds = request.get("productIds");
            if (productIds == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "선택 상품 정보가 없습니다."));
            }

            favoriteProductService.saveFavorites(productIds);
            return ResponseEntity.ok(Map.of("success", true, "message", "저장되었습니다.", "count", productIds.size()));
        } catch (Exception e) {
            log.error("선택 상품 저장 실패", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "저장에 실패했습니다: " + e.getMessage()));
        }
    }

    /**
     * 선택된 상품 개수 조회
     */
    @GetMapping("/count")
    public ResponseEntity<?> getFavoriteCount() {
        try {
            long count = favoriteProductService.getFavoriteCount();
            return ResponseEntity.ok(Map.of("success", true, "count", count));
        } catch (Exception e) {
            log.error("선택 상품 개수 조회 실패", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "조회에 실패했습니다."));
        }
    }

    /**
     * 선택한 상품들의 표시 순서 업데이트
     * 요청 형식: { "orderMap": { "productId": displayOrder, ... } }
     */
    @PutMapping("/order")
    public ResponseEntity<?> updateDisplayOrder(@RequestBody Map<String, Map<Long, Integer>> request) {
        try {
            Map<Long, Integer> orderMap = request.get("orderMap");
            if (orderMap == null || orderMap.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "순서 정보가 없습니다."));
            }

            favoriteProductService.updateDisplayOrder(orderMap);
            return ResponseEntity.ok(Map.of("success", true, "message", "순서가 저장되었습니다.", "count", orderMap.size()));
        } catch (Exception e) {
            log.error("표시 순서 업데이트 실패", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "순서 저장에 실패했습니다: " + e.getMessage()));
        }
    }

    /**
     * 선택된 상품 목록 조회 (순서대로)
     */
    @GetMapping("/ordered")
    public ResponseEntity<?> getFavoritesOrdered() {
        try {
            List<Long> productIds = favoriteProductService.getFavoriteProductIdsOrdered();
            return ResponseEntity.ok(Map.of("success", true, "productIds", productIds));
        } catch (Exception e) {
            log.error("선택 상품 목록 조회 실패", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "조회에 실패했습니다."));
        }
    }
}
