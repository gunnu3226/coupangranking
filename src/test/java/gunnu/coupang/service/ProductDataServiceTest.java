package gunnu.coupang.service;

import gunnu.coupang.dto.SavedProductData;
import gunnu.coupang.entity.Category;
import gunnu.coupang.entity.Company;
import gunnu.coupang.entity.Product;
import gunnu.coupang.entity.ProductDailyData;
import gunnu.coupang.repository.ProductDailyDataRepository;
import gunnu.coupang.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductDataServiceTest {

    @Mock
    ProductRepository productRepository;

    @Mock
    ProductDailyDataRepository productDailyDataRepository;

    @InjectMocks
    ProductDataService productDataService;

    @Test
    void getProductDataByCategoryAndCompanyUsesTargetedQueryAndKeepsLatestPerItemId() {
        LocalDate date = LocalDate.of(2026, 4, 27);
        Product product = product(1L, "item-1", "product-1", Company.IPTIME);
        ProductDailyData older = dailyData(10L, product, date, LocalDateTime.of(2026, 4, 27, 10, 0), 2, "1000");
        ProductDailyData latest = dailyData(11L, product, date, LocalDateTime.of(2026, 4, 27, 11, 0), 1, "900");

        when(productDailyDataRepository.findByDateAndProduct_CategoryAndProduct_Company(
                date, Category.ROUTER, Company.IPTIME))
                .thenReturn(List.of(older, latest));

        List<SavedProductData> result = productDataService.getProductDataByCategoryAndCompany(
                Category.ROUTER, Company.IPTIME, date);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getItemId()).isEqualTo("item-1");
        assertThat(result.get(0).getCurrentPrice()).isEqualTo("900");
        assertThat(result.get(0).getRanking()).isEqualTo(1);
        verify(productDailyDataRepository, never()).findAll();
    }

    @Test
    void getAllNonAdProductsUsesTargetedQueryAndKeepsLatestPerItemId() {
        LocalDate date = LocalDate.of(2026, 4, 27);
        Product product = product(1L, "item-1", "product-1", Company.IPTIME);
        ProductDailyData older = dailyData(10L, product, date, LocalDateTime.of(2026, 4, 27, 10, 0), 3, "1000");
        ProductDailyData latest = dailyData(12L, product, date, LocalDateTime.of(2026, 4, 27, 12, 0), 2, "800");
        older.setIsAd(false);
        latest.setIsAd(false);

        when(productDailyDataRepository.findByDateAndProduct_CategoryAndIsAdFalse(date, Category.ROUTER))
                .thenReturn(List.of(older, latest));

        List<SavedProductData> result = productDataService.getAllNonAdProducts(Category.ROUTER, date);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCurrentPrice()).isEqualTo("800");
        verify(productDailyDataRepository, never()).findAll();
    }

    @Test
    void getAllSavedProductsKeepsAdAndRankedRowsForSameItemId() {
        LocalDate date = LocalDate.of(2026, 4, 27);
        Product product = product(1L, "item-1", "product-1", Company.IPTIME);
        ProductDailyData ad = dailyData(10L, product, date, LocalDateTime.of(2026, 4, 27, 10, 0), null, "1100");
        ProductDailyData ranked = dailyData(11L, product, date, LocalDateTime.of(2026, 4, 27, 11, 0), 1, "900");
        ad.setIsAd(true);
        ranked.setIsAd(false);

        when(productDailyDataRepository.findByDateAndProduct_Category(date, Category.ROUTER))
                .thenReturn(List.of(ad, ranked));

        List<SavedProductData> result = productDataService.getAllSavedProductsByCategory(Category.ROUTER, date);

        assertThat(result).hasSize(2);
        assertThat(result).anySatisfy(productData -> {
            assertThat(productData.getItemId()).isEqualTo("item-1");
            assertThat(productData.isAd()).isTrue();
            assertThat(productData.getRanking()).isNull();
            assertThat(productData.getCurrentPrice()).isEqualTo("1100");
        });
        assertThat(result).anySatisfy(productData -> {
            assertThat(productData.getItemId()).isEqualTo("item-1");
            assertThat(productData.isAd()).isFalse();
            assertThat(productData.getRanking()).isEqualTo(1);
            assertThat(productData.getCurrentPrice()).isEqualTo("900");
        });
        verify(productDailyDataRepository, never()).findAll();
    }

    @Test
    void getFavoriteProductsWithDataKeepsLatestDataAndStillIncludesProductsWithoutData() {
        LocalDate date = LocalDate.of(2026, 4, 27);
        Product withData = product(1L, "item-1", "product-1", Company.IPTIME);
        Product withoutData = product(2L, "item-2", "product-2", Company.TPLINK);
        ProductDailyData older = dailyData(10L, withData, date, LocalDateTime.of(2026, 4, 27, 10, 0), 5, "1000");
        ProductDailyData latest = dailyData(13L, withData, date, LocalDateTime.of(2026, 4, 27, 13, 0), 4, "700");

        when(productRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(withData, withoutData));
        when(productDailyDataRepository.findByDateAndProduct_IdIn(eq(date), any()))
                .thenReturn(List.of(older, latest));

        List<SavedProductData> result = productDataService.getFavoriteProductsWithData(Set.of(1L, 2L), date);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getCurrentPrice()).isEqualTo("700");
        assertThat(result.get(1).getId()).isEqualTo(2L);
        assertThat(result.get(1).getCurrentPrice()).isNull();
        verify(productDailyDataRepository, never()).findAll();
    }

    @Test
    void getFavoriteProductsWithDataKeepsAdAndRankedRowsForSameFavorite() {
        LocalDate date = LocalDate.of(2026, 4, 27);
        Product product = product(1L, "item-1", "product-1", Company.IPTIME);
        ProductDailyData ad = dailyData(10L, product, date, LocalDateTime.of(2026, 4, 27, 10, 0), null, "1100");
        ProductDailyData ranked = dailyData(11L, product, date, LocalDateTime.of(2026, 4, 27, 11, 0), 1, "900");
        ad.setIsAd(true);
        ranked.setIsAd(false);

        when(productRepository.findAllById(Set.of(1L))).thenReturn(List.of(product));
        when(productDailyDataRepository.findByDateAndProduct_IdIn(eq(date), any()))
                .thenReturn(List.of(ad, ranked));

        List<SavedProductData> result = productDataService.getFavoriteProductsWithData(Set.of(1L), date);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(SavedProductData::isAd).containsExactlyInAnyOrder(true, false);
        assertThat(result).extracting(SavedProductData::getCurrentPrice).containsExactlyInAnyOrder("1100", "900");
        verify(productDailyDataRepository, never()).findAll();
    }

    @Test
    void getAvailableDatesUsesDistinctDateQuery() {
        LocalDate newest = LocalDate.of(2026, 4, 27);
        LocalDate older = LocalDate.of(2026, 4, 26);
        when(productDailyDataRepository.findDistinctDatesOrderByDateDesc()).thenReturn(List.of(newest, older));

        assertThat(productDataService.getAvailableDates()).containsExactly(newest, older);
        verify(productDailyDataRepository, never()).findAll();
    }

    private Product product(Long id, String itemId, String productId, Company company) {
        return Product.builder()
                .id(id)
                .itemId(itemId)
                .productId(productId)
                .productName("Product " + itemId)
                .category(Category.ROUTER)
                .company(company)
                .build();
    }

    private ProductDailyData dailyData(Long id,
                                       Product product,
                                       LocalDate date,
                                       LocalDateTime createdAt,
                                       Integer ranking,
                                       String currentPrice) {
        return ProductDailyData.builder()
                .id(id)
                .product(product)
                .date(date)
                .createdAt(createdAt)
                .ranking(ranking)
                .isAd(false)
                .currentPrice(currentPrice)
                .reviewCount("10")
                .productUrl("https://example.com/" + id)
                .build();
    }
}
