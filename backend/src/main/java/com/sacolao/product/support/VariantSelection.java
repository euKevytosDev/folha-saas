package com.sacolao.product.support;

import com.sacolao.common.exception.UnprocessableException;
import com.sacolao.common.util.Money;
import com.sacolao.product.entity.Product;
import com.sacolao.product.entity.ProductVariant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolve sabores escolhidos (1..N) e calcula o preço da linha.
 * <ul>
 *   <li>1 sabor: preço próprio do sabor, ou preço do produto se o sabor não tiver preço</li>
 *   <li>Vários sabores (combo): preço do produto + acréscimos opcionais de cada sabor</li>
 * </ul>
 */
public final class VariantSelection {

    private VariantSelection() {
    }

    public static Resolved resolve(Product product, List<UUID> requestedIds) {
        List<UUID> ids = normalizeIds(requestedIds);
        if (!product.hasAvailableVariants()) {
            if (!ids.isEmpty()) {
                throw new UnprocessableException(
                        "VARIANT_NOT_ALLOWED",
                        "Este produto não possui sabores/opções"
                );
            }
            return new Resolved(List.of(), null, null, product.getName(), Money.of(product.getPrice()));
        }

        int min = Math.max(1, product.getVariantMinChoices());
        int max = Math.max(min, product.getVariantMaxChoices());
        if (ids.isEmpty()) {
            throw new UnprocessableException(
                    "VARIANT_REQUIRED",
                    "Escolha " + choiceHint(min, max) + " para " + product.getName()
            );
        }
        if (ids.size() < min || ids.size() > max) {
            throw new UnprocessableException(
                    "VARIANT_CHOICE_COUNT",
                    "Escolha " + choiceHint(min, max) + " para " + product.getName()
            );
        }

        List<ProductVariant> selected = new ArrayList<>();
        for (UUID id : ids) {
            ProductVariant variant = product.getVariants().stream()
                    .filter(item -> Objects.equals(item.getId(), id))
                    .filter(ProductVariant::isAvailable)
                    .findFirst()
                    .orElseThrow(() -> new UnprocessableException(
                            "VARIANT_UNAVAILABLE",
                            "Sabor/opção indisponível para " + product.getName()
                    ));
            selected.add(variant);
        }

        String names = selected.stream().map(ProductVariant::getName).collect(Collectors.joining(" · "));
        String lineName = product.getName() + " · " + names;
        UUID firstId = selected.getFirst().getId();
        BigDecimal unitPrice = priceFor(product, selected);
        return new Resolved(
                selected.stream().map(ProductVariant::getId).toList(),
                firstId,
                names,
                lineName,
                unitPrice
        );
    }

    public static String softValidate(Product product, List<UUID> requestedIds) {
        try {
            resolve(product, requestedIds);
            return null;
        } catch (UnprocessableException ex) {
            return ex.getMessage();
        }
    }

    private static BigDecimal priceFor(Product product, List<ProductVariant> selected) {
        boolean multi = selected.size() > 1 || product.getVariantMaxChoices() > 1;
        if (!multi) {
            ProductVariant only = selected.getFirst();
            if (only.getPrice() != null) {
                return Money.of(only.getPrice());
            }
            return Money.of(product.getPrice());
        }
        BigDecimal total = Money.of(product.getPrice());
        for (ProductVariant variant : selected) {
            if (variant.getPrice() != null) {
                total = total.add(Money.of(variant.getPrice()));
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public static List<UUID> normalizeIds(List<UUID> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> unique = new LinkedHashSet<>();
        for (UUID id : requestedIds) {
            if (id != null) {
                unique.add(id);
            }
        }
        return unique.stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
    }

    public static String choiceKey(List<UUID> ids) {
        return normalizeIds(ids).stream().map(UUID::toString).collect(Collectors.joining(","));
    }

    private static String choiceHint(int min, int max) {
        if (min == max) {
            return min == 1 ? "1 sabor/opção" : min + " sabores/opções";
        }
        return "de " + min + " a " + max + " sabores/opções";
    }

    public record Resolved(
            List<UUID> variantIds,
            UUID primaryVariantId,
            String variantName,
            String lineName,
            BigDecimal unitPrice
    ) {
    }
}
