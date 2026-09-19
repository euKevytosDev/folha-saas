import { api, ApiError } from "../api/client.js";
import { logout, requirePageAuth } from "../auth/api.js";
import { $ } from "../utils/dom.js";
import { formatBRL } from "../utils/format.js";
import { storeUrl } from "../utils/nav.js";

const me = await requirePageAuth(["OWNER", "ADMIN", "STAFF"]);
if (!me) {
    throw new Error("redirect");
}

const { user, establishment } = me;
$("#user-name").textContent = user.name;
$("#user-role").textContent = user.role;
$("#store-name").textContent = establishment?.name ?? "Estabelecimento";
$("#store-slug").textContent = establishment ? storeUrl(establishment.slug) : "";
$("#store-plan").textContent = establishment?.planCode ?? "";
$("#store-status").textContent = establishment?.active ? "Ativo" : "Inativo";
$("#store-link").href = establishment ? storeUrl(establishment.slug) : "/";

$("#logout-button")?.addEventListener("click", () => logout());

const orderState = { status: null };
await refreshOrders();
renderOrderFilters();

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
            hideAlert(alertBox);
            await renderUsers();
        } catch (error) {
            showFormAlert(alertBox, error, "Não foi possível criar o usuário.");
        }
    });
}

await refreshCatalog();
$("#category-form")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    try {
        await api("/categories", {
            method: "POST",
            body: {
                name: form.name.value,
                sortOrder: Number(form.sortOrder.value || 0)
            }
        });
        form.reset();
        hideAlert($("#category-alert"));
        await refreshCatalog();
    } catch (error) {
        showFormAlert($("#category-alert"), error, "Não foi possível salvar a categoria.");
    }
});

$("#product-form")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    try {
        await api("/products", {
            method: "POST",
            body: {
                name: form.name.value,
                categoryId: form.categoryId.value,
                price: Number(form.price.value),
                unit: form.unit.value,
                imageUrl: form.imageUrl.value || null,
                featured: form.featured.checked,
                available: true,
                minimumQuantity: form.unit.value === "KG" ? 0.2 : 1
            }
        });
        form.reset();
        hideAlert($("#product-alert"));
        await refreshCatalog();
    } catch (error) {
        showFormAlert($("#product-alert"), error, "Não foi possível salvar o produto.");
    }
});

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

async function refreshCatalog() {
    const [categories, products] = await Promise.all([api("/categories"), api("/products")]);
    renderCategoryOptions(categories);
    renderCategories(categories);
    renderProducts(products);
}

function renderCategoryOptions(categories) {
    const select = $("#product-category");
    if (!select) {
        return;
    }
    select.replaceChildren();
    categories.filter((item) => item.active).forEach((item) => {
        const option = document.createElement("option");
        option.value = item.id;
        option.textContent = item.name;
        select.append(option);
    });
}

function renderCategories(categories) {
    const list = $("#categories-list");
    list.replaceChildren();
    if (!categories.length) {
        const empty = document.createElement("p");
        empty.className = "muted";
        empty.textContent = "Nenhuma categoria ainda.";
        list.append(empty);
        return;
    }
    categories.forEach((item) => {
        const row = document.createElement("div");
        row.className = "user-row";
        const name = document.createElement("strong");
        name.textContent = item.name;
        const actions = document.createElement("button");
        actions.className = "btn btn-ghost";
        actions.type = "button";
        actions.textContent = item.active ? "Ocultar" : "Ativar";
        actions.addEventListener("click", async () => {
            await api(`/categories/${item.id}`, {
                method: "PUT",
                body: { active: !item.active }
            });
            await refreshCatalog();
        });
        row.append(name, actions);
        list.append(row);
    });
}

function renderProducts(products) {
    const list = $("#products-list");
    list.replaceChildren();
    if (!products.length) {
        const empty = document.createElement("p");
        empty.className = "muted";
        empty.textContent = "Nenhum produto ainda.";
        list.append(empty);
        return;
    }
    products.forEach((item) => {
        const row = document.createElement("div");
        row.className = "product-admin-row";
        const title = document.createElement("strong");
        title.textContent = item.name;
        const meta = document.createElement("p");
        meta.className = "muted";
        meta.textContent = `${item.categoryName} · ${formatBRL(item.price)} / ${item.unit} · ${item.available ? "à venda" : "oculto"}`;
        const actions = document.createElement("div");
        actions.style.display = "flex";
        actions.style.gap = "0.4rem";
        actions.append(
            flagButton(item.available ? "Pausar" : "Publicar", `/products/${item.id}/availability`, !item.available),
            flagButton(item.featured ? "Tirar destaque" : "Destacar", `/products/${item.id}/featured`, !item.featured)
        );
        row.append(title, meta, actions);
        list.append(row);
    });
}

function flagButton(label, path, value) {
    const button = document.createElement("button");
    button.className = "btn btn-secondary";
    button.type = "button";
    button.textContent = label;
    button.addEventListener("click", async () => {
        await api(path, { method: "PATCH", body: { value } });
        await refreshCatalog();
    });
    return button;
}

function showFormAlert(alertBox, error, fallback) {
    if (!alertBox) {
        return;
    }
    alertBox.hidden = false;
    alertBox.className = "alert alert-error";
    alertBox.textContent = error instanceof ApiError ? error.message : fallback;
}

function hideAlert(alertBox) {
    if (alertBox) {
        alertBox.hidden = true;
    }
}

async function refreshOrders() {
    const query = orderState.status ? `?status=${orderState.status}` : "";
    const [summary, orders] = await Promise.all([
        api("/orders/summary"),
        api(`/orders${query}`)
    ]);
    renderOrderSummary(summary);
    renderOrders(orders);
}

function renderOrderFilters() {
    const row = $("#order-filters");
    if (!row) {
        return;
    }
    row.replaceChildren();
    const filters = [
        { label: "Todos", value: null },
        { label: "Pendentes", value: "PENDING" },
        { label: "Confirmados", value: "CONFIRMED" },
        { label: "Preparo", value: "PREPARING" },
        { label: "Enviados", value: "DISPATCHED" },
        { label: "Entregues", value: "DELIVERED" },
        { label: "Cancelados", value: "CANCELLED" }
    ];
    filters.forEach((filter) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = `chip${orderState.status === filter.value ? " is-active" : ""}`;
        button.textContent = filter.label;
        button.addEventListener("click", async () => {
            orderState.status = filter.value;
            renderOrderFilters();
            await refreshOrders();
        });
        row.append(button);
    });
}

function renderOrderSummary(summary) {
    const box = $("#order-summary");
    if (!box) {
        return;
    }
    box.replaceChildren();
    [
        ["Total", summary.total],
        ["Pendentes", summary.pending],
        ["Preparo", summary.preparing],
        ["Entregues", summary.delivered]
    ].forEach(([label, value]) => {
        const card = document.createElement("div");
        card.className = "stat-card";
        const strong = document.createElement("strong");
        strong.textContent = String(value ?? 0);
        const meta = document.createElement("span");
        meta.className = "muted";
        meta.textContent = label;
        card.append(strong, meta);
        box.append(card);
    });
}

function renderOrders(orders) {
    const list = $("#orders-list");
    if (!list) {
        return;
    }
    list.replaceChildren();
    if (!orders.length) {
        const empty = document.createElement("p");
        empty.className = "muted";
        empty.textContent = "Nenhum pedido neste filtro.";
        list.append(empty);
        return;
    }
    orders.forEach((order) => {
        const row = document.createElement("div");
        row.className = "order-admin-row";
        const title = document.createElement("strong");
        title.textContent = `${order.publicCode} · ${order.customerName}`;
        const meta = document.createElement("p");
        meta.className = "muted";
        meta.textContent = `${statusLabel(order.status)} · ${formatBRL(order.total)} · ${fulfillmentLabel(order.fulfillmentType)} · ${order.customerPhone}`;
        const actions = document.createElement("div");
        actions.className = "order-admin-actions";
        nextStatuses(order.status).forEach((next) => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = next === "CANCELLED" ? "btn btn-secondary" : "btn btn-primary";
            button.textContent = actionLabel(next);
            button.addEventListener("click", async () => {
                try {
                    await api(`/orders/${order.id}/status`, {
                        method: "PATCH",
                        body: { status: next }
                    });
                    hideAlert($("#orders-alert"));
                    await refreshOrders();
                } catch (error) {
                    showFormAlert($("#orders-alert"), error, "Não foi possível atualizar o status.");
                }
            });
            actions.append(button);
        });
        row.append(title, meta, actions);
        list.append(row);
    });
}

function nextStatuses(status) {
    return ({
        PENDING: ["CONFIRMED", "CANCELLED"],
        CONFIRMED: ["PREPARING", "CANCELLED"],
        PREPARING: ["DISPATCHED", "CANCELLED"],
        DISPATCHED: ["DELIVERED"],
        DELIVERED: [],
        CANCELLED: []
    })[status] || [];
}

function statusLabel(status) {
    return ({
        PENDING: "Pendente",
        CONFIRMED: "Confirmado",
        PREPARING: "Em preparo",
        DISPATCHED: "Enviado",
        DELIVERED: "Entregue",
        CANCELLED: "Cancelado"
    })[status] || status;
}

function actionLabel(status) {
    return ({
        CONFIRMED: "Confirmar",
        PREPARING: "Preparar",
        DISPATCHED: "Despachar",
        DELIVERED: "Entregar",
        CANCELLED: "Cancelar"
    })[status] || status;
}

function fulfillmentLabel(value) {
    return value === "PICKUP" ? "Retirada" : "Entrega";
}

