import { api } from "../api/client.js";
import { logout, requirePageAuth } from "../auth/api.js";
import { $ } from "../utils/dom.js";
import { storeUrl } from "../utils/nav.js";

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
        const link = document.createElement("a");
        link.className = "btn btn-secondary";
        link.href = storeUrl(item.slug);
        link.textContent = "Ver loja";
        row.append(identity, link);
        list.append(row);
    });
}
