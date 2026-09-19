import { apiUrl, isCrossOriginApi } from "../config.js";
import { clearSession, getAccessToken, getRefreshToken, saveAuth } from "../auth/session.js";

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

/** Upload multipart (não define Content-Type — o browser envia o boundary). */
export async function apiUpload(path, formData, { retry = true } = {}) {
    const response = await request(path, { method: "POST", body: formData, multipart: true });
    if (response.status === 401 && retry) {
        const refreshed = await refreshAccessToken();
        if (refreshed) {
            return apiUpload(path, formData, { retry: false });
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
        refreshPromise = fetch(apiUrl("/auth/refresh"), {
            method: "POST",
            headers: {
                Accept: "application/json",
                ...(getRefreshToken() ? { "Content-Type": "application/json" } : {})
            },
            credentials: isCrossOriginApi() ? "include" : "same-origin",
            body: getRefreshToken() ? JSON.stringify({ refreshToken: getRefreshToken() }) : undefined
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

function request(path, { method, body, headers, multipart = false }) {
    const publicAuth = path === "/auth/login"
        || path === "/auth/register"
        || path === "/auth/forgot-password"
        || path === "/auth/reset-password";
    const token = publicAuth ? null : getAccessToken();
    const isFormData = multipart || (typeof FormData !== "undefined" && body instanceof FormData);
    return fetch(apiUrl(path), {
        method,
        credentials: isCrossOriginApi() ? "include" : "same-origin",
        headers: {
            Accept: "application/json",
            ...(body && !isFormData ? { "Content-Type": "application/json" } : {}),
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
            ...headers
        },
        body: body ? (isFormData ? body : JSON.stringify(body)) : undefined
    });
}

async function readJson(response) {
    const contentType = response.headers.get("content-type") || "";
    if (!contentType.includes("application/json")) {
        return null;
    }
    return response.json().catch(() => null);
}
