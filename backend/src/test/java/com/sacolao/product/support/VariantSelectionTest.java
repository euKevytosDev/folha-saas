package com.sacolao.product.support;

import com.sacolao.product.entity.Product;
import com.sacolao.product.entity.ProductUnit;
import com.sacolao.product.entity.ProductVariant;
import com.sacolao.product.entity.VariantPriceMode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VariantSelectionTest {

    @Test
    void extraPriceAddsToProductPrice() {
        Product product = product("10.00");
        ProductVariant extra = variant(product, "Morango", "2.50", VariantPriceMode.EXTRA);
        product.getVariants().add(extra);

        VariantSelection.Resolved resolved = VariantSelection.resolve(product, List.of(extra.getId()));

        assertEquals(new BigDecimal("12.50"), resolved.unitPrice());
    }

    @Test
    void specificPriceReplacesProductPrice() {
        Product product = product("10.00");
        ProductVariant fixed = variant(product, "500 g", "8.00", VariantPriceMode.FIXED);
        product.getVariants().add(fixed);

        VariantSelection.Resolved resolved = VariantSelection.resolve(product, List.of(fixed.getId()));

        assertEquals(new BigDecimal("8.00"), resolved.unitPrice());
    }

    @Test
    void extraStillAddsWhenCombinedWithSpecificPrice() {
        Product product = product("10.00");
        ProductVariant fixed = variant(product, "500 g", "8.00", VariantPriceMode.FIXED);
        ProductVariant extra = variant(product, "Cobertura", "1.50", VariantPriceMode.EXTRA);
        product.getVariants().add(fixed);
        product.getVariants().add(extra);
        product.setVariantMaxChoices(2);
        product.setMaximumQuantity(new BigDecimal("2"));

        VariantSelection.Resolved resolved = VariantSelection.resolve(product, List.of(fixed.getId(), extra.getId()));

        assertEquals(new BigDecimal("9.50"), resolved.unitPrice());
    }

    private static Product product(String price) {
        Product product = new Product();
        product.setName("Produto");
        product.setPrice(new BigDecimal(price));
        product.setUnit(ProductUnit.UN);
        product.setMinimumQuantity(BigDecimal.ONE);
        product.setVariantMinChoices(1);
        product.setVariantMaxChoices(1);
        return product;
    }

    private static ProductVariant variant(Product product, String name, String price, VariantPriceMode mode) {
        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", UUID.randomUUID());
        variant.setProduct(product);
        variant.setName(name);
        variant.setPrice(new BigDecimal(price));
        variant.setPriceMode(mode);
        variant.setAvailable(true);
        return variant;
    }
}
