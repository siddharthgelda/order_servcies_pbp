package com.pbp.ecomm.order;

import com.pbp.ecomm.order.dto.*;
import com.pbp.ecomm.order.filter.AuthenticatedUser;
import com.pbp.ecomm.order.services.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderDTO> createOrder(@Valid @RequestBody CreateOrderRequest req,
                                                @AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Order creation request from userId={} items={}", user.getUserId(), req.getItems().size());
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(req, user.getUserId().toString()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDTO> getOrder(@PathVariable UUID id,
                                             @AuthenticationPrincipal AuthenticatedUser user) throws Exception {
        OrderDTO order = user.isAdmin()
                ? orderService.findById(id.toString())
                : orderService.findByIdAndUserId(id, user.getUserId());
        return ResponseEntity.ok(order);
    }

    @GetMapping("/my-orders")
    public ResponseEntity<Page<OrderSummaryDTO>> getMyOrders(@AuthenticationPrincipal AuthenticatedUser user,
                                                             @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(orderService.findByUserId(user.getUserId(), pageable));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderDTO> cancelOrder(@PathVariable UUID id,
                                                @Valid @RequestBody CancelOrderRequest req,
                                                @AuthenticationPrincipal AuthenticatedUser user) throws Exception {
        log.info("Cancel request orderId={} userId={}", id, user.getUserId());
        return ResponseEntity.ok(orderService.cancel(id, user.getUserId(), req.getReason()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<OrderSummaryDTO>> getAllOrders(@RequestParam(required = false) String status,
                                                              @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(orderService.findAll(status, pageable));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<OrderSummaryDTO>> getOrdersByUser(@PathVariable UUID userId,
                                                                 @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(orderService.findByUserId(userId, pageable));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderDTO> updateStatus(@PathVariable UUID id,
                                                 @Valid @RequestBody UpdateOrderStatusRequest req,
                                                 @AuthenticationPrincipal AuthenticatedUser admin) throws Exception {
        log.info("Status update orderId={} newStatus={} by admin={}", id, req.getStatus(), admin.getUserId());
        return ResponseEntity.ok(orderService.updateStatus(id, req, admin.getUserId().toString()));
    }
}