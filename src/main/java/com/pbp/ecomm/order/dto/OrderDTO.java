package com.pbp.ecomm.order.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.pbp.ecomm.order.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {

    private String id;
    private String userId;
    private OrderStatus status;
    private String statusLabel;        // human readable: "Out for Delivery"

    // ── Pricing breakdown ─────────────────────────────────────────

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal subtotal;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal discountAmount;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal shippingCharge;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal taxAmount;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal totalAmount;

    // ── Items ─────────────────────────────────────────────────────

    private List<OrderItemDTO> items;
    private Integer totalItems;  // total quantity across all items

    // ── Shipping ──────────────────────────────────────────────────

    private ShippingAddressDTO shippingAddress;
    private String trackingNumber;
    private String courierName;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expectedDeliveryDate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime deliveredAt;

    // ── Payment ───────────────────────────────────────────────────

    private String paymentId;
    private String paymentMethod;

    // ── Cancellation ──────────────────────────────────────────────

    private String cancellationReason;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime cancelledAt;

    // ── Meta ──────────────────────────────────────────────────────

    private String customerNotes;
    private boolean cancellable;        // computed from status

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    // ── Nested DTOs ───────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDTO {
        private String id;
        private String productId;
        private String productName;
        private String productSku;
        private String productImageUrl;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal unitPrice;

        private Integer quantity;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal lineTotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShippingAddressDTO {
        private String recipientName;
        private String phone;
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String state;
        private String pincode;
        private String country;
        private String formattedAddress;    // full address as single string
    }
}
