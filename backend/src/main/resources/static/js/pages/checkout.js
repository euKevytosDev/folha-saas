import { api, ApiError } from "../api/client.js";
import { clearCart, loadCart } from "../store/cart.js";
import { $, on } from "../utils/dom.js";
import { formatBRL, formatQuantity } from "../utils/format.js";
import { currentStoreSlug, orderUrl, storeUrl } from "../utils/nav.js";

const slug = currentStoreSlug();
const state = {
    store: null,
    quote: null
};

const form = $("#checkout-form");
const alertBox = $("#checkout-alert");

if (!slug) {
    showError("Loja não encontrada.");
} else {
    await boot();
}

async function boot() {
    try {
        const catalog = await api(`/store/${encodeURIComponent(slug)}/catalog`);
        state.store = catalog.store;
        document.title = `Checkout — ${state.store.name}`;
        $("#store-name").textContent = state.store.name;
        $("#back-store").href = storeUrl(slug);

        const items = loadCart(state.store.id);
        if (!items.length) {
            showError("Seu carrinho está vazio.");
            form.hidden = true;
            return;
        }

        state.quote = await api(`/store/${encodeURIComponent(slug)}/cart/quote`, {
            method: "POST",
            body: { items }
        });
        const valid = state.quote.items.filter((line) => !line.issue);
        if (!valid.length) {
            showError("Nenhum item disponível no carrinho.");
            form.hidden = true;
            return;
        }
        renderSummary(valid);
        syncAddressVisibility();
    } catch (error) {
        showError(error instanceof ApiError ? error.message : "Não foi possível abrir o checkout.");
        form.hidden = true;
    }
}

function renderSummary(lines) {
    const box = $("#summary-lines");
    box.replaceChildren();
    lines.forEach((line) => {
        const row = document.createElement("div");
        row.className = "summary-line";
        const left = document.createElement("span");
        left.textContent = `${line.name} · ${formatQuantity(line.quantity, line.unit)}`;
        const right = document.createElement("strong");
        right.textContent = formatBRL(line.subtotal);
        row.append(left, right);
        box.append(row);
    });
    $("#summary-subtotal").textContent = formatBRL(state.quote.subtotal);
    $("#summary-discount").textContent = formatBRL(state.quote.discount);
    $("#summary-delivery").textContent = formatBRL(state.quote.deliveryFee);
    $("#summary-total").textContent = formatBRL(state.quote.total);
}

function syncAddressVisibility() {
    const delivery = form.fulfillmentType.value === "DELIVERY";
    $("#address-fields").hidden = !delivery;
    ["street", "number", "neighborhood", "city", "state"].forEach((id) => {
        $(`#${id}`).required = delivery;
    });
}

on(form, "change", (event) => {
    if (event.target.name === "fulfillmentType") {
        syncAddressVisibility();
    }
});

on(form, "submit", async (event) => {
    event.preventDefault();
    const button = $("#submit-order");
    button.classList.add("is-loading");
    button.disabled = true;
    hideAlert();
    try {
        const items = loadCart(state.store.id)
            .map((item) => ({ productId: item.productId, quantity: item.quantity }));
        const payload = {
            items,
            customerName: form.customerName.value.trim(),
            customerPhone: form.customerPhone.value.trim(),
            customerEmail: form.customerEmail.value.trim() || null,
            fulfillmentType: form.fulfillmentType.value,
            paymentMethod: form.paymentMethod.value,
            notes: form.notes.value.trim() || null
        };
        if (payload.fulfillmentType === "DELIVERY") {
            payload.addressZipCode = form.addressZipCode.value.trim() || null;
            payload.addressStreet = form.addressStreet.value.trim();
            payload.addressNumber = form.addressNumber.value.trim();
            payload.addressComplement = form.addressComplement.value.trim() || null;
            payload.addressNeighborhood = form.addressNeighborhood.value.trim();
            payload.addressCity = form.addressCity.value.trim();
            payload.addressState = form.addressState.value.trim().toUpperCase();
        }
        const order = await api(`/store/${encodeURIComponent(slug)}/orders`, {
            method: "POST",
            body: payload
        });
        clearCart(state.store.id);
        window.location.href = orderUrl(slug, order.publicCode);
    } catch (error) {
        showError(error instanceof ApiError ? error.message : "Não foi possível finalizar o pedido.");
        button.classList.remove("is-loading");
        button.disabled = false;
    }
});

function showError(message) {
    alertBox.hidden = false;
    alertBox.className = "alert alert-error";
    alertBox.textContent = message;
}

function hideAlert() {
    alertBox.hidden = true;
}
