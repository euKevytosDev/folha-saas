import { clearSession, getAccessToken, saveAuth, setAccessToken, setStoredUser } from "../auth/session.js";

export class ApiError extends Error {
    constructor(payload, status) {
        super(payload?.message || "Erro inesperado");
        this.name = "ApiError";
        this.payload = payload;
        this.status = status;
    }
}

let refreshPromise = null;

export async function api(path, { method = "GET", body, headers, retry = true } = {}) {
    const response = await request(path, { method, body, headers });
    if (response.status === 401 && retry && !path.startsWith("/auth/")) {
        const refreshed = await refreshAccessToken();
        if (refreshed) {
            return api(path, { method, body, headers, retry: false });
        }
        clearSession();
    }
    const payload = await readJson(response);
    if (!response.ok) {
        throw new ApiError(payload, response.status);
    }
    return payload;
}

export async function refreshAccessToken() {
    if (!refreshPromise) {
        refreshPromise = fetch("/api/v1/auth/refresh", {
            method: "POST",
            headers: { Accept: "application/json" },
            credentials: "same-origin"
        })
            .then(async (response) => {
                if (!response.ok) {
                    return false;
                }
                const payload = await readJson(response);
                saveAuth(payload);
                return true;
            })
            .finally(() => {
                refreshPromise = null;
            });
    }
    return refreshPromise;
}

function request(path, { method, body, headers }) {
    const token = getAccessToken();
    return fetch(`/api/v1${path}`, {
        method,
        credentials: "same-origin",
        headers: {
            Accept: "application/json",
            ...(body ? { "Content-Type": "application/json" } : {}),
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
            ...headers
        },
        body: body ? JSON.stringify(body) : undefined
    });
}

async function readJson(response) {
    const contentType = response.headers.get("content-type") || "";
    if (!contentType.includes("application/json")) {
        return null;
    }
    return response.json().catch(() => null);
}

export { setAccessToken, setStoredUser };
