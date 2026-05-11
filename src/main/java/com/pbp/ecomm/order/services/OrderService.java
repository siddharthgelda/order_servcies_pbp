package com.pbp.ecomm.order.services;

import com.pbp.ecomm.order.client.ProductClient;
import com.pbp.ecomm.order.dto.*;
import com.pbp.ecomm.order.mapper.OrderMapper;
import com.pbp.ecomm.order.model.*;
import com.pbp.ecomm.order.repo.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderService {

    private final OrderRepository orderRepo;
    private final ProductClient productClient;
    private final OrderMapper orderMapper;

    public OrderDTO create(CreateOrderRequest req, String userId) {
        List<OrderItem> items = req.getItems().stream()
                .map(itemReq -> buildOrderItem(itemReq))
                .toList();

        BigDecimal subtotal = items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal shippingCharge = subtotal.compareTo(new BigDecimal("500")) >= 0
                ? BigDecimal.ZERO : new BigDecimal("49.00");

        BigDecimal taxAmount = subtotal.multiply(new BigDecimal("0.18"))
                .setScale(2, RoundingMode.HALF_UP);

        Order order = Order.builder()
                .userId(userId)
                .status(OrderStatus.PENDING)
                .subtotal(subtotal)
                .shippingCharge(shippingCharge)
                .taxAmount(taxAmount)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(subtotal.add(shippingCharge).add(taxAmount))
                .shippingAddress(orderMapper.toShippingAddress(req.getShippingAddress()))
                .paymentMethod(req.getPaymentMethod())
                .customerNotes(req.getCustomerNotes())
                .items(new ArrayList<>())
                .build();

        items.forEach(order::addItem);
        Order saved = orderRepo.save(order);

        log.info("Order created id={} userId={} total={}", saved.getId(), userId, saved.getTotalAmount());
        return orderMapper.toDTO(saved);
    }

    private OrderItem buildOrderItem(CreateOrderRequest.OrderItemRequest itemReq) {
        try {
            ProductSnapshot snap = productClient.getProductSnapshot(itemReq.getProductId());
            if (!snap.getInStock() || snap.getStockQuantity() < itemReq.getQuantity())
                throw new RuntimeException("Insufficient stock for product: " + snap.getName());
            return OrderItem.builder()
                    .productId(snap.getId()).productName(snap.getName())
                    .productSku(snap.getSku()).productImageUrl(snap.getPrimaryImageUrl())
                    .unitPrice(snap.getEffectivePrice()).quantity(itemReq.getQuantity())
                    .build();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @Transactional(readOnly = true)
    public OrderDTO findById(String id) throws Exception {
        return orderRepo.findByIdWithItems(id).map(orderMapper::toDTO)
                .orElseThrow(() -> new Exception("Order not found: " + id));
    }

    @Transactional(readOnly = true)
    public OrderDTO findByIdAndUserId(UUID id, UUID userId) throws Exception {
        return orderRepo.findByIdWithItemsAndUserId(id.toString(), userId.toString())
                .map(orderMapper::toDTO)
                .orElseThrow(() -> new Exception("Order not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryDTO> findByUserId(UUID userId, Pageable pageable) {
        return orderRepo.findByUserIdOrderByCreatedAtDesc(userId.toString(), pageable)
                .map(orderMapper::toSummaryDTO);
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryDTO> findAll(String status, Pageable pageable) {
        if (status != null)
            return orderRepo.findByStatus(OrderStatus.valueOf(status.toUpperCase()), pageable)
                    .map(orderMapper::toSummaryDTO);
        return orderRepo.findAllByOrderByCreatedAtDesc(pageable).map(orderMapper::toSummaryDTO);
    }
    public OrderDTO cancel(UUID orderId, UUID userId, String reason) throws Exception {
        Order order = orderRepo.findByIdWithItems(orderId.toString())
                .orElseThrow(() -> new Exception("Order not found: " + orderId));

        if (!order.getUserId().equals(userId))
            throw new Exception("Order not found: " + orderId);
        if (!order.isCancellable())
            throw new Exception("Order cannot be cancelled in status: " + order.getStatus());
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(reason);
        order.setCancelledAt(LocalDateTime.now());
        orderRepo.save(order);
        log.info("Order cancelled id={} userId={}", orderId, userId);
        return orderMapper.toDTO(order);
    }
    public OrderDTO updateStatus(UUID orderId, UpdateOrderStatusRequest req, String changedBy) throws Exception {
        Order order = orderRepo.findByIdWithItems(orderId.toString())
                .orElseThrow(() -> new Exception("Order not found: " + orderId));

        order.setStatus(req.getStatus());

        if (req.getStatus() == OrderStatus.SHIPPED) {
            order.setTrackingNumber(req.getTrackingNumber());
            order.setCourierName(req.getCourierName());
            order.setExpectedDeliveryDate(req.getExpectedDeliveryDate());
        }
        if (req.getStatus() == OrderStatus.DELIVERED)
            order.setDeliveredAt(LocalDateTime.now());

        orderRepo.save(order);
        log.info("Order status updated id={} → {}", orderId, req.getStatus());
        return orderMapper.toDTO(order);
    }

    @Transactional(readOnly = true)
    public OrderTrackingDTO getTracking(UUID orderId) throws Exception {
        return orderRepo.findById(orderId.toString())
                .map(orderMapper::toTrackingDTO)
                .orElseThrow(() -> new Exception("Order not found: " + orderId));
    }

    @Transactional(readOnly = true)
    public OrderTrackingDTO getTrackingForUser(UUID orderId, UUID userId) throws Exception {
        return orderRepo.findByIdAndUserId(orderId.toString(), userId.toString())
                .map(orderMapper::toTrackingDTO)
                .orElseThrow(() -> new Exception("Order not found: " + orderId));
    }
}