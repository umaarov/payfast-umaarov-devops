package com.umaarov.logistics.warehouse.api;

import com.umaarov.logistics.warehouse.domain.Product;
import com.umaarov.logistics.warehouse.domain.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/warehouse/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        Product product = productService.create(request.sku(), request.name(), request.initialQuantity());
        return ResponseEntity
                .created(URI.create("/api/warehouse/products/" + product.getSku()))
                .body(ProductResponse.from(product));
    }

    @GetMapping
    public List<ProductResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return productService.list(page, size).stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{sku}")
    public ProductResponse get(@PathVariable String sku) {
        return ProductResponse.from(productService.get(sku));
    }

    @PostMapping("/{sku}/receipts")
    public ProductResponse receive(@PathVariable String sku, @Valid @RequestBody ReceiptRequest request) {
        return ProductResponse.from(productService.receive(sku, request.quantity()));
    }
}
