// model/Order.java
package com.pbp.ecomm.order.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(
        name = "orders",
        indexes = {
                @Index(name = "idx_order_user_id", columnList = "user_id"),
                @Index(name = "idx_order_status", columnList = "status"),
                @Index(name = "idx_order_created_at", columnList = "created_at")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "items")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    @Column(updatable = false, nullable = false)
    private String id;

    // User ID from JWT token — no FK to users table
    // (users live in a separate microservice)
    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    // ── Pricing ───────────────────────────────────────────────────

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;            // sum of all item totals

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount;      // coupon / promo deduction

    @Column(name = "shipping_charge", precision = 12, scale = 2)
    private BigDecimal shippingCharge;      // delivery fee

    @Column(name = "tax_amount", precision = 12, scale = 2)
    private BigDecimal taxAmount;           // GST / VAT

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;         // final payable amount

    // ── Shipping address (embedded, no separate table needed) ─────

    @Embedded
    private ShippingAddress shippingAddress;

    // ── Order items ───────────────────────────────────────────────

    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY,
            orphanRemoval = true
    )
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    // ── Tracking ──────────────────────────────────────────────────

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "courier_name", length = 100)
    private String courierName;

    @Column(name = "expected_delivery_date")
    private LocalDateTime expectedDeliveryDate;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    // ── Payment reference ─────────────────────────────────────────

    @Column(name = "payment_id", length = 100)
    private String paymentId;              // reference from Payment Service

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;          // CARD | UPI | NET_BANKING | COD

    // ── Cancellation ──────────────────────────────────────────────

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // ── Notes ─────────────────────────────────────────────────────

    @Column(name = "customer_notes", length = 500)
    private String customerNotes;

    // ── Auditing ──────────────────────────────────────────────────

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Business logic ────────────────────────────────────────────

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
        recalculateTotals();
    }

    public void recalculateTotals() {
        this.subtotal = items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        BigDecimal shipping = shippingCharge != null ? shippingCharge : BigDecimal.ZERO;
        BigDecimal tax = taxAmount != null ? taxAmount : BigDecimal.ZERO;

        this.totalAmount = subtotal
                .subtract(discount)
                .add(shipping)
                .add(tax)
                .max(BigDecimal.ZERO);              // never negative
    }

    public boolean isCancellable() {
        return status == OrderStatus.PENDING
                || status == OrderStatus.CONFIRMED
                || status == OrderStatus.PROCESSING;
    }

    public boolean isDelivered() {
        return status == OrderStatus.DELIVERED;
    }

    public boolean isActive() {
        return status != OrderStatus.CANCELLED
                && status != OrderStatus.REFUNDED;
    }
}