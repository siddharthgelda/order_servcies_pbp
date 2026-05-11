package com.pbp.ecomm.order.model;


public enum OrderStatus {
    PENDING,            // order placed, payment not yet confirmed
    CONFIRMED,          // payment confirmed
    PAYMENT_FAILED,     // payment attempt failed
    PROCESSING,         // being picked and packed
    SHIPPED,            // dispatched from warehouse
    OUT_FOR_DELIVERY,   // with delivery agent
    DELIVERED,          // received by customer
    CANCELLED,          // cancelled before shipment
    REFUND_INITIATED,   // refund started
    REFUNDED            // refund completed
}
