package gunnu.coupang.repository;

import gunnu.coupang.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByItemIdAndProductId(String itemId, String productId);
    Optional<Product> findByProductId(String productId);
}
