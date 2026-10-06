import { api } from "../api/client.js";
import { forgetOrder, loadRemembered } from "../store/my-orders.js";
import { $ } from "../utils/dom.js";
import { formatBRL } from "../utils/format.js";
import { currentStoreSlug, orderUrl, storeUrl } from "../utils/nav.js";

const slug = currentStoreSlug();
const list = $("#orders-list");
const empty = $("#orders-empty");
const alertBox = $("#orders-alert");

if (!slug) {
    showError("Loja não encontrada.");
} else {
    $("#back-store").href = storeUrl(slug);
    await boot();
}

async function boot() {
    try {
        const catalog = await api(`/store/${encodeURIComponent(slug)}/catalog`);
        const storeId = catalog.store?.id;
        document.title = `Meus pedidos — ${catalog.store?.name || "MaxPedidos"}`;
        const saved = loadRemembered(storeId);
        if (!saved.length) {
            empty.textContent = "Nenhum pedido neste celular. Quando você finalizar um, ele aparece aqui.";
            return;
        }
        const orders = [];
        await Promise.all(saved.map(async (savedOrder) => {
            try {
                const order = await api(
                    `/store/${encodeURIComponent(slug)}/orders/${encodeURIComponent(savedOrder.code)}?token=${encodeURIComponent(savedOrder.token)}`
                );
                orders.push({ order, token: savedOrder.token });
            } catch {
                forgetOrder(storeId, savedOrder.code);
            }
        }));
        orders.sort((a, b) => String(b.order.createdAt || "").localeCompare(String(a.order.createdAt || "")));
        if (!orders.length) {
            empty.textContent = "Nenhum pedido neste celular. Quando você finalizar um, ele aparece aqui.";
            return;
        }
        empty.hidden = true;
        orders.forEach(({ order, token }) => {
            const link = document.createElement("a");
            link.className = "my-order";
            link.href = orderUrl(slug, order.publicCode, order.viewToken || token);
            const copy = document.createElement("span");
            const title = document.createElement("strong");
            title.textContent = order.publicCode;
            const meta = document.createElement("span");
            meta.className = "muted";
            meta.textContent = `${statusLabel(order.status)} · ${formatWhen(order.createdAt)}`;
            copy.append(title, meta);
            const total = document.createElement("strong");
            total.textContent = formatBRL(order.total);
            link.append(copy, total);
            list.append(link);
        });
    } catch {
        showError("Não foi possível abrir seus pedidos.");
        empty.hidden = true;
    }
}

function statusLabel(status) {
    return ({
        PENDING: "Pendente",
        CONFIRMED: "Confirmado",
        PREPARING: "Em preparo",
        DISPATCHED: "Saiu para entrega",
        DELIVERED: "Entregue",
        CANCELLED: "Cancelado"
    })[status] || status || "Pedido";
}

function formatWhen(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return "";
    }
    return date.toLocaleString("pt-BR", {
        timeZone: "America/Sao_Paulo",
        day: "2-digit",
        month: "2-digit",
        hour: "2-digit",
        minute: "2-digit"
    });
}

function showError(message) {
    alertBox.hidden = false;
    alertBox.className = "alert alert-error";
    alertBox.textContent = message;
}
