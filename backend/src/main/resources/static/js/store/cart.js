const PREFIX = "folha.cart.";

function key(storeId) {
    return PREFIX + storeId;
}

function normalizeVariantIds(variantIds) {
    const list = Array.isArray(variantIds)
        ? variantIds
        : (variantIds ? [variantIds] : []);
    return [...new Set(list.filter(Boolean).map(String))].sort();
}

function choiceKey(productId, variantIds) {
    return `${productId}::${normalizeVariantIds(variantIds).join(",")}`;
}

function sameLine(item, productId, variantIds) {
    return choiceKey(item.productId, item.variantIds || (item.variantId ? [item.variantId] : []))
        === choiceKey(productId, variantIds);
}

export function loadCart(storeId) {
    if (!storeId) {
        return [];
    }
    try {
        const raw = localStorage.getItem(key(storeId));
        const parsed = raw ? JSON.parse(raw) : [];
        if (!Array.isArray(parsed)) {
            return [];
        }
        return parsed
            .filter((item) => item?.productId && item.quantity > 0)
            .map((item) => ({
                productId: item.productId,
                variantId: item.variantId || (item.variantIds?.[0] ?? null),
                variantIds: normalizeVariantIds(item.variantIds || (item.variantId ? [item.variantId] : [])),
                quantity: item.quantity
            }));
    } catch {
        return [];
    }
}

export function saveCart(storeId, items) {
    localStorage.setItem(key(storeId), JSON.stringify(items));
}

export function clearCart(storeId) {
    localStorage.removeItem(key(storeId));
}

export function addToCart(storeId, productId, quantity, variantIds = null) {
    const ids = normalizeVariantIds(variantIds);
    const items = loadCart(storeId);
    const existing = items.find((item) => sameLine(item, productId, ids));
    if (existing) {
        existing.quantity = roundQty(existing.quantity + quantity);
    } else {
        items.push({
            productId,
            variantId: ids[0] || null,
            variantIds: ids,
            quantity: roundQty(quantity)
        });
    }
    saveCart(storeId, items.filter((item) => item.quantity > 0));
    return loadCart(storeId);
}

export function setCartQuantity(storeId, productId, quantity, variantIds = null) {
    const qty = roundQty(quantity);
    const ids = normalizeVariantIds(variantIds);
    const items = loadCart(storeId);
    const index = items.findIndex((item) => sameLine(item, productId, ids));
    if (qty <= 0) {
        if (index >= 0) {
            items.splice(index, 1);
        }
    } else if (index >= 0) {
        items[index] = {
            ...items[index],
            quantity: qty,
            variantId: ids[0] || null,
            variantIds: ids
        };
    } else {
        items.push({
            productId,
            variantId: ids[0] || null,
            variantIds: ids,
            quantity: qty
        });
    }
    saveCart(storeId, items);
    return items;
}

export function cartCount(items) {
    return items.reduce((total, item) => total + Number(item.quantity || 0), 0);
}

function roundQty(value) {
    return Math.round(Number(value) * 1000) / 1000;
}
