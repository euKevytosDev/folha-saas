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
const cepHint = $("#cep-hint");
const couponHint = $("#coupon-hint");

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

        applyFulfillmentOptions(state.store.delivery);
        await refreshQuote();
        const valid = (state.quote?.items || []).filter((line) => !line.issue);
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

function applyFulfillmentOptions(delivery) {
    const deliveryRadio = form.querySelector('input[name="fulfillmentType"][value="DELIVERY"]');
    const pickupRadio = form.querySelector('input[name="fulfillmentType"][value="PICKUP"]');
    if (delivery && delivery.deliveryEnabled === false && deliveryRadio) {
        deliveryRadio.disabled = true;
        deliveryRadio.closest("label")?.classList.add("is-disabled");
    }
    if (delivery && delivery.pickupEnabled === false && pickupRadio) {
        pickupRadio.disabled = true;
        pickupRadio.closest("label")?.classList.add("is-disabled");
    }
    if (deliveryRadio?.disabled && pickupRadio && !pickupRadio.disabled) {
        pickupRadio.checked = true;
    }
    if (pickupRadio?.disabled && deliveryRadio && !deliveryRadio.disabled) {
        deliveryRadio.checked = true;
    }
}

async function refreshQuote() {
    const items = loadCart(state.store.id);
    state.quote = await api(`/store/${encodeURIComponent(slug)}/cart/quote`, {
        method: "POST",
        body: {
            items,
            fulfillmentType: form.fulfillmentType.value,
            couponCode: form.couponCode?.value?.trim() || null
        }
    });
    if (couponHint) {
        couponHint.textContent = state.quote.couponMessage || "";
        couponHint.className = state.quote.couponCode ? "muted ok-hint" : "muted";
    }
    const valid = state.quote.items.filter((line) => !line.issue);
    renderSummary(valid);
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

async function lookupCep(raw) {
    const cep = String(raw || "").replace(/\D/g, "");
    if (cep.length !== 8) {
        return;
    }
    if (cepHint) {
        cepHint.textContent = "Buscando endereço…";
    }
    try {
        const response = await fetch(`https://viacep.com.br/ws/${cep}/json/`);
        if (!response.ok) {
            throw new Error("CEP indisponível");
        }
        const data = await response.json();
        if (data.erro) {
            if (cepHint) {
                cepHint.textContent = "CEP não encontrado.";
            }
            return;
        }
        form.addressStreet.value = data.logradouro || form.addressStreet.value;
        form.addressNeighborhood.value = data.bairro || form.addressNeighborhood.value;
        form.addressCity.value = data.localidade || form.addressCity.value;
        form.addressState.value = (data.uf || form.addressState.value || "").toUpperCase();
        form.addressZipCode.value = cep.replace(/(\d{5})(\d{3})/, "$1-$2");
        if (cepHint) {
            cepHint.textContent = "Endereço preenchido. Confira o número.";
        }
        form.addressNumber.focus();
    } catch {
        if (cepHint) {
            cepHint.textContent = "Não foi possível buscar o CEP agora.";
        }
    }
}

on(form, "change", async (event) => {
    if (event.target.name === "fulfillmentType") {
        syncAddressVisibility();
        try {
            await refreshQuote();
        } catch (error) {
            showError(error instanceof ApiError ? error.message : "Não foi possível recalcular o frete.");
        }
    }
});

on($("#apply-coupon"), "click", async () => {
    try {
        await refreshQuote();
        hideAlert();
    } catch (error) {
        showError(error instanceof ApiError ? error.message : "Cupom inválido.");
    }
});

on(form.addressZipCode, "blur", () => lookupCep(form.addressZipCode.value));
on(form.addressZipCode, "input", () => {
    const digits = form.addressZipCode.value.replace(/\D/g, "");
    if (digits.length === 8) {
        lookupCep(digits);
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
            notes: form.notes.value.trim() || null,
            couponCode: form.couponCode?.value?.trim() || null
        };
        if (payload.fulfillmentType === "DELIVERY") {
            payload.addressZipCode = form.addressZipCode.value.replace(/\D/g, "") || null;
            payload.addressStreet = form.addressStreet.value.trim();
            payload.addressNumber = form.addressNumber.value.trim();
            payload.addressComplement = form.addressComplement.value.trim() || null;
            payload.addressNeighborhood = form.addressNeighborhood.value.trim();
            payload.addressCity = form.addressCity.value.trim();
            payload.addressState = form.addressState.value.trim().toUpperCase();
        }
        const idempotencyKey = crypto.randomUUID();
        const order = await api(`/store/${encodeURIComponent(slug)}/orders`, {
            method: "POST",
            body: payload,
            headers: { "Idempotency-Key": idempotencyKey }
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
