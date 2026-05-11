package com.pbp.ecomm.order.dto;


import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotEmpty(message = "Order must have at least one item")
    @Size(max = 50, message = "Cannot order more than 50 distinct items")
    @Valid
    private List<OrderItemRequest> items;

    @NotNull(message = "Shipping address is required")
    @Valid
    private ShippingAddressRequest shippingAddress;

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String customerNotes;

    @NotBlank(message = "Payment method is required")
    @Pattern(
            regexp = "CARD|UPI|NET_BANKING|COD",
            message = "Payment method must be CARD, UPI, NET_BANKING, or COD"
    )
    private String paymentMethod;

    // ── Nested DTOs ───────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {

        @NotNull(message = "Product ID is required")
        private java.util.UUID productId;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 100, message = "Cannot order more than 100 units of a single item")
        private Integer quantity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShippingAddressRequest {

        @NotBlank(message = "Recipient name is required")
        @Size(max = 150)
        private String recipientName;

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number")
        private String phone;

        @NotBlank(message = "Address line 1 is required")
        @Size(max = 255)
        private String addressLine1;

        @Size(max = 255)
        private String addressLine2;

        @NotBlank(message = "City is required")
        @Size(max = 100)
        private String city;

        @NotBlank(message = "State is required")
        @Size(max = 100)
        private String state;

        @NotBlank(message = "Pincode is required")
        @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Enter a valid 6-digit pincode")
        private String pincode;

        @NotBlank(message = "Country is required")
        @Size(max = 100)
        private String country;
    }
}