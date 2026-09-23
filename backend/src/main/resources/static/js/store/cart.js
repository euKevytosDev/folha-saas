const PREFIX = "folha.cart.";

function key(storeId) {
    return PREFIX + storeId;
}

export function loadCart(storeId) {
    if (!storeId) {
        return [];
    }
    try {
        const raw = localStorage.getItem(key(storeId));
        const parsed = raw ? JSON.parse(raw) : [];
        return Array.isArray(parsed) ? parsed.filter((item) => item?.productId && item.quantity > 0) : [];
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

export function addToCart(storeId, productId, quantity) {
    const items = loadCart(storeId);
    const existing = items.find((item) => item.productId === productId);
    if (existing) {
        existing.quantity = roundQty(existing.quantity + quantity);
    } else {
        items.push({ productId, quantity: roundQty(quantity) });
    }
    saveCart(storeId, items.filter((item) => item.quantity > 0));
    return loadCart(storeId);
}

export function setCartQuantity(storeId, productId, quantity) {
    const qty = roundQty(quantity);
    const items = loadCart(storeId);
    const index = items.findIndex((item) => item.productId === productId);
    if (qty <= 0) {
        if (index >= 0) {
            items.splice(index, 1);
        }
    } else if (index >= 0) {
        items[index] = { ...items[index], quantity: qty };
    } else {
        items.push({ productId, quantity: qty });
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
