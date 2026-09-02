import { api, ApiError } from "../api/client.js";
import { logout, requirePageAuth } from "../auth/api.js";
import { $ } from "../utils/dom.js";

const me = await requirePageAuth(["OWNER", "ADMIN", "STAFF"]);
if (!me) {
    throw new Error("redirect");
}

const { user, establishment } = me;
$("#user-name").textContent = user.name;
$("#user-role").textContent = user.role;
$("#store-name").textContent = establishment?.name ?? "Estabelecimento";
$("#store-slug").textContent = establishment ? `/loja/${establishment.slug}` : "";
$("#store-plan").textContent = establishment?.planCode ?? "";
$("#store-status").textContent = establishment?.active ? "Ativo" : "Inativo";
$("#store-link").href = establishment ? `/loja/${establishment.slug}` : "/";

$("#logout-button")?.addEventListener("click", () => logout());

const usersCard = $("#users-card");
if (user.role === "STAFF") {
    usersCard?.setAttribute("hidden", "");
} else {
    if (user.role === "ADMIN") {
        document.querySelector("#member-role option[value='ADMIN']")?.remove();
    }
    await renderUsers();
    $("#user-form")?.addEventListener("submit", async (event) => {
        event.preventDefault();
        const form = event.currentTarget;
        const alertBox = $("#users-alert");
        try {
            await api("/users", {
                method: "POST",
                body: {
                    name: form.name.value,
                    email: form.email.value,
                    password: form.password.value,
                    role: form.role.value
                }
            });
            form.reset();
            if (alertBox) {
                alertBox.hidden = true;
            }
            await renderUsers();
        } catch (error) {
            if (alertBox) {
                alertBox.hidden = false;
                alertBox.className = "alert alert-error";
                alertBox.textContent = error instanceof ApiError ? error.message : "Não foi possível criar o usuário.";
            }
        }
    });
}

async function renderUsers() {
    const list = $("#users-list");
    if (!list) {
        return;
    }
    const users = await api("/users");
    list.replaceChildren();
    users.forEach((item) => {
        const row = document.createElement("div");
        row.className = "user-row";
        const identity = document.createElement("div");
        const name = document.createElement("strong");
        name.textContent = item.name;
        const meta = document.createElement("p");
        meta.className = "muted";
        meta.textContent = `${item.email} · ${item.role}`;
        identity.append(name, meta);
        row.append(identity);
        list.append(row);
    });
}
