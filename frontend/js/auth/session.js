const ACCESS_KEY = "folha.accessToken";
const USER_KEY = "folha.user";
const REFRESH_KEY = "folha.refreshToken";

/** Persistência longa — sobrevive a fechar o navegador. */
const store = typeof localStorage !== "undefined" ? localStorage : sessionStorage;

function read(key) {
    try {
        return store.getItem(key) ?? sessionStorage.getItem(key);
    } catch {
        return null;
    }
}

function write(key, value) {
    try {
        if (value == null) {
            store.removeItem(key);
            sessionStorage.removeItem(key);
            return;
        }
        store.setItem(key, value);
        sessionStorage.removeItem(key);
    } catch {
        // storage cheio / privado
    }
}

export function getAccessToken() {
    return read(ACCESS_KEY);
}

export function setAccessToken(token) {
    write(ACCESS_KEY, token || null);
}

export function getRefreshToken() {
    return read(REFRESH_KEY);
}

export function setRefreshToken(token) {
    write(REFRESH_KEY, token || null);
}

export function getStoredUser() {
    const raw = read(USER_KEY);
    if (!raw) {
        return null;
    }
    try {
        return JSON.parse(raw);
    } catch {
        return null;
    }
}

export function setStoredUser(user) {
    write(USER_KEY, user ? JSON.stringify(user) : null);
}

export function clearSession() {
    write(ACCESS_KEY, null);
    write(USER_KEY, null);
    write(REFRESH_KEY, null);
}

export function saveAuth(payload) {
    if (!payload) {
        return;
    }
    if (payload.accessToken) {
        setAccessToken(payload.accessToken);
    }
    if (payload.user) {
        setStoredUser(payload.user);
    }
    // Sempre guarda o refresh no storage (além do cookie HttpOnly no same-origin).
    if (payload.refreshToken) {
        setRefreshToken(payload.refreshToken);
    }
}
