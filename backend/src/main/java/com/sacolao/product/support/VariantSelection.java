package com.sacolao.product.support;

import com.sacolao.common.exception.UnprocessableException;
import com.sacolao.common.util.Money;
import com.sacolao.product.entity.Product;
import com.sacolao.product.entity.ProductVariant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolve as opções escolhidas e o preço da linha.
 * A quantidade mínima/máxima do produto é quantas opções entram no combo
 * (pode repetir o mesmo sabor). O preço do produto é o valor da promoção;
 * o preço do sabor, se houver, soma como acréscimo.
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

        int[] slots = slotLimits(product);
        int min = slots[0];
        int max = slots[1];
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

        String names = formatNames(selected);
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
        BigDecimal total = Money.of(product.getPrice());
        for (ProductVariant variant : selected) {
            if (variant.getPrice() != null) {
                total = total.add(Money.of(variant.getPrice()));
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Mínimo e máximo de opções do combo. Usa a quantidade mín/máx do produto.
     * Combos antigos que só tinham variantMin/Max continuam valendo até serem salvos de novo.
     */
    public static int[] slotLimits(Product product) {
        int qtyMin = 1;
        if (product.getMinimumQuantity() != null && product.getMinimumQuantity().signum() > 0) {
            qtyMin = Math.max(1, product.getMinimumQuantity().setScale(0, RoundingMode.HALF_UP).intValue());
        }
        Integer qtyMax = null;
        if (product.getMaximumQuantity() != null && product.getMaximumQuantity().signum() > 0) {
            qtyMax = Math.max(qtyMin, product.getMaximumQuantity().setScale(0, RoundingMode.HALF_UP).intValue());
        }
        int legacyMin = Math.max(1, product.getVariantMinChoices());
        int legacyMax = Math.max(legacyMin, product.getVariantMaxChoices());
        if (legacyMax > 1 && qtyMin <= 1 && (qtyMax == null || qtyMax <= 1)) {
            return new int[] { legacyMin, legacyMax };
        }
        return new int[] { qtyMin, qtyMax == null ? qtyMin : qtyMax };
    }

    public static List<UUID> normalizeIds(List<UUID> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            return List.of();
        }
        return requestedIds.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
    }

    public static String choiceKey(List<UUID> ids) {
        return normalizeIds(ids).stream().map(UUID::toString).collect(Collectors.joining(","));
    }

    private static String formatNames(List<ProductVariant> selected) {
        List<String> parts = new ArrayList<>();
        String current = null;
        int count = 0;
        for (ProductVariant variant : selected) {
            if (Objects.equals(current, variant.getName())) {
                count++;
                continue;
            }
            if (current != null) {
                parts.add(count > 1 ? current + " ×" + count : current);
            }
            current = variant.getName();
            count = 1;
        }
        if (current != null) {
            parts.add(count > 1 ? current + " ×" + count : current);
        }
        return String.join(" · ", parts);
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
