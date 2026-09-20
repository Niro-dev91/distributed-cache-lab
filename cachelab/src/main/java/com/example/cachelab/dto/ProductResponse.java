package com.example.cachelab.dto;

import java.math.BigDecimal;

public record ProductResponse(

        Long id,
        String name,
        BigDecimal price,
        Integer quantity

) {
}
