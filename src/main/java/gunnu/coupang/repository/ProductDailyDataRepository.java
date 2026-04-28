package gunnu.coupang.repository;

import gunnu.coupang.entity.Category;
import gunnu.coupang.entity.Company;
import gunnu.coupang.entity.Product;
import gunnu.coupang.entity.ProductDailyData;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductDailyDataRepository extends JpaRepository<ProductDailyData, Long> {
    Optional<ProductDailyData> findByProductAndDate(Product product, LocalDate date);
    Optional<ProductDailyData> findByProductAndDateAndDisplayPosition(Product product, LocalDate date, Integer displayPosition);

    @EntityGraph(attributePaths = "product")
    List<ProductDailyData> findByDateAndProduct_CategoryAndProduct_Company(
            LocalDate date,
            Category category,
            Company company);

    @EntityGraph(attributePaths = "product")
    List<ProductDailyData> findByDateAndProduct_Category(
            LocalDate date,
            Category category);

    @EntityGraph(attributePaths = "product")
    List<ProductDailyData> findByDateAndProduct_CategoryAndIsAdFalse(
            LocalDate date,
            Category category);

    @EntityGraph(attributePaths = "product")
    List<ProductDailyData> findByDateAndProduct_IdIn(LocalDate date, Collection<Long> productIds);

    @Query("select distinct d.date from ProductDailyData d order by d.date desc")
    List<LocalDate> findDistinctDatesOrderByDateDesc();
}
