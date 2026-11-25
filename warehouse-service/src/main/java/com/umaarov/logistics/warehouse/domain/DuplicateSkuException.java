package com.umaarov.logistics.warehouse.domain;

public class DuplicateSkuException extends RuntimeException {

    public DuplicateSkuException(String sku) {
        super("Product with sku " + sku + " already exists");
    }
}
