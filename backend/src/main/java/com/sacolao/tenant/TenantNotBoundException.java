package com.sacolao.tenant;

public class TenantNotBoundException extends RuntimeException {

    public TenantNotBoundException() {
        super("Operação requer um estabelecimento");
    }
}
