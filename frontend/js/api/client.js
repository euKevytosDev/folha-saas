export class ApiError extends Error {
    constructor(payload, status) {
        super(payload?.message || "Erro inesperado");
        this.name = "ApiError";
        this.payload = payload;
        this.status = status;
    }
}

const TOKEN_KEY = "folha.accessToken";

export function getAccessToken() {
    return sessionStorage.getItem(TOKEN_KEY);
}

export function setAccessToken(token) {
    if (token) {
        sessionStorage.setItem(TOKEN_KEY, token);
        return;
    }
    sessionStorage.removeItem(TOKEN_KEY);
}

export async function api(path, { method = "GET", body, headers } = {}) {
    const token = getAccessToken();
    const response = await fetch(`/api/v1${path}`, {
        method,
        headers: {
            Accept: "application/json",
            ...(body ? { "Content-Type": "application/json" } : {}),
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
            ...headers
        },
        body: body ? JSON.stringify(body) : undefined
    });

    const payload = await response.json().catch(() => null);
    if (!response.ok) {
        throw new ApiError(payload, response.status);
    }
    return payload;
}
