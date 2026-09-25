const PREFIX = "folha.cart.";

function key(storeId) {
    return PREFIX + storeId;
}

function lineKey(productId, variantId) {
    return `${productId}::${variantId || ""}`;
}

function sameLine(item, productId, variantId) {
    return item.productId === productId && (item.variantId || null) === (variantId || null);
}

export function loadCart(storeId) {
    if (!storeId) {
        return [];
    }
    try {
        const raw = localStorage.getItem(key(storeId));
        const parsed = raw ? JSON.parse(raw) : [];
        return Array.isArray(parsed)
            ? parsed.filter((item) => item?.productId && item.quantity > 0)
            : [];
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

export function addToCart(storeId, productId, quantity, variantId = null) {
    const items = loadCart(storeId);
    const existing = items.find((item) => sameLine(item, productId, variantId));
    if (existing) {
        existing.quantity = roundQty(existing.quantity + quantity);
    } else {
        items.push({
            productId,
            variantId: variantId || null,
            quantity: roundQty(quantity)
        });
    }
    saveCart(storeId, items.filter((item) => item.quantity > 0));
    return loadCart(storeId);
}

export function setCartQuantity(storeId, productId, quantity, variantId = null) {
    const qty = roundQty(quantity);
    const items = loadCart(storeId);
    const index = items.findIndex((item) => sameLine(item, productId, variantId));
    if (qty <= 0) {
        if (index >= 0) {
            items.splice(index, 1);
        }
    } else if (index >= 0) {
        items[index] = { ...items[index], quantity: qty, variantId: variantId || null };
    } else {
        items.push({ productId, variantId: variantId || null, quantity: qty });
    }
    saveCart(storeId, items);
    return items;
}

export function cartCount(items) {
    return items.reduce((total, item) => total + Number(item.quantity || 0), 0);
}

export function cartLineKey(item) {
    return lineKey(item.productId, item.variantId);
}

function roundQty(value) {
    return Math.round(Number(value) * 1000) / 1000;
}
