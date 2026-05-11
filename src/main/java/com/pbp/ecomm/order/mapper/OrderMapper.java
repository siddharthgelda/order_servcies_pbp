// mapper/OrderMapper.java
package com.pbp.ecomm.order.mapper;

import com.pbp.ecomm.order.dto.CreateOrderRequest;
import com.pbp.ecomm.order.dto.OrderDTO;
import com.pbp.ecomm.order.dto.OrderSummaryDTO;
import com.pbp.ecomm.order.dto.OrderTrackingDTO;
import com.pbp.ecomm.order.model.Order;
import com.pbp.ecomm.order.model.OrderItem;
import com.pbp.ecomm.order.model.OrderStatus;
import com.pbp.ecomm.order.model.ShippingAddress;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Component
public class OrderMapper {

    // ─────────────────────────────────────────────────────────────
    // Order → OrderDTO  (full detail view)
    // ─────────────────────────────────────────────────────────────

    public OrderDTO toDTO(Order order) {
        if (order == null) return null;

        return OrderDTO.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus())
                .statusLabel(resolveStatusLabel(order.getStatus()))

                // ── Pricing ───────────────────────────────────────
                .subtotal(order.getSubtotal())
                .discountAmount(order.getDiscountAmount())
                .shippingCharge(order.getShippingCharge())
                .taxAmount(order.getTaxAmount())
                .totalAmount(order.getTotalAmount())

                // ── Items ─────────────────────────────────────────
                .items(resolveItems(order.getItems()))
                .totalItems(resolveTotalItemCount(order.getItems()))

                // ── Shipping ──────────────────────────────────────
                .shippingAddress(toShippingAddressDTO(order.getShippingAddress()))
                .trackingNumber(order.getTrackingNumber())
                .courierName(order.getCourierName())
                .expectedDeliveryDate(order.getExpectedDeliveryDate())
                .deliveredAt(order.getDeliveredAt())

                // ── Payment ───────────────────────────────────────
                .paymentId(order.getPaymentId())
                .paymentMethod(order.getPaymentMethod())

                // ── Cancellation ──────────────────────────────────
                .cancellationReason(order.getCancellationReason())
                .cancelledAt(order.getCancelledAt())

                // ── Meta ──────────────────────────────────────────
                .customerNotes(order.getCustomerNotes())
                .cancellable(order.isCancellable())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // Order → OrderSummaryDTO  (lightweight list view)
    // ─────────────────────────────────────────────────────────────

    public OrderSummaryDTO toSummaryDTO(Order order) {
        if (order == null) return null;

        // First item used as the visual representative in order list
        OrderItem firstItem = resolveFirstItem(order.getItems());

        return OrderSummaryDTO.builder()
                .id(order.getId())
                .status(order.getStatus())
                .statusLabel(resolveStatusLabel(order.getStatus()))
                .totalAmount(order.getTotalAmount())
                .itemCount(resolveTotalItemCount(order.getItems()))
                .primaryProductName(firstItem != null
                        ? firstItem.getProductName() : null)
                .primaryProductImage(firstItem != null
                        ? firstItem.getProductImageUrl() : null)
                .createdAt(order.getCreatedAt())
                .cancellable(order.isCancellable())
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // Order → OrderTrackingDTO
    // ─────────────────────────────────────────────────────────────

    public OrderTrackingDTO toTrackingDTO(Order order) {
        if (order == null) return null;

        return OrderTrackingDTO.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .statusLabel(resolveStatusLabel(order.getStatus()))
                .trackingNumber(order.getTrackingNumber())
                .courierName(order.getCourierName())
                .shippingAddress(toShippingAddressDTO(order.getShippingAddress()))
                .expectedDeliveryDate(order.getExpectedDeliveryDate())
                .deliveredAt(order.getDeliveredAt())
                .timeline(buildStatusTimeline(order.getStatus()))
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // OrderItem → OrderItemDTO
    // ─────────────────────────────────────────────────────────────

    public OrderDTO.OrderItemDTO toItemDTO(OrderItem item) {
        if (item == null) return null;

        return OrderDTO.OrderItemDTO.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .productSku(item.getProductSku())
                .productImageUrl(item.getProductImageUrl())
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .lineTotal(item.getLineTotal())
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // ShippingAddress (embedded) → ShippingAddressDTO
    // ─────────────────────────────────────────────────────────────

    public OrderDTO.ShippingAddressDTO toShippingAddressDTO(
            ShippingAddress address) {

        if (address == null) return null;

        return OrderDTO.ShippingAddressDTO.builder()
                .recipientName(address.getRecipientName())
                .phone(address.getPhone())
                .addressLine1(address.getAddressLine1())
                .addressLine2(address.getAddressLine2())
                .city(address.getCity())
                .state(address.getState())
                .pincode(address.getPincode())
                .country(address.getCountry())
                .formattedAddress(address.getFormattedAddress())
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // ShippingAddressRequest → ShippingAddress (embedded entity)
    // Used in OrderService.create()
    // ─────────────────────────────────────────────────────────────

    public ShippingAddress toShippingAddress(
            CreateOrderRequest.ShippingAddressRequest req) {

        if (req == null) return null;

        return ShippingAddress.builder()
                .recipientName(req.getRecipientName().trim())
                .phone(req.getPhone().trim())
                .addressLine1(req.getAddressLine1().trim())
                .addressLine2(StringUtils.hasText(req.getAddressLine2())
                        ? req.getAddressLine2().trim() : null)
                .city(req.getCity().trim())
                .state(req.getState().trim())
                .pincode(req.getPincode().trim())
                .country(req.getCountry().trim())
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // OrderStatusHistory → OrderStatusHistoryDTO
    // ─────────────────────────────────────────────────────────────

 /*   public OrderStatusHistoryDTO toHistoryDTO(OrderStatusHistory history) {
        if (history == null) return null;

        return OrderStatusHistoryDTO.builder()
                .id(history.getId())
                .orderId(history.getOrderId())
                .fromStatus(history.getFromStatus())
                .fromStatusLabel(history.getFromStatus() != null
                        ? resolveStatusLabel(history.getFromStatus()) : null)
                .toStatus(history.getToStatus())
                .toStatusLabel(resolveStatusLabel(history.getToStatus()))
                .changedBy(history.getChangedBy())
                .remarks(history.getRemarks())
                .createdAt(history.getCreatedAt())
                .build();
    }
*/
    // ─────────────────────────────────────────────────────────────
    // List mappings
    // ─────────────────────────────────────────────────────────────

    public List<OrderDTO> toDTOList(List<Order> orders) {
        if (orders == null || orders.isEmpty()) return Collections.emptyList();
        return orders.stream().map(this::toDTO).toList();
    }

    public List<OrderSummaryDTO> toSummaryDTOList(List<Order> orders) {
        if (orders == null || orders.isEmpty()) return Collections.emptyList();
        return orders.stream().map(this::toSummaryDTO).toList();
    }

    // ─────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Maps items list — returns empty list if null (lazy not loaded).
     * Never throws LazyInitializationException to the caller.
     */
    private List<OrderDTO.OrderItemDTO> resolveItems(List<OrderItem> items) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        return items.stream()
                .map(this::toItemDTO)
                .toList();
    }

    /**
     * Total quantity = sum of all item quantities.
     * e.g. 2x Nike + 1x Samsung = 3 items
     */
    private Integer resolveTotalItemCount(List<OrderItem> items) {
        if (items == null || items.isEmpty()) return 0;
        return items.stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();
    }

    /**
     * Returns the first item in the order — used as visual
     * representative in order summary / history list cards.
     */
    private OrderItem resolveFirstItem(List<OrderItem> items) {
        if (items == null || items.isEmpty()) return null;
        return items.get(0);
    }

    /**
     * Human-readable status labels for frontend display.
     * Avoids sending raw enum names to UI.
     */
    private String resolveStatusLabel(OrderStatus status) {
        if (status == null) return null;
        return switch (status) {
            case PENDING -> "Order Placed";
            case CONFIRMED -> "Order Confirmed";
            case PAYMENT_FAILED -> "Payment Failed";
            case PROCESSING -> "Being Prepared";
            case SHIPPED -> "Shipped";
            case OUT_FOR_DELIVERY -> "Out for Delivery";
            case DELIVERED -> "Delivered";
            case CANCELLED -> "Cancelled";
            case REFUND_INITIATED -> "Refund Initiated";
            case REFUNDED -> "Refunded";
        };
    }

    /**
     * Builds a visual timeline showing which steps are
     * completed, active, and upcoming for the tracking view.
     * <p>
     * Used by the frontend to render a progress stepper.
     */
    private List<OrderTrackingDTO.TimelineStep> buildStatusTimeline(
            OrderStatus currentStatus) {

        // Define the standard delivery journey steps in order
        List<OrderStatus> journey = List.of(
                OrderStatus.PENDING,
                OrderStatus.CONFIRMED,
                OrderStatus.PROCESSING,
                OrderStatus.SHIPPED,
                OrderStatus.OUT_FOR_DELIVERY,
                OrderStatus.DELIVERED
        );

        int currentIndex = journey.indexOf(currentStatus);

        return journey.stream()
                .map(step -> {
                    int stepIndex = journey.indexOf(step);
                    String state;

                    if (currentStatus == OrderStatus.CANCELLED
                            || currentStatus == OrderStatus.REFUNDED) {
                        state = "CANCELLED";
                    } else if (stepIndex < currentIndex) {
                        state = "COMPLETED";
                    } else if (stepIndex == currentIndex) {
                        state = "ACTIVE";
                    } else {
                        state = "PENDING";
                    }

                    return OrderTrackingDTO.TimelineStep.builder()
                            .status(step)
                            .label(resolveStatusLabel(step))
                            .state(state)
                            .build();
                })
                .toList();
    }
}
