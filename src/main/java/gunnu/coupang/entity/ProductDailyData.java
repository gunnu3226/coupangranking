package gunnu.coupang.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_daily_data",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDailyData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "display_ranking")
    private Integer display_ranking;

    @Column(name = "ranking")
    private Integer ranking;

    @Column(name = "is_ad")
    private Boolean isAd;

    @Column(name = "current_price")
    private String currentPrice;

    @Column(name = "original_price")
    private String originalPrice;

    @Column(name = "discount_rate")
    private String discountRate;

    @Column(name = "rating")
    private String rating;

    @Column(name = "review_count")
    private String reviewCount;

//    @Column(name = "delivery_info", length = 500)
//    private String deliveryInfo;

//    @Column(name = "delivery_cost")
//    private String deliveryCost;

//    @Column(name = "cashback_amount")
//    private String cashbackAmount;

//    @Column(name = "image_url", length = 1000)
//    private String imageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
