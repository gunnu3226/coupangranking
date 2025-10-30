package gunnu.coupang.repository;

import gunnu.coupang.entity.FavoriteProduct;
import gunnu.coupang.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteProductRepository extends JpaRepository<FavoriteProduct, Long> {
    Optional<FavoriteProduct> findByProduct(Product product);
    List<FavoriteProduct> findAllByOrderByCreatedAtDesc();
    boolean existsByProduct(Product product);
    void deleteByProduct(Product product);
}
