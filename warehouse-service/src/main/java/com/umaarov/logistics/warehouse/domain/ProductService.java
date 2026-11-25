package com.umaarov.logistics.warehouse.domain;

import com.umaarov.logistics.warehouse.persistence.ProductRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository products;
    private final Clock clock;

    public ProductService(ProductRepository products, Clock clock) {
        this.products = products;
        this.clock = clock;
    }

    @Transactional
    public Product create(String sku, String name, int initialQuantity) {
        if (products.existsBySku(sku)) {
            throw new DuplicateSkuException(sku);
        }
        return products.save(new Product(sku, name, initialQuantity, clock.instant()));
    }

    @Transactional(readOnly = true)
    public Product get(String sku) {
        return products.findBySku(sku).orElseThrow(() -> new ProductNotFoundException(sku));
    }

    @Transactional(readOnly = true)
    public List<Product> list(int page, int size) {
        return products.findAll(PageRequest.of(page, size, Sort.by("sku"))).getContent();
    }

    @Transactional
    public Product receive(String sku, int quantity) {
        Product product = get(sku);
        products.receive(product.getId(), quantity, clock.instant());
        return products.findById(product.getId()).orElseThrow(() -> new ProductNotFoundException(sku));
    }
}
