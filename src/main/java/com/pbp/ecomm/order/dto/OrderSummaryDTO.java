package com.pbp.ecomm.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pbp.ecomm.order.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSummaryDTO {

    private String id;
    private OrderStatus status;
    private String statusLabel;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal totalAmount;

    private Integer itemCount;
    private String primaryProductName;     // first item name
    private String primaryProductImage;    // first item image

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    private boolean cancellable;
}
