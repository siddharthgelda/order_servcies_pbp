package com.pbp.ecomm.order.client;


import com.pbp.ecomm.order.dto.ProductSnapshot;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import javax.naming.ServiceUnavailableException;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "product", url = "${services.product.url}", fallback = ProductClientFallback.class)
public interface ProductClient {

    /**
     * Fetch a single product snapshot by ID.
     * Called once per distinct product in the order.
     */
    @GetMapping("/api/v1/products/{id}")
    ProductSnapshot getProductSnapshot(@PathVariable UUID id) throws Exception;

    /**
     * Batch fetch multiple products in a single HTTP call.
     * More efficient than N individual calls for large orders.
     */
    @GetMapping("/api/v1/products/batch")
    List<ProductSnapshot> getProductSnapshots(
            @RequestParam List<UUID> ids) throws ServiceUnavailableException;
}