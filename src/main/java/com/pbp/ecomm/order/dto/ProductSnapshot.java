// client/dto/ProductSnapshot.java
package com.pbp.ecomm.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Immutable snapshot of a Product at the time of order creation.
 * <p>
 * WHY A SNAPSHOT:
 * - Products change over time (price, name, images)
 * - Orders must reflect what the customer actually bought and paid
 * - This DTO is returned by the Product Service Feign client
 * - Key fields are then copied into OrderItem as a permanent record
 * <p>
 * IMPORTANT:
 * - This is NOT a JPA entity — it is never persisted directly
 * - It is a transient object that lives only during order creation
 * - Fields stored in OrderItem are the permanent record
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)  // safe against Product Service API changes
public class ProductSnapshot {

    // ── Identity ──────────────────────────────────────────────────

    private String id;
    private String name;
    private String description;
    private String sku;

    // ── Pricing ───────────────────────────────────────────────────

    private BigDecimal price;               // original price
    private BigDecimal discountedPrice;     // null if no discount
    private Double discountPercent;     // null if no discount

    // ── Category ──────────────────────────────────────────────────

    private Integer categoryId;
    private String categoryName;
    private String categorySlug;

    // ── Media ─────────────────────────────────────────────────────

    private String imageUrls;

    // ── Attributes ────────────────────────────────────────────────

    private Map<String, Object> attributes;

    // ── Stock ─────────────────────────────────────────────────────

    private Integer stockQuantity;          // available = quantity - reserved
    private Boolean inStock;

    // ── Ratings ───────────────────────────────────────────────────

    private Double averageRating;
    private Integer reviewCount;

    // ── Status ────────────────────────────────────────────────────

    private Boolean active;

    // ─────────────────────────────────────────────────────────────
    // Business logic methods
    // Used by OrderService during order item construction
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns the effective purchase price.
     * <p>
     * Priority:
     * 1. discountedPrice if present and less than original price
     * 2. original price as fallback
     * <p>
     * This is the price locked into OrderItem.unitPrice at purchase time.
     */
    public BigDecimal getEffectivePrice() {
        if (discountedPrice != null
                && discountedPrice.compareTo(BigDecimal.ZERO) > 0
                && discountedPrice.compareTo(price) < 0) {
            return discountedPrice.setScale(2, RoundingMode.HALF_UP);
        }
        return price != null
                ? price.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    /**
     * Returns the primary (first) image URL.
     * Used to populate OrderItem.productImageUrl snapshot.
     * Returns null if no images are available.
     */
    public String getPrimaryImageUrl() {

        return imageUrls;
    }

    /**
     * Returns true if the product is available for purchase.
     * <p>
     * All three conditions must be true:
     * 1. Product is active (not deactivated by admin)
     * 2. inStock flag is true
     * 3. Actual available quantity > 0
     */
    public boolean isAvailableForPurchase() {
        return Boolean.TRUE.equals(active)
                && Boolean.TRUE.equals(inStock)
                && stockQuantity != null
                && stockQuantity > 0;
    }

    /**
     * Checks if the requested quantity can be fulfilled.
     * Used before reserving stock during order creation.
     */
    public boolean canFulfil(int requestedQuantity) {
        if (!isAvailableForPurchase()) return false;
        return stockQuantity >= requestedQuantity;
    }

    /**
     * Calculates line total for a given quantity.
     * Convenience method used in order total calculations.
     */
    public BigDecimal calculateLineTotal(int quantity) {
        return getEffectivePrice()
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Returns a safe display name — never null.
     * Falls back to SKU if name is somehow missing.
     */
    public String getDisplayName() {
        if (name != null && !name.isBlank()) return name;
        if (sku != null && !sku.isBlank()) return sku;
        return "Product " + id;
    }

    /**
     * Returns true if the product has an active discount.
     */
    public boolean hasDiscount() {
        return discountedPrice != null
                && discountPercent != null
                && discountPercent > 0
                && discountedPrice.compareTo(price) < 0;
    }

    /**
     * Returns the discount amount in absolute currency value.
     * e.g. price=8999, discountedPrice=8099.10 → savings=899.90
     */
    public BigDecimal getDiscountAmount() {
        if (!hasDiscount()) return BigDecimal.ZERO;
        return price.subtract(discountedPrice)
                .setScale(2, RoundingMode.HALF_UP);
    }

}