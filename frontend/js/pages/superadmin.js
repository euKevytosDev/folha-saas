import { api } from "../api/client.js";
import { logout, requirePageAuth } from "../auth/api.js";
import { $ } from "../utils/dom.js";

const me = await requirePageAuth(["SUPER_ADMIN"]);
if (!me) {
    throw new Error("redirect");
}

$("#user-name").textContent = me.user.name;
$("#logout-button")?.addEventListener("click", () => logout());

const establishments = await api("/admin/establishments");
const list = $("#establishments-list");
list.replaceChildren();
if (!establishments.length) {
    const empty = document.createElement("p");
    empty.className = "muted";
    empty.textContent = "Nenhum estabelecimento cadastrado ainda.";
    list.append(empty);
} else {
    establishments.forEach((item) => {
        const row = document.createElement("article");
        row.className = "user-row";
        const identity = document.createElement("div");
        const name = document.createElement("strong");
        name.textContent = item.name;
        const meta = document.createElement("p");
        meta.className = "muted";
        meta.textContent = `/${item.slug} · ${item.planCode} · ${item.active ? "ativo" : "inativo"}`;
        identity.append(name, meta);
        row.append(identity);
        list.append(row);
    });
}
