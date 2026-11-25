package com.umaarov.logistics.warehouse.domain;

public class ProductNotFoundException extends RuntimeException {

    private final String sku;

    public ProductNotFoundException(String sku) {
        super("Product with sku " + sku + " does not exist");
        this.sku = sku;
    }

    public String getSku() {
        return sku;
    }
}
