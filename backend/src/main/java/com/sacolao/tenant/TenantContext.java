package com.sacolao.tenant;

import java.util.Optional;
import java.util.UUID;

/**
 * Contexto do tenant da requisição atual.
 *
 * - Painel autenticado: establishment_id extraído do JWT e confirmado no banco.
 * - Loja pública: establishment resolvido pelo slug da URL (Fase 3).
 * - SUPER_ADMIN: contexto vazio; sem acesso operacional implícito.
 *
 * Sempre limpar com {@link #clear()} ao final da requisição.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID establishmentId) {
        CURRENT.set(establishmentId);
    }

    public static Optional<UUID> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static UUID require() {
        return get().orElseThrow(TenantNotBoundException::new);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
