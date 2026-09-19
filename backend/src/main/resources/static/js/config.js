const raw = window.__FOLHA__ || {};

export const config = {
    apiBaseUrl: String(raw.apiBaseUrl || "").replace(/\/$/, ""),
    basePath: String(raw.basePath || "").replace(/\/$/, ""),
    staticHost: Boolean(raw.staticHost)
};

export function isCrossOriginApi() {
    return Boolean(config.apiBaseUrl);
}

export function apiUrl(path) {
    return `${config.apiBaseUrl}/api/v1${path}`;
}

export function withBase(path) {
    const clean = path.startsWith("/") ? path : `/${path}`;
    return `${config.basePath}${clean}`;
}
