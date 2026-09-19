import { api, apiUpload, ApiError } from "../api/client.js";
import { logout, requirePageAuth } from "../auth/api.js";
import { $ } from "../utils/dom.js";
import { formatBRL, formatQuantity } from "../utils/format.js";
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

const mediaState = { enabled: false, uploading: false };
await loadMediaConfig();
wireProductImageControls();

const orderState = { status: "OPEN" };
renderOrderFilters();
await refreshOrders();

const paymentSettingsCard = $("#payment-settings-card");
if (user.role === "STAFF") {
    paymentSettingsCard?.setAttribute("hidden", "");
} else {
    await loadPaymentSettings();
    $("#payment-settings-form")?.addEventListener("submit", async (event) => {
        event.preventDefault();
        const form = event.currentTarget;
        const alertBox = $("#payment-settings-alert");
        try {
            const body = {
                provider: form.provider.value,
                pixEnabled: form.pixEnabled.checked,
                mockMode: form.mockMode.checked,
                onlineEnabled: false
            };
            if (form.accessToken.value.trim()) {
                body.accessToken = form.accessToken.value.trim();
            }
            await api("/payments/settings", { method: "PUT", body });
            form.accessToken.value = "";
            hideAlert(alertBox);
            await loadPaymentSettings();
        } catch (error) {
            showFormAlert(alertBox, error, "Não foi possível salvar pagamentos.");
        }
    });
}

if (user.role !== "STAFF") {
    await loadDeliverySettings();
    await refreshCoupons();
    $("#delivery-settings-form")?.addEventListener("submit", async (event) => {
        event.preventDefault();
        const form = event.currentTarget;
        const alertBox = $("#delivery-settings-alert");
        try {
            const freeAbove = form.freeAboveAmount.value;
            const eta = form.estimatedMinutes.value;
            await api("/delivery/settings", {
                method: "PUT",
                body: {
                    deliveryEnabled: form.deliveryEnabled.checked,
                    pickupEnabled: form.pickupEnabled.checked,
                    fixedFee: Number(form.fixedFee.value || 0),
                    freeAboveAmount: freeAbove === "" ? null : Number(freeAbove),
                    estimatedMinutes: eta === "" ? null : Number(eta)
                }
            });
            hideAlert(alertBox);
            await loadDeliverySettings();
        } catch (error) {
            showFormAlert(alertBox, error, "Não foi possível salvar frete.");
        }
    });
    $("#coupon-form")?.addEventListener("submit", async (event) => {
        event.preventDefault();
        const form = event.currentTarget;
        try {
            const min = form.minOrderAmount.value;
            await api("/coupons", {
                method: "POST",
                body: {
                    code: form.code.value.trim(),
                    discountType: form.discountType.value,
                    discountValue: Number(form.discountValue.value),
                    minOrderAmount: min === "" ? null : Number(min),
                    active: true
                }
            });
            form.reset();
            hideAlert($("#coupon-alert"));
            await refreshCoupons();
        } catch (error) {
            showFormAlert($("#coupon-alert"), error, "Não foi possível criar o cupom.");
        }
    });
} else {
    $("#delivery-settings-card")?.setAttribute("hidden", "");
    $("#coupons-card")?.setAttribute("hidden", "");
}

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
    if (mediaState.uploading) {
        showFormAlert($("#product-alert"), null, "Aguarde o envio da imagem.");
        return;
    }
    try {
        const stockControlled = form.stockControlled.checked;
        const stockRaw = form.stockQuantity.value;
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
                stockControlled,
                stockQuantity: stockControlled ? Number(stockRaw || 0) : null,
                minimumQuantity: form.unit.value === "KG" ? 0.2 : 1
            }
        });
        form.reset();
        clearProductImagePreview();
        hideAlert($("#product-alert"));
        await refreshCatalog();
    } catch (error) {
        showFormAlert($("#product-alert"), error, "Não foi possível salvar o produto.");
    }
});

async function loadMediaConfig() {
    try {
        const config = await api("/media/config");
        mediaState.enabled = !!config.enabled;
        const fileInput = $("#product-image-file");
        const hint = $("#product-image-hint");
        if (!mediaState.enabled) {
            if (fileInput) {
                fileInput.disabled = true;
            }
            if (hint) {
                hint.textContent = "Upload ainda não configurado — cole uma URL da imagem. Configure Cloudinary no servidor para enviar arquivo.";
            }
        } else if (hint) {
            hint.textContent = "Envie um arquivo (JPG/PNG/WEBP, até 5 MB) ou cole uma URL.";
        }
    } catch {
        mediaState.enabled = false;
    }
}

function wireProductImageControls() {
    const fileInput = $("#product-image-file");
    const urlInput = $("#product-image");
    fileInput?.addEventListener("change", async () => {
        const file = fileInput.files?.[0];
        if (!file) {
            return;
        }
        if (!mediaState.enabled) {
            showFormAlert($("#product-alert"), null, "Upload não configurado. Cole a URL da imagem.");
            fileInput.value = "";
            return;
        }
        mediaState.uploading = true;
        try {
            const formData = new FormData();
            formData.append("file", file);
            const uploaded = await apiUpload("/media/upload", formData);
            if (urlInput) {
                urlInput.value = uploaded.url;
            }
            setProductImagePreview(uploaded.url);
            hideAlert($("#product-alert"));
        } catch (error) {
            showFormAlert($("#product-alert"), error, "Falha ao enviar a imagem.");
            fileInput.value = "";
        } finally {
            mediaState.uploading = false;
        }
    });
    urlInput?.addEventListener("input", () => {
        const value = urlInput.value.trim();
        if (value) {
            setProductImagePreview(value);
        } else {
            clearProductImagePreview();
        }
    });
}

function setProductImagePreview(url) {
    const box = $("#product-image-preview");
    const img = $("#product-image-preview-img");
    if (!box || !img || !url) {
        return;
    }
    img.src = url;
    box.hidden = false;
}

function clearProductImagePreview() {
    const box = $("#product-image-preview");
    const img = $("#product-image-preview-img");
    const fileInput = $("#product-image-file");
    if (img) {
        img.removeAttribute("src");
    }
    if (box) {
        box.hidden = true;
    }
    if (fileInput) {
        fileInput.value = "";
    }
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
        const stockLabel = item.stockControlled
            ? ` · estoque ${item.stockQuantity ?? 0}`
            : "";
        meta.textContent = `${item.categoryName} · ${formatBRL(item.price)} / ${item.unit} · ${item.available ? "à venda" : "oculto"}${stockLabel}`;
        const actions = document.createElement("div");
        actions.style.display = "flex";
        actions.style.gap = "0.4rem";
        actions.append(
            flagButton(item.available ? "Pausar" : "Publicar", `/products/${item.id}/availability`, !item.available),
            flagButton(item.featured ? "Tirar destaque" : "Destacar", `/products/${item.id}/featured`, !item.featured)
        );
        if (item.stockControlled) {
            const stockBtn = document.createElement("button");
            stockBtn.type = "button";
            stockBtn.className = "btn btn-ghost";
            stockBtn.textContent = "Ajustar estoque";
            stockBtn.addEventListener("click", async () => {
                const raw = window.prompt("Nova quantidade em estoque", String(item.stockQuantity ?? 0));
                if (raw == null || raw === "") {
                    return;
                }
                try {
                    await api(`/products/${item.id}/stock`, {
                        method: "PATCH",
                        body: { quantity: Number(raw) }
                    });
                    await refreshCatalog();
                } catch (error) {
                    showFormAlert($("#product-alert"), error, "Não foi possível ajustar o estoque.");
                }
            });
            actions.append(stockBtn);
        }
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
    const statusParam = orderState.status === "PENDING" ? "?status=PENDING" : "";
    const [summary, orders] = await Promise.all([
        api("/orders/summary"),
        api(`/orders${statusParam}`)
    ]);
    let filtered = orders;
    if (orderState.status === "OPEN") {
        filtered = orders.filter((order) => !["DELIVERED", "CANCELLED"].includes(order.status));
    } else if (orderState.status === "DONE") {
        filtered = orders.filter((order) => ["DELIVERED", "CANCELLED"].includes(order.status));
    }
    renderOrderSummary(summary);
    renderOrders(filtered);
}

function renderOrderFilters() {
    const row = $("#order-filters");
    if (!row) {
        return;
    }
    row.replaceChildren();
    const filters = [
        { label: "Em andamento", value: "OPEN" },
        { label: "Pendentes", value: "PENDING" },
        { label: "Finalizados", value: "DONE" },
        { label: "Todos", value: null }
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
    const storeName = establishment?.name ?? "nossa loja";
    orders.forEach((order) => {
        const row = document.createElement("div");
        row.className = "order-admin-row";

        const head = document.createElement("div");
        head.className = "order-admin-head";
        const title = document.createElement("strong");
        title.textContent = `${order.publicCode} · ${order.customerName}`;
        const badge = document.createElement("span");
        badge.className = "badge";
        badge.textContent = statusLabel(order.status);
        head.append(title, badge);

        const meta = document.createElement("p");
        meta.className = "muted";
        const payment = order.payment;
        const payLabel = payment
            ? ` · Pagamento ${paymentStatusLabel(payment.status)}`
            : "";
        meta.textContent = `${formatBRL(order.total)} · ${fulfillmentLabel(order.fulfillmentType)} · ${formatPhoneDisplay(order.customerPhone)}${payLabel}`;

        const items = document.createElement("ul");
        items.className = "order-item-list";
        (order.items || []).forEach((item) => {
            const li = document.createElement("li");
            li.textContent = `${formatQuantity(item.quantity, item.productUnit)} · ${item.productName}`;
            items.append(li);
        });

        row.append(head, meta, items);

        const addressLine = formatOrderAddress(order);
        if (addressLine) {
            const address = document.createElement("p");
            address.className = "order-admin-address";
            address.textContent = addressLine;
            row.append(address);
        }

        if (order.notes) {
            const notes = document.createElement("p");
            notes.className = "muted";
            notes.textContent = `Obs.: ${order.notes}`;
            row.append(notes);
        }

        const next = nextStatus(order.status);
        const actions = document.createElement("div");
        actions.className = "order-admin-actions";

        const waGeneral = whatsappLink(
            order.customerPhone,
            `Olá ${order.customerName}! Aqui é da ${storeName}. Sobre o pedido ${order.publicCode}:`
        );
        if (waGeneral) {
            const waBtn = document.createElement("a");
            waBtn.className = "btn btn-whatsapp";
            waBtn.href = waGeneral;
            waBtn.target = "_blank";
            waBtn.rel = "noopener noreferrer";
            waBtn.textContent = "WhatsApp";
            actions.append(waBtn);

            const waMissing = document.createElement("a");
            waMissing.className = "btn btn-secondary";
            waMissing.href = whatsappLink(
                order.customerPhone,
                `Olá ${order.customerName}! Sobre o pedido ${order.publicCode} da ${storeName}: um item ficou indisponível. Podemos trocar ou ajustar o pedido?`
            );
            waMissing.target = "_blank";
            waMissing.rel = "noopener noreferrer";
            waMissing.textContent = "Avisar falta";
            actions.append(waMissing);
        }

        if (payment && payment.status !== "PAID" && ["CASH", "ON_DELIVERY"].includes(payment.method)) {
            const confirmPay = document.createElement("button");
            confirmPay.type = "button";
            confirmPay.className = "btn btn-secondary";
            confirmPay.textContent = "Confirmar pagamento";
            confirmPay.addEventListener("click", () => confirmPayment(order.id));
            actions.append(confirmPay);
        }
        if (next) {
            const advance = document.createElement("button");
            advance.type = "button";
            advance.className = "btn btn-primary";
            advance.textContent = actionLabel(next);
            advance.addEventListener("click", () => updateOrderStatus(order.id, next));
            actions.append(advance);
        }
        if (canCancel(order.status)) {
            const cancel = document.createElement("button");
            cancel.type = "button";
            cancel.className = "btn btn-ghost";
            cancel.textContent = "Cancelar";
            cancel.addEventListener("click", () => updateOrderStatus(order.id, "CANCELLED"));
            actions.append(cancel);
        }
        if (actions.childNodes.length) {
            row.append(actions);
        }
        list.append(row);
    });
}

function toWhatsAppNumber(phone) {
    const digits = String(phone || "").replace(/\D+/g, "");
    if (!digits) {
        return null;
    }
    if (digits.startsWith("55") && digits.length >= 12) {
        return digits;
    }
    if (digits.length >= 10 && digits.length <= 11) {
        return `55${digits}`;
    }
    return digits;
}

function whatsappLink(phone, text) {
    const number = toWhatsAppNumber(phone);
    if (!number) {
        return null;
    }
    const query = text ? `?text=${encodeURIComponent(text)}` : "";
    return `https://wa.me/${number}${query}`;
}

function formatPhoneDisplay(phone) {
    const digits = String(phone || "").replace(/\D+/g, "");
    if (digits.length === 11) {
        return `(${digits.slice(0, 2)}) ${digits.slice(2, 7)}-${digits.slice(7)}`;
    }
    if (digits.length === 10) {
        return `(${digits.slice(0, 2)}) ${digits.slice(2, 6)}-${digits.slice(6)}`;
    }
    return phone || "";
}

function formatOrderAddress(order) {
    if (order.fulfillmentType !== "DELIVERY") {
        return null;
    }
    const parts = [
        [order.addressStreet, order.addressNumber].filter(Boolean).join(", "),
        order.addressComplement,
        order.addressNeighborhood,
        [order.addressCity, order.addressState].filter(Boolean).join(" - "),
        order.addressZipCode
    ].filter(Boolean);
    return parts.length ? `Entrega: ${parts.join(" · ")}` : null;
}

async function loadPaymentSettings() {
    try {
        const settings = await api("/payments/settings");
        const form = $("#payment-settings-form");
        if (!form) {
            return;
        }
        form.provider.value = settings.provider || "MERCADO_PAGO";
        form.mockMode.checked = !!settings.mockMode;
        form.pixEnabled.checked = !!settings.pixEnabled;
        const hint = $("#payment-token-hint");
        if (hint) {
            hint.textContent = settings.accessTokenConfigured
                ? "Token já configurado. Deixe em branco para manter."
                : "Nenhum token configurado — mock ativo por padrão.";
        }
    } catch (error) {
        showFormAlert($("#payment-settings-alert"), error, "Não foi possível carregar pagamentos.");
    }
}

async function loadDeliverySettings() {
    try {
        const settings = await api("/delivery/settings");
        const form = $("#delivery-settings-form");
        if (!form) {
            return;
        }
        form.deliveryEnabled.checked = !!settings.deliveryEnabled;
        form.pickupEnabled.checked = !!settings.pickupEnabled;
        form.fixedFee.value = settings.fixedFee ?? 0;
        form.freeAboveAmount.value = settings.freeAboveAmount ?? "";
        form.estimatedMinutes.value = settings.estimatedMinutes ?? "";
    } catch (error) {
        showFormAlert($("#delivery-settings-alert"), error, "Não foi possível carregar frete.");
    }
}

async function refreshCoupons() {
    const list = $("#coupons-list");
    if (!list) {
        return;
    }
    try {
        const coupons = await api("/coupons");
        list.replaceChildren();
        if (!coupons.length) {
            const empty = document.createElement("p");
            empty.className = "muted";
            empty.textContent = "Nenhum cupom ainda.";
            list.append(empty);
            return;
        }
        coupons.forEach((coupon) => {
            const row = document.createElement("div");
            row.className = "product-admin-row";
            const title = document.createElement("strong");
            title.textContent = coupon.code;
            const meta = document.createElement("p");
            meta.className = "muted";
            const valueLabel = coupon.discountType === "PERCENT"
                ? `${coupon.discountValue}%`
                : formatBRL(coupon.discountValue);
            meta.textContent = `${valueLabel} · usos ${coupon.usedCount}${coupon.usageLimit ? `/${coupon.usageLimit}` : ""} · ${coupon.active ? "ativo" : "inativo"}`;
            const del = document.createElement("button");
            del.type = "button";
            del.className = "btn btn-ghost";
            del.textContent = "Remover";
            del.addEventListener("click", async () => {
                try {
                    await api(`/coupons/${coupon.id}`, { method: "DELETE" });
                    await refreshCoupons();
                } catch (error) {
                    showFormAlert($("#coupon-alert"), error, "Não foi possível remover o cupom.");
                }
            });
            row.append(title, meta, del);
            list.append(row);
        });
    } catch (error) {
        showFormAlert($("#coupon-alert"), error, "Não foi possível carregar cupons.");
    }
}

async function confirmPayment(orderId) {
    try {
        await api(`/orders/${orderId}/payment/confirm`, { method: "POST" });
        hideAlert($("#orders-alert"));
        await refreshOrders();
    } catch (error) {
        showFormAlert($("#orders-alert"), error, "Não foi possível confirmar o pagamento.");
    }
}

async function updateOrderStatus(orderId, status) {
    try {
        await api(`/orders/${orderId}/status`, {
            method: "PATCH",
            body: { status }
        });
        hideAlert($("#orders-alert"));
        await refreshOrders();
    } catch (error) {
        showFormAlert($("#orders-alert"), error, "Não foi possível atualizar o status.");
    }
}

function nextStatus(status) {
    return ({
        PENDING: "CONFIRMED",
        CONFIRMED: "PREPARING",
        PREPARING: "DISPATCHED",
        DISPATCHED: "DELIVERED"
    })[status] || null;
}

function canCancel(status) {
    return ["PENDING", "CONFIRMED", "PREPARING"].includes(status);
}

function statusLabel(status) {
    return ({
        PENDING: "Pendente",
        CONFIRMED: "Confirmado",
        PREPARING: "Em preparo",
        DISPATCHED: "Saiu",
        DELIVERED: "Entregue",
        CANCELLED: "Cancelado"
    })[status] || status;
}

function actionLabel(status) {
    return ({
        CONFIRMED: "Aceitar pedido",
        PREPARING: "Iniciar preparo",
        DISPATCHED: "Marcar como saiu",
        DELIVERED: "Marcar entregue"
    })[status] || status;
}

function fulfillmentLabel(value) {
    return value === "PICKUP" ? "Retirada" : "Entrega";
}

function paymentStatusLabel(status) {
    return ({
        PENDING: "aguardando",
        AUTHORIZED: "autorizado",
        PAID: "pago",
        FAILED: "falhou",
        REFUNDED: "estornado",
        CANCELLED: "cancelado",
        EXPIRED: "expirado"
    })[status] || status;
}

