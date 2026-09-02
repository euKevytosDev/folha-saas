# Folha — sessão de autenticação (FASE 2)
export function logout() {
    sessionStorage.removeItem("folha.accessToken");
    sessionStorage.removeItem("folha.refreshToken");
    window.location.href = "/login";
}
