import { api } from "../api/client.js";
import { clearSession, getAccessToken, getRefreshToken, getStoredUser, saveAuth } from "./session.js";
import { pageUrl } from "../utils/nav.js";

export async function login(email, password) {
    const payload = await api("/auth/login", {
        method: "POST",
        body: { email, password },
        retry: false
    });
    saveAuth(payload);
    return payload;
}

export async function registerAccount(input) {
    const payload = await api("/auth/register", {
        method: "POST",
        body: input,
        retry: false
    });
    saveAuth(payload);
    return payload;
}

export async function logout() {
    try {
        await api("/auth/logout", {
            method: "POST",
            retry: false,
            body: getRefreshToken() ? { refreshToken: getRefreshToken() } : undefined
        });
    } catch {
        // O cookie/token pode já estar inválido.
    }
    clearSession();
    window.location.href = pageUrl("login");
}

export function redirectAfterLogin(user = getStoredUser()) {
    window.location.href = pageUrl(user?.role === "SUPER_ADMIN" ? "superadmin" : "admin");
}

export async function requirePageAuth(allowedRoles) {
    try {
        const me = await api("/auth/me");
        saveAuth({
            accessToken: getAccessToken(),
            user: me.user
        });
        if (allowedRoles && !allowedRoles.includes(me.user.role)) {
            redirectAfterLogin(me.user);
            return null;
        }
        return me;
    } catch {
        clearSession();
        window.location.href = pageUrl("login");
        return null;
    }
}
