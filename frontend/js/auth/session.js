const ACCESS_KEY = "folha.accessToken";
const USER_KEY = "folha.user";

export function getAccessToken() {
    return sessionStorage.getItem(ACCESS_KEY);
}

export function setAccessToken(token) {
    if (token) {
        sessionStorage.setItem(ACCESS_KEY, token);
        return;
    }
    sessionStorage.removeItem(ACCESS_KEY);
}

export function getStoredUser() {
    const raw = sessionStorage.getItem(USER_KEY);
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
    if (user) {
        sessionStorage.setItem(USER_KEY, JSON.stringify(user));
        return;
    }
    sessionStorage.removeItem(USER_KEY);
}

export function clearSession() {
    sessionStorage.removeItem(ACCESS_KEY);
    sessionStorage.removeItem(USER_KEY);
}

export function saveAuth(payload) {
    setAccessToken(payload.accessToken);
    setStoredUser(payload.user);
}
