import { api, ApiError } from "../api/client.js";
import { $ } from "../utils/dom.js";
import { formatBRL, formatQuantity } from "../utils/format.js";
import { storeUrl } from "../utils/nav.js";

const { slug, publicCode } = currentOrderRef();
const alertBox = $("#order-alert");

if (!slug || !publicCode) {
    showError("Pedido não encontrado.");
} else {
    $("#back-store").href = storeUrl(slug);
    await boot();
}

async function boot() {
    try {
        const order = await api(`/store/${encodeURIComponent(slug)}/orders/${encodeURIComponent(publicCode)}`);
        document.title = `Pedido ${order.publicCode} — Folha`;
        $("#order-code").textContent = order.publicCode;
        $("#order-card").hidden = false;
        $("#order-status").textContent = statusLabel(order.status);
        $("#order-heading").textContent = headingFor(order.status);
        $("#order-meta").textContent = `${fulfillmentLabel(order.fulfillmentType)} · ${paymentLabel(order.paymentMethod)}`;
        renderItems(order.items);
        $("#order-subtotal").textContent = formatBRL(order.subtotal);
        $("#order-total").textContent = formatBRL(order.total);
        renderDetails(order);
    } catch (error) {
        showError(error instanceof ApiError ? error.message : "Não foi possível carregar o pedido.");
    }
}

function renderItems(items) {
    const box = $("#order-items");
    box.replaceChildren();
    items.forEach((item) => {
        const row = document.createElement("div");
        row.className = "order-item-row";
        const left = document.createElement("span");
        left.textContent = `${item.productName} · ${formatQuantity(item.quantity, item.productUnit)}`;
        const right = document.createElement("strong");
        right.textContent = formatBRL(item.subtotal);
        row.append(left, right);
        box.append(row);
    });
}

function renderDetails(order) {
    const box = $("#order-details");
    box.replaceChildren();
    addDetail(box, "Cliente", order.customerName);
    addDetail(box, "Telefone", order.customerPhone);
    if (order.fulfillmentType === "DELIVERY") {
        const address = [
            order.addressStreet,
            order.addressNumber,
            order.addressComplement,
            order.addressNeighborhood,
            order.addressCity,
            order.addressState,
            order.addressZipCode
        ].filter(Boolean).join(", ");
        addDetail(box, "Endereço", address);
    }
    if (order.notes) {
        addDetail(box, "Observações", order.notes);
    }
}

function addDetail(box, label, value) {
    const line = document.createElement("p");
    const strong = document.createElement("strong");
    strong.textContent = `${label}: `;
    line.append(strong, document.createTextNode(value || "—"));
    box.append(line);
}

function currentOrderRef() {
    const params = new URLSearchParams(window.location.search);
    if (params.get("slug") && params.get("code")) {
        return { slug: params.get("slug"), publicCode: params.get("code") };
    }
    const parts = window.location.pathname.split("/").filter(Boolean);
    const pedidoIndex = parts.lastIndexOf("pedido");
    if (pedidoIndex >= 0 && parts[pedidoIndex + 1] && parts[pedidoIndex + 2]) {
        return {
            slug: decodeURIComponent(parts[pedidoIndex + 1]),
            publicCode: decodeURIComponent(parts[pedidoIndex + 2])
        };
    }
    return { slug: "", publicCode: "" };
}

function statusLabel(status) {
    return ({
        PENDING: "Pendente",
        CONFIRMED: "Confirmado",
        PREPARING: "Em preparo",
        DISPATCHED: "Saiu para entrega",
        DELIVERED: "Entregue",
        CANCELLED: "Cancelado"
    })[status] || status;
}

function headingFor(status) {
    return ({
        PENDING: "Pedido recebido",
        CONFIRMED: "Pedido confirmado",
        PREPARING: "Estamos preparando",
        DISPATCHED: "A caminho",
        DELIVERED: "Pedido entregue",
        CANCELLED: "Pedido cancelado"
    })[status] || "Pedido";
}

function fulfillmentLabel(value) {
    return value === "PICKUP" ? "Retirada" : "Entrega";
}

function paymentLabel(value) {
    return ({
        PIX: "PIX",
        CASH: "Dinheiro",
        CARD: "Cartão",
        ON_DELIVERY: "Na entrega"
    })[value] || value;
}

function showError(message) {
    alertBox.hidden = false;
    alertBox.className = "alert alert-error";
    alertBox.textContent = message;
}
