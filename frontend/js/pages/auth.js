import { $, on } from "../utils/dom.js";
import { ApiError } from "../api/client.js";
import { login, redirectAfterLogin, registerAccount } from "../auth/api.js";
import { api } from "../api/client.js";
import { pageUrl } from "../utils/nav.js";

function showAlert(type, message) {
    const alertBox = $(".alert");
    if (!alertBox) {
        return;
    }
    alertBox.hidden = false;
    alertBox.className = `alert alert-${type}`;
    alertBox.textContent = message;
}

function setLoading(form, loading) {
    const button = form.querySelector("button[type='submit']");
    if (!button) {
        return;
    }
    button.disabled = loading;
    button.classList.toggle("is-loading", loading);
}

on($("#login-form"), "submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    setLoading(form, true);
    try {
        const payload = await login(form.email.value, form.password.value);
        redirectAfterLogin(payload.user);
    } catch (error) {
        showAlert("error", error instanceof ApiError ? error.message : "Não foi possível entrar.");
    } finally {
        setLoading(form, false);
    }
});

on($("#signup-form"), "submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    setLoading(form, true);
    try {
        const payload = await registerAccount({
            establishmentName: form.store.value,
            name: form.name.value,
            email: form.email.value,
            password: form.password.value
        });
        redirectAfterLogin(payload.user);
    } catch (error) {
        showAlert("error", error instanceof ApiError ? error.message : "Não foi possível criar a loja.");
    } finally {
        setLoading(form, false);
    }
});

on($("#recover-form"), "submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    setLoading(form, true);
    try {
        const payload = await api("/auth/forgot-password", {
            method: "POST",
            body: { email: form.email.value },
            retry: false
        });
        showAlert("success", payload.message);
    } catch (error) {
        showAlert("error", error instanceof ApiError ? error.message : "Não foi possível enviar o pedido.");
    } finally {
        setLoading(form, false);
    }
});

on($("#reset-form"), "submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    setLoading(form, true);
    try {
        await api("/auth/reset-password", {
            method: "POST",
            body: {
                token: form.token.value,
                password: form.password.value
            },
            retry: false
        });
        showAlert("success", "Senha atualizada. Você já pode entrar.");
        window.setTimeout(() => {
            window.location.href = pageUrl("login");
        }, 1200);
    } catch (error) {
        showAlert("error", error instanceof ApiError ? error.message : "Não foi possível redefinir a senha.");
    } finally {
        setLoading(form, false);
    }
});

const tokenInput = $("#token");
if (tokenInput) {
    const params = new URLSearchParams(window.location.search);
    const token = params.get("token");
    if (token) {
        tokenInput.value = token;
    }
}
