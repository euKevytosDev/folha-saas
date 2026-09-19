import { api, ApiError } from "../api/client.js";
import { $ } from "../utils/dom.js";
import { formatBRL, formatQuantity } from "../utils/format.js";
import { storeUrl } from "../utils/nav.js";

const { slug, publicCode } = currentOrderRef();
const alertBox = $("#order-alert");
const paymentBox = $("#payment-box");

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
        $("#order-heading").textContent = headingFor(order.status, order.payment);
        $("#order-meta").textContent = `${fulfillmentLabel(order.fulfillmentType)} · ${paymentLabel(order.paymentMethod)}`;
        renderItems(order.items);
        $("#order-subtotal").textContent = formatBRL(order.subtotal);
        $("#order-total").textContent = formatBRL(order.total);
        renderDetails(order);
        renderPayment(order.payment);
    } catch (error) {
        showError(error instanceof ApiError ? error.message : "Não foi possível carregar o pedido.");
    }
}

function renderPayment(payment) {
    if (!paymentBox) {
        return;
    }
    paymentBox.replaceChildren();
    if (!payment) {
        paymentBox.hidden = true;
        return;
    }
    paymentBox.hidden = false;

    const status = document.createElement("p");
    status.innerHTML = `<strong>Pagamento:</strong> ${paymentStatusLabel(payment.status)} · ${payment.provider}`;
    paymentBox.append(status);

    if (payment.pixCopyPaste && payment.status !== "PAID") {
        const label = document.createElement("p");
        label.className = "muted";
        label.textContent = "PIX copia e cola";
        const code = document.createElement("textarea");
        code.className = "pix-code";
        code.readOnly = true;
        code.rows = 4;
        code.value = payment.pixCopyPaste;
        const copy = document.createElement("button");
        copy.type = "button";
        copy.className = "btn btn-secondary";
        copy.textContent = "Copiar PIX";
        copy.addEventListener("click", async () => {
            try {
                await navigator.clipboard.writeText(payment.pixCopyPaste);
                copy.textContent = "Copiado";
            } catch {
                code.select();
            }
        });
        paymentBox.append(label, code, copy);
    }

    if (payment.provider === "MOCK" && payment.status !== "PAID") {
        const simulate = document.createElement("button");
        simulate.type = "button";
        simulate.className = "btn btn-primary";
        simulate.style.marginTop = "0.75rem";
        simulate.textContent = "Simular pagamento (mock)";
        simulate.addEventListener("click", async () => {
            simulate.disabled = true;
            try {
                await api(`/store/${encodeURIComponent(slug)}/orders/${encodeURIComponent(publicCode)}/payment/simulate`, {
                    method: "POST"
                });
                await boot();
            } catch (error) {
                showError(error instanceof ApiError ? error.message : "Falha ao simular pagamento.");
                simulate.disabled = false;
            }
        });
        paymentBox.append(simulate);
    }

    if (payment.status === "PAID") {
        const paid = document.createElement("p");
        paid.className = "muted";
        paid.textContent = "Pagamento confirmado.";
        paymentBox.append(paid);
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
    const idx = parts.indexOf("pedido");
    if (idx >= 0 && parts[idx + 1] && parts[idx + 2]) {
        return { slug: parts[idx + 1], publicCode: parts[idx + 2] };
    }
    return { slug: null, publicCode: null };
}

function showError(message) {
    alertBox.hidden = false;
    alertBox.className = "alert alert-error";
    alertBox.textContent = message;
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

function paymentStatusLabel(status) {
    return ({
        PENDING: "Aguardando",
        AUTHORIZED: "Autorizado",
        PAID: "Pago",
        FAILED: "Falhou",
        REFUNDED: "Estornado",
        CANCELLED: "Cancelado",
        EXPIRED: "Expirado"
    })[status] || status;
}

function headingFor(status, payment) {
    if (payment && payment.status === "PENDING" && payment.method === "PIX") {
        return "Pague o PIX para confirmar";
    }
    return ({
        PENDING: "Pedido recebido",
        CONFIRMED: "Pedido confirmado",
        PREPARING: "Preparando seu pedido",
        DISPATCHED: "Saiu para entrega",
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
