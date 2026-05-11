package com.pbp.ecomm.order.repo;


import com.pbp.ecomm.order.model.Order;
import com.pbp.ecomm.order.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;


@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    // ── Customer queries ──────────────────────────────────────────

    Optional<Order> findByIdAndUserId(String id, String userId);

    Page<Order> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    // ── Admin queries ─────────────────────────────────────────────

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // ── Order detail with items (avoids N+1) ──────────────────────

    @Query("""
                SELECT o FROM Order o
                LEFT JOIN FETCH o.items
                WHERE o.id = :id
            """)
    Optional<Order> findByIdWithItems(@Param("id") String id);

    @Query("""
                SELECT o FROM Order o
                LEFT JOIN FETCH o.items
                WHERE o.id = :id
                  AND o.userId = :userId
            """)
    Optional<Order> findByIdWithItemsAndUserId(
            @Param("id") String id,
            @Param("userId") String userId);

    // ── Status queries ────────────────────────────────────────────

    long countByUserId(String userId);

    long countByStatus(OrderStatus status);

    // ── Analytics ─────────────────────────────────────────────────

    @Query("""
                SELECT SUM(o.totalAmount) FROM Order o
                WHERE o.status = 'DELIVERED'
                  AND o.createdAt BETWEEN :from AND :to
            """)
    java.math.BigDecimal sumDeliveredRevenue(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
