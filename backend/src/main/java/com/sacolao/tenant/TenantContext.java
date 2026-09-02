package com.sacolao.tenant;

import java.util.Optional;
import java.util.UUID;

/**
 * Contexto do tenant da requisição atual.
 *
 * Estratégia (FASE 2):
 * - Painel autenticado: establishment_id extraído do JWT.
 * - Loja pública: establishment resolvido pelo slug da URL.
 * - SUPER_ADMIN: contexto vazio, sem acesso operacional a dados do tenant
 *   salvo ação administrativa explícita.
 *
 * O filtro/interceptor deve sempre chamar {@link #clear()} ao final da requisição.
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
        return get().orElseThrow(() -> new IllegalStateException("Tenant não definido para esta requisição"));
    }

    public static void clear() {
        CURRENT.remove();
    }
}
