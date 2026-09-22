import { config, withBase } from "../config.js";

export function pageUrl(page) {
    if (config.staticHost) {
        const file = page === "home" ? "index.html" : `${page}.html`;
        return withBase(file);
    }
    if (page === "home") {
        return "/";
    }
    return `/${page}`;
}

export function storeUrl(slug) {
    if (config.staticHost) {
        return `${withBase("loja.html")}?slug=${encodeURIComponent(slug)}`;
    }
    return `/loja/${slug}`;
}

export function checkoutUrl(slug) {
    if (config.staticHost) {
        return `${withBase("checkout.html")}?slug=${encodeURIComponent(slug)}`;
    }
    return `/loja/${slug}/checkout`;
}

export function orderUrl(slug, publicCode, viewToken) {
    const tokenQuery = viewToken ? `&token=${encodeURIComponent(viewToken)}` : "";
    if (config.staticHost) {
        return `${withBase("pedido.html")}?slug=${encodeURIComponent(slug)}&code=${encodeURIComponent(publicCode)}${tokenQuery}`;
    }
    const tokenPath = viewToken ? `?token=${encodeURIComponent(viewToken)}` : "";
    return `/pedido/${encodeURIComponent(slug)}/${encodeURIComponent(publicCode)}${tokenPath}`;
}

export function currentStoreSlug() {
    const params = new URLSearchParams(window.location.search);
    if (params.get("slug")) {
        return params.get("slug");
    }
    const parts = window.location.pathname.split("/").filter(Boolean);
    const lojaIndex = parts.lastIndexOf("loja");
    if (lojaIndex >= 0 && parts[lojaIndex + 1]) {
        return decodeURIComponent(parts[lojaIndex + 1]);
    }
    return "";
}
