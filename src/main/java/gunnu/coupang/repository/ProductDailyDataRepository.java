package gunnu.coupang.repository;

import gunnu.coupang.entity.Product;
import gunnu.coupang.entity.ProductDailyData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface ProductDailyDataRepository extends JpaRepository<ProductDailyData, Long> {
    Optional<ProductDailyData> findByProductAndDate(Product product, LocalDate date);
    Optional<ProductDailyData> findByProductAndDateAndDisplayPosition(Product product, LocalDate date, Integer displayPosition);
}
