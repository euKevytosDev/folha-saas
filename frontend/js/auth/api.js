import { api } from "../api/client.js";
import { clearSession, getAccessToken, getStoredUser, saveAuth } from "./session.js";

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
        await api("/auth/logout", { method: "POST", retry: false });
    } catch {
        // O cookie/token pode já estar inválido.
    }
    clearSession();
    window.location.href = "/login";
}

export function redirectAfterLogin(user = getStoredUser()) {
    window.location.href = user?.role === "SUPER_ADMIN" ? "/superadmin" : "/admin";
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
        window.location.href = "/login";
        return null;
    }
}
