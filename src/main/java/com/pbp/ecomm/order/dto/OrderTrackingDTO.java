package com.pbp.ecomm.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pbp.ecomm.order.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

// dto/OrderTrackingDTO.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTrackingDTO {

    private String orderId;
    private OrderStatus status;
    private String statusLabel;
    private String trackingNumber;
    private String courierName;

    private OrderDTO.ShippingAddressDTO shippingAddress;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expectedDeliveryDate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime deliveredAt;

    // Visual progress stepper — list of all steps with their state
    private List<TimelineStep> timeline;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    // ── Nested ────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineStep {
        private OrderStatus status;
        private String label;

        // COMPLETED | ACTIVE | PENDING | CANCELLED
        private String state;
    }
}