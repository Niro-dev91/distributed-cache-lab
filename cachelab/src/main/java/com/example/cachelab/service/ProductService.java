package com.example.cachelab.service;

import com.example.cachelab.dto.ProductRequest;
import com.example.cachelab.dto.ProductResponse;
import com.example.cachelab.entity.Product;
import com.example.cachelab.repository.ProductRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ProductService {

    private static final Logger log =
            LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;

    public ProductService(
            ProductRepository productRepository
    ) {
        this.productRepository = productRepository;
    }


    @Transactional
    public ProductResponse createProduct(
            ProductRequest request
    ) {

        log.info(
                "Creating product name={}",
                request.name()
        );

        Product product = new Product(
                request.name(),
                request.price(),
                request.quantity()
        );

        Product savedProduct =
                productRepository.save(product);

        log.info(
                "Product created productId={}",
                savedProduct.getId()
        );

        return mapToResponse(savedProduct);
    }


    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {

        log.info(
                "Fetching product productId={}",
                id
        );

        Product product =
                productRepository
                        .findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Product not found productId={}",
                                    id
                            );

                            return new ResponseStatusException(
                                    NOT_FOUND,
                                    "Product not found"
                            );
                        });

        return mapToResponse(product);
    }


    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {

        log.info("Fetching all products");

        return productRepository
                .findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    @Transactional
    public ProductResponse updateProduct(
            Long id,
            ProductRequest request
    ) {

        Product product =
                productRepository
                        .findById(id)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        NOT_FOUND,
                                        "Product not found"
                                )
                        );

        product.update(
                request.name(),
                request.price(),
                request.quantity()
        );

        Product updatedProduct =
                productRepository.save(product);

        log.info(
                "Product updated productId={}",
                id
        );

        return mapToResponse(updatedProduct);
    }


    @Transactional
    public void deleteProduct(Long id) {

        Product product =
                productRepository
                        .findById(id)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        NOT_FOUND,
                                        "Product not found"
                                )
                        );

        productRepository.delete(product);

        log.info(
                "Product deleted productId={}",
                id
        );
    }


    private ProductResponse mapToResponse(
            Product product
    ) {

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getQuantity()
        );
    }
}
