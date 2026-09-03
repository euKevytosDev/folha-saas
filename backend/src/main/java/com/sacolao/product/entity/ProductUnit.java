package com.sacolao.product.entity;

public enum ProductUnit {
    UN,
    KG,
    G,
    L,
    ML,
    CX,
    PCT;

    public boolean decimalAllowed() {
        return this == KG || this == G || this == L || this == ML;
    }
}
