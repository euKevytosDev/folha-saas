const PREFIX = "folha.myOrders.";
const MAX_ORDERS = 30;

export function rememberOrder(storeId, entry) {
    if (!storeId || !entry?.code || !entry?.token) {
        return;
    }
    const next = loadRemembered(storeId).filter((item) => item.code !== entry.code);
    next.unshift({
        code: String(entry.code),
        token: String(entry.token),
        at: entry.at || new Date().toISOString()
    });
    try {
        localStorage.setItem(PREFIX + storeId, JSON.stringify(next.slice(0, MAX_ORDERS)));
    } catch {
        // o celular pode recusar gravar; o pedido continua no link atual
    }
}

export function loadRemembered(storeId) {
    if (!storeId) {
        return [];
    }
    try {
        const raw = JSON.parse(localStorage.getItem(PREFIX + storeId) || "[]");
        if (!Array.isArray(raw)) {
            return [];
        }
        return raw.filter((item) => item && item.code && item.token);
    } catch {
        return [];
    }
}

export function forgetOrder(storeId, code) {
    if (!storeId || !code) {
        return;
    }
    const next = loadRemembered(storeId).filter((item) => item.code !== code);
    try {
        localStorage.setItem(PREFIX + storeId, JSON.stringify(next));
    } catch {
        // ignora falha de gravação
    }
}
