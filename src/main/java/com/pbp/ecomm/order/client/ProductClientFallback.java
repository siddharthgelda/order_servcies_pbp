// client/fallback/ProductClientFallback.java
package com.pbp.ecomm.order.client;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.naming.ServiceUnavailableException;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class ProductClientFallback implements ProductClient {

    @Override
    public com.pbp.ecomm.order.dto.ProductSnapshot getProductSnapshot(UUID id) throws Exception {
        log.error("Product Service unavailable — fallback triggered for id={}", id);
        throw new Exception(
                "Product Service is currently unavailable. " +
                        "Please try again in a moment."
        );
    }

    @Override
    public List<com.pbp.ecomm.order.dto.ProductSnapshot> getProductSnapshots(List<UUID> ids) throws ServiceUnavailableException {
        log.error("Product Service unavailable — batch fallback for {} products", ids.size());
        throw new ServiceUnavailableException(
                "Product Service is currently unavailable. " +
                        "Please try again in a moment."
        );
    }
}