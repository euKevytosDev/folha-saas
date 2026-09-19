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
    const items = loadCart(storeId).filter((item) => item.productId !== productId);
    if (quantity > 0) {
        items.push({ productId, quantity: roundQty(quantity) });
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
