package com.pbp.ecomm.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pbp.ecomm.order.model.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// dto/UpdateOrderStatusRequest.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderStatusRequest {

    @NotNull(message = "Status is required")
    private OrderStatus status;

    @Size(max = 500)
    private String remarks;

    // For SHIPPED status
    private String trackingNumber;
    private String courierName;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expectedDeliveryDate;
}