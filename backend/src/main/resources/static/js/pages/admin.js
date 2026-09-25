import { api, apiUpload, ApiError } from "../api/client.js";
import { logout, requirePageAuth } from "../auth/api.js";
import { $ } from "../utils/dom.js";
import { formatBRL, formatQuantity, discountPercent } from "../utils/format.js";
import { compressImageFile } from "../utils/image.js";
import { createThumb, optimizedImageUrl, setPreviewImage } from "../utils/media.js";
import { storeUrl } from "../utils/nav.js";

/** Controle do form por name — evita colisão com form.name (atributo HTML). */
function control(form, name) {
    if (!form) {
        return null;
    }
    const named = form.elements?.namedItem(name);
    if (named && typeof named === "object" && "value" in named) {
        return named;
    }
    return form.querySelector(`[name="${name}"]`);
}

// Bloqueia submit nativo imediato (evita 405 em /admin antes do auth carregar).
document.querySelectorAll("form.auth-form").forEach((form) => {
    form.addEventListener("submit", (event) => event.preventDefault());
});

const me = await requirePageAuth(["OWNER", "ADMIN", "STAFF"]);
if (!me) {
    throw new Error("redirect");
}

const { user } = me;
let establishment = me.establishment;
$("#user-name").textContent = user.name;
$("#user-role").textContent = user.role;
$("#store-name").textContent = establishment?.name ?? "Estabelecimento";
$("#store-slug").textContent = establishment ? storeUrl(establishment.slug) : "";
$("#store-plan").textContent = establishment?.planCode ?? "";
$("#store-status").textContent = establishment?.active ? "Ativo" : "Inativo";
$("#store-link").href = establishment ? storeUrl(establishment.slug) : "/";
$("#store-link-top")?.setAttribute("href", establishment ? storeUrl(establishment.slug) : "/");

$("#logout-button")?.addEventListener("click", () => logout());

if (user.role === "STAFF") {
    document.querySelectorAll("[data-owner-only]").forEach((el) => {
        el.hidden = true;
    });
}
wireAdminNav();

const mediaState = { enabled: false, uploading: false };
let catalogCategories = [];
/** @type {string | null} */
let editingProductId = null;
const formCarousels = new Map();
const DAY_LABELS = {
    mon: "Segunda",
    tue: "Terça",
    wed: "Quarta",
    thu: "Quinta",
    fri: "Sexta",
    sat: "Sábado",
    sun: "Domingo"
};
wireFormCarousels();
wireCatalogForms();
await refreshCatalog().catch((error) => {
    showFormAlert($("#product-alert"), error, "Não foi possível carregar o catálogo.");
});

await loadMediaConfig();
wireProductImageControls();
renderStoreOpsCard();
wireStoreProfileForm();
renderOpeningHoursEditor(establishment?.openingHours || {});
$("#refresh-orders-btn")?.addEventListener("click", async () => {
    const btn = $("#refresh-orders-btn");
    if (btn) {
        btn.disabled = true;
        btn.textContent = "Atualizando…";
    }
    try {
        await refreshOrders();
        establishment = await api("/establishments/me");
        renderStoreOpsCard();
    } catch (error) {
        showFormAlert($("#orders-alert"), error, "Não foi possível atualizar os pedidos.");
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.textContent = "Atualizar";
        }
    }
});

const orderState = { status: "OPEN" };
let knownOrderIds = new Set();
let ordersPollTimer = 0;
let audioCtx = null;
renderOrderFilters();
await refreshOrders().catch((error) => {
    showFormAlert($("#orders-alert"), error, "Não foi possível carregar os pedidos.");
});

function playNewOrderChime() {
    try {
        audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
        const osc = audioCtx.createOscillator();
        const gain = audioCtx.createGain();
        osc.type = "sine";
        osc.frequency.value = 880;
        gain.gain.value = 0.04;
        osc.connect(gain);
        gain.connect(audioCtx.destination);
        osc.start();
        osc.stop(audioCtx.currentTime + 0.18);
    } catch {
        // ignore autoplay restrictions
    }
}

async function pollOrdersQuietly() {
    try {
        const result = await refreshOrders({ silent: true, detectNew: true });
        if (result?.hasNew) {
            playNewOrderChime();
        }
    } catch {
        // keep polling
    }
}

function scheduleOrdersPoll() {
    window.clearTimeout(ordersPollTimer);
    ordersPollTimer = window.setTimeout(async () => {
        if (document.visibilityState !== "hidden") {
            await pollOrdersQuietly();
        }
        scheduleOrdersPoll();
    }, 12000);
}

scheduleOrdersPoll();
document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
        pollOrdersQuietly();
    }
});

const paymentSettingsCard = $("#payment-settings-card");
if (user.role !== "STAFF") {
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
            showFormSuccess(alertBox, "Pagamentos atualizados.");
        } catch (error) {
            showFormAlert(alertBox, error, "Não foi possível salvar pagamentos.");
        }
    });

    await loadFiscalSettings();
    $("#fiscal-settings-form")?.addEventListener("submit", async (event) => {
        event.preventDefault();
        const form = event.currentTarget;
        const alertBox = $("#fiscal-settings-alert");
        try {
            const body = {
                enabled: form.enabled.checked,
                environment: form.environment.value,
                cnpj: form.cnpj.value.trim(),
                autoEmitOnPaid: form.autoEmitOnPaid.checked,
                defaultNcm: form.defaultNcm.value.trim() || "21069090",
                defaultCfop: form.defaultCfop.value.trim() || "5102",
                icmsOrigem: 0,
                icmsSituacaoTributaria: form.icmsSituacaoTributaria.value.trim() || "102"
            };
            if (form.apiToken.value.trim()) {
                body.apiToken = form.apiToken.value.trim();
            }
            await api("/fiscal/settings", { method: "PUT", body });
            form.apiToken.value = "";
            hideAlert(alertBox);
            await loadFiscalSettings();
            showFormSuccess(alertBox, "Configuração fiscal salva.");
        } catch (error) {
            showFormAlert(alertBox, error, "Não foi possível salvar o fiscal.");
        }
    });
    $("#fiscal-test-btn")?.addEventListener("click", async () => {
        const alertBox = $("#fiscal-settings-alert");
        const btn = $("#fiscal-test-btn");
        try {
            if (btn) {
                btn.disabled = true;
                btn.textContent = "Testando…";
            }
            const result = await api("/fiscal/settings/test", { method: "POST", body: {} });
            if (result?.ok) {
                showFormSuccess(alertBox, result.message || "Conexão OK.");
            } else {
                showFormAlert(alertBox, null, result?.message || "Falha ao conectar na Focus.");
            }
        } catch (error) {
            showFormAlert(alertBox, error, "Não foi possível testar a Focus NFe.");
        } finally {
            if (btn) {
                btn.disabled = false;
                btn.textContent = "Testar conexão";
            }
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
            const pickupEta = form.pickupEtaMinutes.value;
            const deliveryEta = form.deliveryEtaMinutes.value;
            const minOrder = form.minOrderAmount.value;
            await api("/delivery/settings", {
                method: "PUT",
                body: {
                    deliveryEnabled: form.deliveryEnabled.checked,
                    pickupEnabled: form.pickupEnabled.checked,
                    fixedFee: Number(form.fixedFee.value || 0),
                    freeAboveAmount: freeAbove === "" ? null : Number(freeAbove),
                    pickupEtaMinutes: pickupEta === "" ? null : Number(pickupEta),
                    deliveryEtaMinutes: deliveryEta === "" ? null : Number(deliveryEta),
                    estimatedMinutes: deliveryEta === "" ? null : Number(deliveryEta),
                    minOrderAmount: minOrder === "" ? null : Number(minOrder)
                }
            });
            hideAlert(alertBox);
            await loadDeliverySettings();
            renderStoreOpsCard();
            showFormSuccess(alertBox, "Frete atualizado.");
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
}

const usersCard = $("#users-card");
if (user.role !== "STAFF") {
    if (user.role === "ADMIN") {
        document.querySelector("#member-role option[value='ADMIN']")?.remove();
    }
    await renderUsers().catch((error) => {
        showFormAlert($("#users-alert"), error, "Não foi possível carregar a equipe.");
    });
    $("#user-form")?.addEventListener("submit", async (event) => {
        event.preventDefault();
        const form = event.currentTarget;
        const alertBox = $("#users-alert");
        try {
            await api("/users", {
                method: "POST",
                body: {
                    name: control(form, "name")?.value,
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

function wireCatalogForms() {
    const categoryForm = $("#category-form");
    const categoryBtn = categoryForm?.querySelector("[data-category-submit], button[type='submit']");
    categoryBtn?.addEventListener("click", async (event) => {
        event.preventDefault();
        await saveCategory(categoryForm);
    });
    categoryForm?.addEventListener("submit", async (event) => {
        event.preventDefault();
        await saveCategory(categoryForm);
    });

    $("#product-form")?.addEventListener("submit", async (event) => {
        event.preventDefault();
        const form = event.currentTarget;
        const alertBox = $("#product-alert");
        if (mediaState.uploading) {
            showFormAlert(alertBox, null, "Aguarde o envio da imagem.");
            return;
        }
        if (!validateFormCarousel(form)) {
            return;
        }
        if (!form.categoryId.value) {
            showFormAlert(alertBox, null, "Selecione uma categoria.");
            formCarousels.get(form)?.go(0);
            return;
        }
        const submitBtn = form.querySelector("[data-carousel-submit]");
        if (submitBtn?.disabled) {
            return;
        }
        const minimumQuantity = readMinimumQuantity(form);
        if (minimumQuantity == null) {
            showFormAlert(alertBox, null, "Informe a quantidade mínima válida.");
            formCarousels.get(form)?.go(1);
            return;
        }
        const promo = readPromotionFields(form);
        if (!promo.ok) {
            showFormAlert(alertBox, null, promo.error);
            formCarousels.get(form)?.go(1);
            return;
        }
        const variants = readProductVariants(form);
        if (!variants.ok) {
            showFormAlert(alertBox, null, variants.error);
            formCarousels.get(form)?.go(1);
            return;
        }
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.textContent = "Salvando…";
        }
        try {
            const stockRaw = form.stockQuantity.value.trim();
            const hasStock = stockRaw !== "";
            let price = Number(form.price.value);
            if (variants.items.length) {
                const minVariant = Math.min(...variants.items.map((item) => item.price));
                if (!Number.isFinite(price) || price <= 0) {
                    price = minVariant;
                }
            }
            const body = {
                name: control(form, "name")?.value,
                categoryId: form.categoryId.value,
                price,
                compareAtPrice: promo.compareAtPrice,
                unit: form.unit.value,
                imageUrl: form.imageUrl.value || null,
                featured: promo.enabled,
                stockControlled: hasStock,
                stockQuantity: hasStock ? Number(stockRaw) : null,
                minimumQuantity,
                ncm: form.ncm?.value?.trim() || null,
                variants: variants.items
            };
            if (editingProductId) {
                await api(`/products/${editingProductId}`, {
                    method: "PUT",
                    body
                });
                showFormSuccess(alertBox, "Produto atualizado.");
            } else {
                await api("/products", {
                    method: "POST",
                    body: {
                        ...body,
                        available: true
                    }
                });
                showFormSuccess(alertBox, "Produto salvo no catálogo.");
            }
            await refreshCatalog();
            clearProductForm(form);
            showAdminPanel("produtos");
        } catch (error) {
            showFormAlert(alertBox, error, "Não foi possível salvar o produto.");
        } finally {
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = editingProductId ? "Salvar alterações" : "Salvar produto";
            }
        }
    });

    $("#product-cancel-edit")?.addEventListener("click", () => {
        clearProductForm($("#product-form"));
        hideAlert($("#product-alert"));
    });

    const productForm = $("#product-form");
    productForm?.unit?.addEventListener("change", () => syncMinQtyField(productForm, { resetValue: true }));
    if (productForm) {
        syncMinQtyField(productForm);
        wireProductPromoFields(productForm);
        wireProductVariantFields(productForm);
    }

    $("#bulk-kg-min-btn")?.addEventListener("click", () => applyBulkKgMinimum());
}

function wireProductPromoFields(form) {
    const toggle = form.promoEnabled || $("#product-promo");
    const priceInput = form.price;
    const compareInput = form.compareAtPrice || $("#product-compare-price");
    const refresh = () => syncProductPromoFields(form);
    toggle?.addEventListener("change", refresh);
    priceInput?.addEventListener("input", refresh);
    compareInput?.addEventListener("input", refresh);
    syncProductPromoFields(form);
}

function syncProductPromoFields(form) {
    if (!form) {
        return;
    }
    const enabled = !!(form.promoEnabled?.checked);
    const fields = $("#product-promo-fields");
    const preview = $("#product-promo-preview");
    const compareInput = form.compareAtPrice || $("#product-compare-price");
    if (fields) {
        fields.hidden = !enabled;
    }
    if (compareInput) {
        compareInput.required = enabled;
        if (!enabled) {
            compareInput.value = "";
        }
    }
    if (!preview) {
        return;
    }
    if (!enabled) {
        preview.hidden = true;
        preview.textContent = "";
        return;
    }
    const price = Number(form.price?.value);
    const compare = Number(compareInput?.value);
    const pct = discountPercent(price, compare);
    if (pct == null) {
        preview.hidden = true;
        preview.textContent = "";
        return;
    }
    preview.hidden = false;
    preview.textContent = `Etiqueta na loja: −${pct}% · de ${formatBRL(compare)} por ${formatBRL(price)}`;
}

function readPromotionFields(form) {
    const enabled = !!(form.promoEnabled?.checked);
    if (!enabled) {
        return { ok: true, enabled: false, compareAtPrice: 0 };
    }
    const price = Number(form.price?.value);
    const compare = Number(form.compareAtPrice?.value || $("#product-compare-price")?.value);
    if (!Number.isFinite(compare) || compare <= 0) {
        return { ok: false, error: "Informe o preço antigo da promoção." };
    }
    if (!Number.isFinite(price) || price <= 0) {
        return { ok: false, error: "Informe o preço atual da promoção." };
    }
    if (compare <= price) {
        return { ok: false, error: "O preço antigo precisa ser maior que o preço atual." };
    }
    return { ok: true, enabled: true, compareAtPrice: compare };
}

function wireProductVariantFields(form) {
    const toggle = form.hasVariants || $("#product-has-variants");
    const addBtn = $("#product-variant-add");
    toggle?.addEventListener("change", () => syncProductVariantFields(form));
    addBtn?.addEventListener("click", () => {
        appendVariantRow();
        syncProductVariantFields(form);
    });
    syncProductVariantFields(form);
}

function syncProductVariantFields(form) {
    const enabled = !!(form.hasVariants?.checked || $("#product-has-variants")?.checked);
    const box = $("#product-variants-box");
    if (box) {
        box.hidden = !enabled;
    }
    if (enabled) {
        const list = $("#product-variants-list");
        if (list && !list.children.length) {
            appendVariantRow();
        }
    }
}

function appendVariantRow(variant = null) {
    const list = $("#product-variants-list");
    if (!list) {
        return;
    }
    const row = document.createElement("div");
    row.className = "product-variant-row";
    if (variant?.id) {
        row.dataset.variantId = variant.id;
    }
    const nameField = document.createElement("div");
    nameField.className = "field";
    const nameLabel = document.createElement("label");
    nameLabel.textContent = "Sabor / opção";
    const nameInput = document.createElement("input");
    nameInput.type = "text";
    nameInput.name = "variantName";
    nameInput.placeholder = "Ex.: Morango";
    nameInput.maxLength = 120;
    nameInput.value = variant?.name || "";
    nameInput.required = true;
    nameField.append(nameLabel, nameInput);

    const priceField = document.createElement("div");
    priceField.className = "field";
    const priceLabel = document.createElement("label");
    priceLabel.textContent = "Preço (R$)";
    const priceInput = document.createElement("input");
    priceInput.type = "number";
    priceInput.name = "variantPrice";
    priceInput.min = "0.01";
    priceInput.step = "0.01";
    priceInput.placeholder = "0,00";
    priceInput.value = variant?.price != null ? String(variant.price) : "";
    priceInput.required = true;
    priceField.append(priceLabel, priceInput);

    const removeBtn = document.createElement("button");
    removeBtn.type = "button";
    removeBtn.className = "btn btn-ghost";
    removeBtn.textContent = "Remover";
    removeBtn.addEventListener("click", () => {
        row.remove();
        const listEl = $("#product-variants-list");
        if (listEl && !listEl.children.length && ($("#product-has-variants")?.checked)) {
            appendVariantRow();
        }
    });

    row.append(nameField, priceField, removeBtn);
    list.append(row);
}

function fillProductVariants(variants) {
    const list = $("#product-variants-list");
    const toggle = $("#product-has-variants");
    if (!list || !toggle) {
        return;
    }
    list.replaceChildren();
    const items = Array.isArray(variants) ? variants : [];
    toggle.checked = items.length > 0;
    if (items.length) {
        items.forEach((item) => appendVariantRow(item));
    }
    syncProductVariantFields($("#product-form"));
}

function readProductVariants(form) {
    const enabled = !!(form.hasVariants?.checked || $("#product-has-variants")?.checked);
    if (!enabled) {
        return { ok: true, items: [] };
    }
    const rows = [...document.querySelectorAll("#product-variants-list .product-variant-row")];
    const items = [];
    for (let i = 0; i < rows.length; i += 1) {
        const row = rows[i];
        const name = row.querySelector('[name="variantName"]')?.value?.trim() || "";
        const price = Number(row.querySelector('[name="variantPrice"]')?.value);
        if (!name && !Number.isFinite(price)) {
            continue;
        }
        if (!name) {
            return { ok: false, error: "Informe o nome de cada sabor." };
        }
        if (!Number.isFinite(price) || price <= 0) {
            return { ok: false, error: `Informe o preço do sabor "${name}".` };
        }
        items.push({
            id: row.dataset.variantId || null,
            name,
            price,
            available: true,
            sortOrder: i
        });
    }
    if (!items.length) {
        return { ok: false, error: "Adicione pelo menos um sabor ou desmarque a opção." };
    }
    return { ok: true, items };
}

function parseMoneyPrompt(raw) {
    if (raw == null) {
        return null;
    }
    const normalized = String(raw).trim().replace(/\s/g, "").replace(",", ".");
    if (!normalized) {
        return null;
    }
    const value = Number(normalized);
    return Number.isFinite(value) ? value : NaN;
}

async function beginProductPromotion(item) {
    const alertBox = $("#product-alert");
    const oldRaw = window.prompt(
        `Promoção · ${item.name}\n\nPreço ANTIGO (aparece riscado na loja):`,
        item.compareAtPrice != null ? String(item.compareAtPrice) : ""
    );
    if (oldRaw == null) {
        return;
    }
    const compareAtPrice = parseMoneyPrompt(oldRaw);
    if (!Number.isFinite(compareAtPrice) || compareAtPrice <= 0) {
        showFormAlert(alertBox, null, "Informe um preço antigo válido.");
        return;
    }
    const newRaw = window.prompt(
        `Promoção · ${item.name}\n\nPreço NOVO (valor de venda na promoção):`,
        item.price != null ? String(item.price) : ""
    );
    if (newRaw == null) {
        return;
    }
    const price = parseMoneyPrompt(newRaw);
    if (!Number.isFinite(price) || price <= 0) {
        showFormAlert(alertBox, null, "Informe um preço novo válido.");
        return;
    }
    if (compareAtPrice <= price) {
        showFormAlert(alertBox, null, "O preço antigo precisa ser maior que o preço novo.");
        return;
    }
    const pct = discountPercent(price, compareAtPrice);
    const ok = window.confirm(
        `Confirmar promoção de ${item.name}?\n\nDe ${formatBRL(compareAtPrice)} por ${formatBRL(price)}${pct != null ? ` (−${pct}%)` : ""}\n\nO produto entra na seção Promoção da loja.`
    );
    if (!ok) {
        return;
    }
    try {
        await api(`/products/${item.id}`, {
            method: "PUT",
            body: {
                price,
                compareAtPrice,
                featured: true
            }
        });
        showFormSuccess(alertBox, `Promoção ativa${pct != null ? ` (−${pct}%)` : ""}.`);
        await refreshCatalog();
    } catch (error) {
        showFormAlert(alertBox, error, "Não foi possível ativar a promoção.");
    }
}

async function clearProductPromotion(item) {
    const alertBox = $("#product-alert");
    const ok = window.confirm(`Tirar a promoção de "${item.name}"?`);
    if (!ok) {
        return;
    }
    try {
        await api(`/products/${item.id}`, {
            method: "PUT",
            body: { compareAtPrice: 0, featured: false }
        });
        showFormSuccess(alertBox, "Promoção removida.");
        await refreshCatalog();
    } catch (error) {
        showFormAlert(alertBox, error, "Não foi possível remover a promoção.");
    }
}

async function applyBulkKgMinimum() {
    const alertBox = $("#bulk-kg-min-alert");
    const input = $("#bulk-kg-min-grams");
    const btn = $("#bulk-kg-min-btn");
    const grams = Math.round(Number(input?.value));
    if (!Number.isFinite(grams) || grams < 50) {
        showFormAlert(alertBox, null, "Informe um mínimo válido em gramas (ex.: 500 ou 700).");
        return;
    }
    let products = [];
    try {
        products = await api("/products");
    } catch (error) {
        showFormAlert(alertBox, error, "Não foi possível carregar os produtos.");
        return;
    }
    const kgProducts = (products || []).filter((item) => String(item.unit || "").toUpperCase() === "KG");
    if (!kgProducts.length) {
        showFormAlert(alertBox, null, "Não há produtos por kg no catálogo.");
        return;
    }
    const ok = window.confirm(
        `Aplicar mínimo de compra de ${grams} g em ${kgProducts.length} produto(s) por peso?\n\nIsso substitui o mínimo atual de todos os itens KG.`
    );
    if (!ok) {
        return;
    }
    const minimumQuantity = grams / 1000;
    if (btn) {
        btn.disabled = true;
        btn.textContent = "Aplicando…";
    }
    hideAlert(alertBox);
    let done = 0;
    let failed = 0;
    try {
        for (const item of kgProducts) {
            try {
                await api(`/products/${item.id}`, {
                    method: "PUT",
                    body: { minimumQuantity }
                });
                done += 1;
            } catch {
                failed += 1;
            }
        }
        await refreshCatalog();
        if (failed > 0) {
            showFormAlert(
                alertBox,
                null,
                `Atualizados ${done} produto(s). Falha em ${failed}. Tente de novo nos que faltaram.`
            );
        } else {
            showFormSuccess(alertBox, `Mínimo de ${grams} g aplicado em ${done} produto(s) por kg.`);
        }
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.textContent = "Aplicar em todos os KG";
        }
    }
}

function syncMinQtyField(form, options = {}) {
    if (!form) {
        return;
    }
    const input = control(form, "minimumQuantityInput") || $("#product-min-qty");
    const label = $("#product-min-qty-label");
    const hint = $("#product-min-qty-hint");
    if (!input) {
        return;
    }
    const unit = String(form.unit?.value || "UN").toUpperCase();
    if (unit === "KG") {
        if (label) {
            label.textContent = "Quantidade mínima de compra (gramas)";
        }
        if (hint) {
            hint.textContent = "Só restringe se você quiser (ex.: mamão mín. 700 g). Use 100 se não houver limite especial. Na loja o seletor ainda começa em 500 g.";
        }
        input.min = "50";
        input.step = "50";
        if (options.resetValue || !input.value) {
            input.value = "100";
        }
    } else {
        if (label) {
            label.textContent = "Quantidade mínima de compra";
        }
        if (hint) {
            hint.textContent = "Mínimo por item no pedido. Em geral 1. Só aumente se quiser restringir.";
        }
        input.min = "1";
        input.step = "1";
        if (options.resetValue || !input.value || Number(input.value) >= 50) {
            input.value = "1";
        }
    }
}

/** Converte o campo do admin para o valor da API (KG em kg; demais na própria unidade). */
function readMinimumQuantity(form) {
    const input = control(form, "minimumQuantityInput") || $("#product-min-qty");
    const raw = Number(input?.value);
    if (!Number.isFinite(raw) || raw <= 0) {
        return null;
    }
    const unit = String(form.unit?.value || "UN").toUpperCase();
    if (unit === "KG") {
        return Math.round(raw) / 1000;
    }
    return Math.round(raw * 1000) / 1000;
}

function fillMinimumQuantityInput(form, item) {
    const input = control(form, "minimumQuantityInput") || $("#product-min-qty");
    if (!input) {
        return;
    }
    syncMinQtyField(form);
    const unit = String(item?.unit || form.unit?.value || "UN").toUpperCase();
    const min = Number(item?.minimumQuantity);
    if (!Number.isFinite(min) || min <= 0) {
        input.value = unit === "KG" ? "100" : "1";
        return;
    }
    if (unit === "KG") {
        input.value = String(Math.round(min * 1000));
    } else {
        input.value = String(min);
    }
}

async function saveCategory(form) {
    if (!form) {
        return;
    }
    const alertBox = $("#category-alert");
    const submitBtn = form.querySelector("[data-category-submit], button[type='submit']");
    const nameInput = control(form, "name");
    const name = nameInput?.value.trim() || "";
    if (!name) {
        nameInput?.reportValidity?.();
        showFormAlert(alertBox, null, "Informe o nome da categoria.");
        return;
    }
    if (submitBtn?.disabled) {
        return;
    }
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = "Salvando…";
    }
    try {
        const created = await api("/categories", {
            method: "POST",
            body: {
                name,
                sortOrder: Number(form.sortOrder.value || 0)
            }
        });
        if (nameInput) {
            nameInput.value = "";
        }
        const orderInput = control(form, "sortOrder");
        if (orderInput) {
            orderInput.value = "0";
        }
        showFormSuccess(alertBox, "Categoria salva.");
        await refreshCatalog();
        if (created?.id) {
            const select = $("#product-category");
            if (select) {
                select.value = String(created.id);
                select.disabled = false;
            }
        }
        showAdminPanel("produtos");
    } catch (error) {
        showFormAlert(alertBox, error, "Não foi possível salvar a categoria.");
    } finally {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = "Adicionar";
        }
    }
}

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
            hint.textContent = "Envie um arquivo (JPG/PNG/WEBP, até 5 MB). Fotos grandes são compactadas automaticamente, sem distorcer.";
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
        let localPreview = null;
        try {
            localPreview = URL.createObjectURL(file);
            setProductImagePreview(localPreview);
            const compact = await compressImageFile(file, { maxEdge: 1600 });
            if (compact.size > 5 * 1024 * 1024) {
                showFormAlert($("#product-alert"), null, "Imagem ainda grande demais após compactar. Use outra foto (até 5 MB).");
                fileInput.value = "";
                clearProductImagePreview();
                return;
            }
            const formData = new FormData();
            formData.append("file", compact, compact.name || "produto.jpg");
            const uploaded = await apiUpload("/media/upload", formData);
            if (urlInput) {
                urlInput.value = uploaded.url;
            }
            setProductImagePreview(uploaded.url);
            hideAlert($("#product-alert"));
        } catch (error) {
            showFormAlert($("#product-alert"), error, "Falha ao enviar a imagem.");
            fileInput.value = "";
            clearProductImagePreview();
        } finally {
            if (localPreview) {
                URL.revokeObjectURL(localPreview);
            }
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
    setPreviewImage(img, url, { width: 600, height: 600 });
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
    catalogCategories = Array.isArray(categories) ? categories : [];
    renderCategoryOptions(catalogCategories);
    renderCategories(catalogCategories);
    renderProducts(products);
}

function renderCategoryOptions(categories) {
    const select = $("#product-category");
    const hint = $("#product-category-hint");
    if (!select) {
        return;
    }
    const previous = select.value;
    const active = (categories || []).filter((item) => item.active !== false);
    select.replaceChildren();
    const placeholder = document.createElement("option");
    placeholder.value = "";
    placeholder.textContent = active.length ? "Selecione uma categoria" : "Crie uma categoria primeiro";
    select.append(placeholder);
    active.forEach((item) => {
        const option = document.createElement("option");
        option.value = String(item.id);
        option.textContent = item.name;
        select.append(option);
    });
    const previousId = previous ? String(previous) : "";
    if (previousId && active.some((item) => String(item.id) === previousId)) {
        select.value = previousId;
    } else if (active.length === 1) {
        select.value = String(active[0].id);
    } else {
        select.value = "";
    }
    select.disabled = active.length === 0;
    if (hint) {
        hint.hidden = active.length > 0;
    }
}

function clearProductForm(form) {
    if (!form) {
        return;
    }
    editingProductId = null;
    const title = $("#product-form-title");
    if (title) {
        title.textContent = "Novo produto";
    }
    const cancelBtn = $("#product-cancel-edit");
    if (cancelBtn) {
        cancelBtn.hidden = true;
    }
    const submitBtn = form.querySelector("[data-carousel-submit]");
    if (submitBtn) {
        submitBtn.textContent = "Salvar produto";
    }
    const nameInput = control(form, "name");
    if (nameInput) {
        nameInput.value = "";
    }
    form.price.value = "";
    form.unit.value = "UN";
    form.imageUrl.value = "";
    form.stockQuantity.value = "";
    if (form.ncm) {
        form.ncm.value = "";
    }
    if (form.promoEnabled) {
        form.promoEnabled.checked = false;
    }
    if (form.compareAtPrice) {
        form.compareAtPrice.value = "";
    }
    syncProductPromoFields(form);
    fillProductVariants([]);
    syncMinQtyField(form, { resetValue: true });
    const fileInput = $("#product-image-file");
    if (fileInput) {
        fileInput.value = "";
    }
    clearProductImagePreview();
    renderCategoryOptions(catalogCategories);
    resetFormCarousel(form);
}

function beginEditProduct(item) {
    const form = $("#product-form");
    if (!form || !item) {
        return;
    }
    editingProductId = item.id;
    const title = $("#product-form-title");
    if (title) {
        title.textContent = `Editar · ${item.name}`;
    }
    const cancelBtn = $("#product-cancel-edit");
    if (cancelBtn) {
        cancelBtn.hidden = false;
    }
    const submitBtn = form.querySelector("[data-carousel-submit]");
    if (submitBtn) {
        submitBtn.textContent = "Salvar alterações";
    }
    renderCategoryOptions(catalogCategories);
    const nameInput = control(form, "name");
    if (nameInput) {
        nameInput.value = item.name || "";
    }
    form.categoryId.value = item.categoryId || "";
    form.price.value = item.price != null ? String(item.price) : "";
    form.unit.value = item.unit || "UN";
    form.imageUrl.value = item.imageUrl || "";
    form.stockQuantity.value = item.stockControlled && item.stockQuantity != null
        ? String(item.stockQuantity)
        : "";
    if (form.ncm) {
        form.ncm.value = item.ncm || "";
    }
    const hasPromo = item.compareAtPrice != null && Number(item.compareAtPrice) > Number(item.price);
    if (form.promoEnabled) {
        form.promoEnabled.checked = hasPromo;
    }
    if (form.compareAtPrice) {
        form.compareAtPrice.value = hasPromo ? String(item.compareAtPrice) : "";
    }
    syncProductPromoFields(form);
    fillProductVariants(item.variants || []);
    fillMinimumQuantityInput(form, item);
    const fileInput = $("#product-image-file");
    if (fileInput) {
        fileInput.value = "";
    }
    if (item.imageUrl) {
        setProductImagePreview(item.imageUrl);
    } else {
        clearProductImagePreview();
    }
    resetFormCarousel(form);
    // Preço/promoção ficam no passo 2 — abre direto nele ao editar
    formCarousels.get(form)?.go(1);
    hideAlert($("#product-alert"));
    showAdminPanel("produtos");
    scrollToProductEditor();
}

function scrollToProductEditor() {
    const target = $("#product-form-block") || $("#product-form-title") || $("#product-form");
    if (!target) {
        return;
    }
    const run = () => {
        const header = document.querySelector(".site-header");
        const nav = document.querySelector(".admin-nav");
        const stickyOffset = (header?.getBoundingClientRect().height || 0)
            + (nav?.getBoundingClientRect().height || 0)
            + 12;
        const top = window.scrollY + target.getBoundingClientRect().top - stickyOffset;
        window.scrollTo({ top: Math.max(0, top), behavior: "smooth" });
        const nameInput = $("#product-name");
        window.setTimeout(() => {
            nameInput?.focus({ preventScroll: true });
        }, 280);
    };
    // espera o painel/layout estabilizar (mobile e desktop)
    requestAnimationFrame(() => {
        requestAnimationFrame(run);
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
            try {
                await api(`/categories/${item.id}`, {
                    method: "PUT",
                    body: { active: !item.active }
                });
                await refreshCatalog();
            } catch (error) {
                showFormAlert($("#category-alert"), error, "Não foi possível atualizar a categoria.");
            }
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
            : " · venda livre";
        const minKg = Number(item.minimumQuantity || 0);
        const minLabel = item.unit === "KG"
            ? (minKg > 0.1 + 1e-9 ? ` · mín. compra ${Math.round(minKg * 1000)} g` : "")
            : Number(item.minimumQuantity) > 1
                ? ` · mín. compra ${item.minimumQuantity}`
                : "";
        const pct = discountPercent(item.price, item.compareAtPrice);
        const promoLabel = pct != null
            ? ` · promo −${pct}% (de ${formatBRL(item.compareAtPrice)})`
            : "";
        const variantCount = Array.isArray(item.variants) ? item.variants.length : 0;
        const variantLabel = variantCount > 0
            ? ` · ${variantCount} sabor${variantCount === 1 ? "" : "es"}`
            : "";
        meta.textContent = `${item.categoryName} · ${formatBRL(item.price)} / ${item.unit} · ${item.available ? "à venda" : "oculto"}${stockLabel}${minLabel}${promoLabel}${variantLabel}`;
        const body = document.createElement("div");
        const actions = document.createElement("div");
        actions.style.display = "flex";
        actions.style.gap = "0.4rem";
        actions.style.flexWrap = "wrap";
        const editBtn = document.createElement("button");
        editBtn.type = "button";
        editBtn.className = "btn btn-secondary";
        editBtn.textContent = "Editar";
        editBtn.addEventListener("click", () => beginEditProduct(item));
        actions.append(
            editBtn,
            flagButton(item.available ? "Pausar" : "Publicar", `/products/${item.id}/availability`, !item.available)
        );
        const promoBtn = document.createElement("button");
        promoBtn.type = "button";
        if (pct != null) {
            promoBtn.className = "btn btn-secondary";
            promoBtn.textContent = "Tirar promoção";
            promoBtn.addEventListener("click", () => clearProductPromotion(item));
        } else {
            promoBtn.className = "btn btn-primary";
            promoBtn.textContent = "Promoção";
            promoBtn.addEventListener("click", () => beginProductPromotion(item));
        }
        actions.append(promoBtn);
        const stockBtn = document.createElement("button");
        stockBtn.type = "button";
        stockBtn.className = "btn btn-secondary";
        stockBtn.textContent = "Estoque";
        stockBtn.addEventListener("click", async () => {
            const raw = window.prompt(
                "Quantidade em estoque. Deixe 0 ou cancele para não mudar.",
                String(item.stockQuantity ?? "")
            );
            if (raw == null || raw.trim() === "") {
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
        const deleteBtn = document.createElement("button");
        deleteBtn.type = "button";
        deleteBtn.className = "btn btn-danger";
        deleteBtn.textContent = "Apagar";
        deleteBtn.addEventListener("click", async () => {
            const ok = window.confirm(
                `Apagar o produto "${item.name}"?\n\nEssa ação não pode ser desfeita.`
            );
            if (!ok) {
                return;
            }
            try {
                await api(`/products/${item.id}`, { method: "DELETE" });
                if (editingProductId === item.id) {
                    clearProductForm($("#product-form"));
                }
                hideAlert($("#product-alert"));
                showFormSuccess($("#product-alert"), `Produto "${item.name}" apagado.`);
                await refreshCatalog();
            } catch (error) {
                showFormAlert($("#product-alert"), error, "Não foi possível apagar o produto.");
            }
        });
        actions.append(deleteBtn);
        body.append(title, meta, actions);
        row.append(createThumb(item.imageUrl, item.name, "product-admin-thumb"), body);
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

function showFormSuccess(alertBox, message) {
    if (!alertBox) {
        return;
    }
    alertBox.hidden = false;
    alertBox.className = "alert alert-success";
    alertBox.textContent = message;
}

function hideAlert(alertBox) {
    if (alertBox) {
        alertBox.hidden = true;
    }
}

async function refreshOrders(options = {}) {
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
    const nextIds = new Set(orders.map((order) => order.id));
    let hasNew = false;
    if (options.detectNew && knownOrderIds.size > 0) {
        for (const id of nextIds) {
            if (!knownOrderIds.has(id)) {
                hasNew = true;
                break;
            }
        }
    }
    knownOrderIds = nextIds;
    renderOrderSummary(summary);
    renderOrders(filtered);
    return { hasNew };
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
        const nfceLabel = order.nfce && order.nfce.status && order.nfce.status !== "NONE"
            ? ` · NFC-e ${nfceStatusLabel(order.nfce.status)}`
            : "";
        meta.textContent = `${formatBRL(order.total)} · ${fulfillmentLabel(order.fulfillmentType)} · ${formatPhoneDisplay(order.customerPhone)}${payLabel}${nfceLabel}`;

        const items = document.createElement("ul");
        items.className = "order-item-list";
        (order.items || []).forEach((item) => {
            const li = document.createElement("li");
            li.className = "order-item-line";
            li.append(
                createThumb(item.imageUrl, item.productName, "order-item-thumb"),
                document.createTextNode(`${formatQuantity(item.quantity, item.productUnit)} · ${item.productName}`)
            );
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

        const printBtn = document.createElement("button");
        printBtn.type = "button";
        printBtn.className = "btn btn-secondary";
        printBtn.textContent = "Imprimir notinha";
        printBtn.addEventListener("click", () => printNonFiscalReceipt(order));
        actions.append(printBtn);

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

        appendNfceActions(actions, order);

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

function orderDeliveryAddress(order) {
    return [
        [order.addressStreet, order.addressNumber].filter(Boolean).join(", "),
        order.addressComplement,
        order.addressNeighborhood,
        [order.addressCity, order.addressState].filter(Boolean).join(" - "),
        order.addressZipCode
    ].filter(Boolean).join(" · ");
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");
}

function formatOrderDateTime(value) {
    if (!value) {
        return "—";
    }
    try {
        return new Date(value).toLocaleString("pt-BR", {
            day: "2-digit",
            month: "2-digit",
            year: "numeric",
            hour: "2-digit",
            minute: "2-digit"
        });
    } catch {
        return String(value);
    }
}

function paymentMethodLabel(method) {
    switch (String(method || "").toUpperCase()) {
        case "PIX":
            return "PIX";
        case "CASH":
            return "Dinheiro";
        case "ON_DELIVERY":
            return "Na entrega";
        case "CARD":
            return "Cartão";
        default:
            return method || "—";
    }
}

/** Cupom / notinha sem valor fiscal — separação e entrega (NFC-e no caixa/balança). */
function printNonFiscalReceipt(order) {
    const store = establishment || {};
    const addressLine = orderDeliveryAddress(order);
    const isDelivery = String(order.fulfillmentType || "").toUpperCase() === "DELIVERY";
    const itemsHtml = (order.items || []).map((item) => {
        const qty = formatQuantity(item.quantity, item.productUnit);
        return `<tr>
            <td class="qty">${escapeHtml(qty)}</td>
            <td class="name">${escapeHtml(item.productName)}</td>
            <td class="money">${escapeHtml(formatBRL(item.unitPrice))}</td>
            <td class="money">${escapeHtml(formatBRL(item.subtotal))}</td>
        </tr>`;
    }).join("");

    const html = `<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<title>Notinha ${escapeHtml(order.publicCode)}</title>
<style>
  @page { margin: 6mm; size: 80mm auto; }
  * { box-sizing: border-box; }
  body {
    margin: 0;
    font-family: "Courier New", Courier, monospace;
    font-size: 12px;
    color: #111;
    background: #fff;
  }
  .ticket {
    width: 72mm;
    max-width: 100%;
    margin: 0 auto;
    padding: 4mm 2mm 8mm;
  }
  .center { text-align: center; }
  .muted { color: #444; }
  h1 {
    margin: 0 0 2px;
    font-size: 14px;
    font-weight: 700;
    text-transform: uppercase;
  }
  .banner {
    margin: 8px 0;
    padding: 4px 0;
    border-top: 1px dashed #111;
    border-bottom: 1px dashed #111;
    font-weight: 700;
    letter-spacing: 0.04em;
    text-transform: uppercase;
  }
  .line { margin: 2px 0; }
  .sep { border: 0; border-top: 1px dashed #111; margin: 8px 0; }
  table { width: 100%; border-collapse: collapse; }
  th, td { padding: 2px 0; vertical-align: top; }
  th { font-size: 10px; text-align: left; border-bottom: 1px solid #111; }
  td.qty { width: 22%; }
  td.name { width: 40%; word-break: break-word; }
  td.money { width: 19%; text-align: right; white-space: nowrap; }
  .totals .label { text-align: left; }
  .totals .value { text-align: right; font-weight: 700; }
  .totals .grand .value { font-size: 14px; }
  .foot {
    margin-top: 10px;
    font-size: 10px;
    text-align: center;
  }
  .blank { margin-top: 10px; font-size: 11px; }
  .no-print { margin: 12px auto; text-align: center; }
  @media print {
    .no-print { display: none !important; }
  }
</style>
</head>
<body>
  <div class="no-print">
    <button onclick="window.print()" style="padding:8px 14px;font-size:14px;cursor:pointer;">Imprimir</button>
  </div>
  <div class="ticket">
    <div class="center">
      <h1>${escapeHtml(store.name || "Loja")}</h1>
      ${store.phone ? `<div class="line muted">${escapeHtml(formatPhoneDisplay(store.phone))}</div>` : ""}
      ${store.address ? `<div class="line muted">${escapeHtml(store.address)}</div>` : ""}
    </div>
    <div class="banner center">Documento não fiscal</div>
    <div class="line"><strong>Pedido:</strong> ${escapeHtml(order.publicCode)}</div>
    <div class="line"><strong>Data:</strong> ${escapeHtml(formatOrderDateTime(order.createdAt))}</div>
    <div class="line"><strong>Status:</strong> ${escapeHtml(statusLabel(order.status))}</div>
    <div class="line"><strong>Tipo:</strong> ${escapeHtml(fulfillmentLabel(order.fulfillmentType))}</div>
    <hr class="sep">
    <div class="line"><strong>Cliente:</strong> ${escapeHtml(order.customerName)}</div>
    <div class="line"><strong>Telefone:</strong> ${escapeHtml(formatPhoneDisplay(order.customerPhone))}</div>
    ${isDelivery && addressLine
        ? `<div class="line"><strong>Endereço:</strong> ${escapeHtml(addressLine)}</div>`
        : `<div class="line"><strong>Retirada</strong> na loja</div>`}
    ${order.notes ? `<div class="line"><strong>Obs.:</strong> ${escapeHtml(order.notes)}</div>` : ""}
    <hr class="sep">
    <table>
      <thead>
        <tr>
          <th>Qtd</th>
          <th>Item</th>
          <th style="text-align:right">Unit.</th>
          <th style="text-align:right">Total</th>
        </tr>
      </thead>
      <tbody>
        ${itemsHtml || `<tr><td colspan="4">Sem itens</td></tr>`}
      </tbody>
    </table>
    <hr class="sep">
    <table class="totals">
      <tr><td class="label">Subtotal</td><td class="value">${escapeHtml(formatBRL(order.subtotal))}</td></tr>
      <tr><td class="label">Desconto</td><td class="value">${escapeHtml(formatBRL(order.discount || 0))}</td></tr>
      <tr><td class="label">Entrega</td><td class="value">${escapeHtml(formatBRL(order.deliveryFee || 0))}</td></tr>
      <tr class="grand"><td class="label">TOTAL</td><td class="value">${escapeHtml(formatBRL(order.total))}</td></tr>
    </table>
    <hr class="sep">
    <div class="line"><strong>Pagamento:</strong> ${escapeHtml(paymentMethodLabel(order.paymentMethod || order.payment?.method))}
      ${order.payment?.status ? ` (${escapeHtml(paymentStatusLabel(order.payment.status))})` : ""}</div>
    <div class="blank">
      <div>Peso real (balança): _______________</div>
      <div>Conferido por: ___________________</div>
    </div>
    <div class="foot">
      SEM VALOR FISCAL<br>
      NFC-e / cupom fiscal emitidos no caixa após pesagem.
    </div>
  </div>
  <script>
    window.addEventListener("load", () => setTimeout(() => window.print(), 250));
  </script>
</body>
</html>`;

    const popup = window.open("", "_blank", "noopener,noreferrer,width=420,height=720");
    if (!popup) {
        window.alert("Permita pop-ups para imprimir a notinha.");
        return;
    }
    popup.document.open();
    popup.document.write(html);
    popup.document.close();
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

async function loadFiscalSettings() {
    try {
        const settings = await api("/fiscal/settings");
        const form = $("#fiscal-settings-form");
        if (!form) {
            return;
        }
        form.enabled.checked = !!settings.enabled;
        form.environment.value = settings.environment || "HOMOLOG";
        form.cnpj.value = settings.cnpj || "";
        form.autoEmitOnPaid.checked = settings.autoEmitOnPaid !== false;
        form.defaultNcm.value = settings.defaultNcm || "21069090";
        form.defaultCfop.value = settings.defaultCfop || "5102";
        form.icmsSituacaoTributaria.value = settings.icmsSituacaoTributaria || "102";
        const hint = $("#fiscal-token-hint");
        if (hint) {
            hint.textContent = settings.tokenConfigured
                ? "Token Focus já configurado. Deixe em branco para manter."
                : "Sem token — cadastre a empresa na Focus e cole o token aqui.";
        }
    } catch (error) {
        showFormAlert($("#fiscal-settings-alert"), error, "Não foi possível carregar o fiscal.");
    }
}

function appendNfceActions(actions, order) {
    const nfce = order.nfce;
    const status = nfce?.status || "NONE";
    if (status === "AUTHORIZED") {
        if (nfce.danfeUrl) {
            const danfe = document.createElement("a");
            danfe.className = "btn btn-secondary";
            danfe.href = nfce.danfeUrl;
            danfe.target = "_blank";
            danfe.rel = "noopener noreferrer";
            danfe.textContent = "Ver NFC-e";
            actions.append(danfe);
        }
        return;
    }
    if (status === "PROCESSING") {
        const refresh = document.createElement("button");
        refresh.type = "button";
        refresh.className = "btn btn-secondary";
        refresh.textContent = "Atualizar NFC-e";
        refresh.addEventListener("click", () => refreshNfce(order.id));
        actions.append(refresh);
        return;
    }
    const paid = order.payment?.status === "PAID" || ["CONFIRMED", "PREPARING", "DISPATCHED", "DELIVERED"].includes(order.status);
    if (!paid) {
        return;
    }
    const emit = document.createElement("button");
    emit.type = "button";
    emit.className = "btn btn-secondary";
    emit.textContent = status === "ERROR" || status === "DENIED" ? "Reemitir NFC-e" : "Emitir NFC-e";
    emit.addEventListener("click", () => emitNfce(order.id));
    actions.append(emit);
}

async function emitNfce(orderId) {
    try {
        await api(`/orders/${orderId}/nfce`, { method: "POST", body: {} });
        hideAlert($("#orders-alert"));
        await refreshOrders();
    } catch (error) {
        showFormAlert($("#orders-alert"), error, "Não foi possível emitir a NFC-e.");
    }
}

async function refreshNfce(orderId) {
    try {
        await api(`/orders/${orderId}/nfce/refresh`, { method: "POST", body: {} });
        hideAlert($("#orders-alert"));
        await refreshOrders();
    } catch (error) {
        showFormAlert($("#orders-alert"), error, "Não foi possível atualizar a NFC-e.");
    }
}

function nfceStatusLabel(status) {
    return ({
        NONE: "não emitida",
        PROCESSING: "processando",
        AUTHORIZED: "autorizada",
        DENIED: "negada",
        CANCELLED: "cancelada",
        ERROR: "erro"
    })[status] || status;
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
        form.minOrderAmount.value = settings.minOrderAmount ?? "";
        form.pickupEtaMinutes.value = settings.pickupEtaMinutes ?? "";
        form.deliveryEtaMinutes.value = settings.deliveryEtaMinutes ?? settings.estimatedMinutes ?? "";
        window.__deliverySettings = settings;
        renderStoreOpsCard();
    } catch (error) {
        showFormAlert($("#delivery-settings-alert"), error, "Não foi possível carregar frete.");
    }
}

function renderStoreOpsCard() {
    const nameEl = $("#store-ops-name");
    const badge = $("#store-ops-open-badge");
    const meta = $("#store-ops-meta");
    const stats = $("#store-ops-stats");
    const cover = $("#store-ops-cover");
    const logo = $("#store-ops-logo");
    if (!nameEl || !establishment) {
        return;
    }
    nameEl.textContent = establishment.name ?? "Loja";
    $("#store-name").textContent = establishment.name ?? "Estabelecimento";
    const open = !!establishment.acceptingOrders;
    if (badge) {
        badge.textContent = open ? "Aberta" : "Fechada";
        badge.className = `store-status-badge ${open ? "is-open" : "is-closed"}`;
    }
    if (meta) {
        meta.textContent = [
            establishment.address,
            [establishment.city, establishment.state].filter(Boolean).join("/")
        ].filter(Boolean).join(" · ") || storeUrl(establishment.slug);
    }
    if (cover) {
        if (establishment.coverUrl) {
            cover.style.backgroundImage = `url("${optimizedImageUrl(establishment.coverUrl, { width: 1600, height: 700, mode: "fill" })}")`;
            cover.classList.add("has-image");
        } else {
            cover.style.backgroundImage = "";
            cover.classList.remove("has-image");
        }
    }
    if (logo) {
        if (establishment.logoUrl) {
            logo.src = optimizedImageUrl(establishment.logoUrl, { width: 256, height: 256 });
            logo.alt = establishment.name ?? "";
            logo.hidden = false;
        } else {
            logo.hidden = true;
        }
    }
    if (stats) {
        stats.replaceChildren();
        const delivery = window.__deliverySettings || {};
        const chips = [];
        if (establishment.ratingCount > 0 && establishment.ratingAvg != null) {
            chips.push(["Avaliação", `${establishment.ratingAvg} ★`]);
        }
        if (delivery.pickupEtaMinutes != null) {
            chips.push(["Retirada", `${delivery.pickupEtaMinutes} min`]);
        }
        if (delivery.deliveryEtaMinutes != null) {
            chips.push(["Entrega", `${delivery.deliveryEtaMinutes} min`]);
        }
        if (delivery.minOrderAmount != null) {
            chips.push(["Pedido mín.", formatBRL(delivery.minOrderAmount)]);
        }
        chips.forEach(([label, value]) => {
            const item = document.createElement("div");
            item.className = "store-ops-stat";
            const strong = document.createElement("strong");
            strong.textContent = value;
            const span = document.createElement("span");
            span.className = "muted";
            span.textContent = label;
            item.append(strong, span);
            stats.append(item);
        });
    }
}

function wireStoreProfileForm() {
    const form = $("#store-profile-form");
    if (!form || !establishment) {
        return;
    }
    try {
        fillStoreProfileForm(establishment);
    } catch (error) {
        console.error(error);
        renderOpeningHoursEditor(establishment.openingHours || {});
        showFormAlert($("#store-profile-alert"), error, "Não foi possível carregar todos os dados da loja.");
    }
    wireStoreMediaUploads();
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        await saveStoreProfile();
    });
}

async function saveStoreProfile({ skipCarouselValidation = false, silent = false } = {}) {
    const form = $("#store-profile-form");
    const alertBox = $("#store-profile-alert");
    if (!form || !establishment) {
        return;
    }
    if (!skipCarouselValidation && !validateFormCarousel(form)) {
        return;
    }
    const hours = readOpeningHoursFromEditor();
    if (!hoursValid(hours)) {
        showFormAlert(alertBox, null, "Confira os horários: abertura precisa ser antes do fechamento.");
        formCarousels.get(form)?.go(1);
        return;
    }
    const submitBtn = form.querySelector("[data-carousel-submit]");
    if (submitBtn && !silent) {
        submitBtn.disabled = true;
        submitBtn.textContent = "Salvando…";
    }
    try {
        const body = {
            name: control(form, "name")?.value,
            phone: form.phone.value || null,
            address: form.address.value || null,
            logoUrl: form.logoUrl.value || null,
            coverUrl: form.coverUrl.value || null,
            storeOpenMode: form.storeOpenMode.value,
            openingHours: hours
        };
        establishment = await api(`/establishments/${establishment.id}`, {
            method: "PUT",
            body
        });
        fillStoreProfileForm(establishment);
        renderStoreOpsCard();
        showFormSuccess(alertBox, "Loja atualizada.");
        $("#store-name").textContent = establishment?.name ?? "Estabelecimento";
    } catch (error) {
        showFormAlert(alertBox, error, "Não foi possível salvar a loja.");
        throw error;
    } finally {
        if (submitBtn && !silent) {
            submitBtn.disabled = false;
            submitBtn.textContent = "Salvar loja";
        }
    }
}

function fillStoreProfileForm(store) {
    const form = $("#store-profile-form");
    if (!form || !store) {
        return;
    }
    const nameInput = control(form, "name");
    if (nameInput) {
        nameInput.value = store.name ?? "";
    }
    form.phone.value = store.phone ?? "";
    form.address.value = store.address ?? "";
    form.logoUrl.value = store.logoUrl ?? "";
    form.coverUrl.value = store.coverUrl ?? "";
    form.storeOpenMode.value = store.storeOpenMode ?? "AUTO";
    setStoreMediaPreview("#store-logo-preview", "#store-logo-preview-img", store.logoUrl, { width: 256, height: 256 });
    setStoreMediaPreview("#store-cover-preview", "#store-cover-preview-img", store.coverUrl, { width: 800, height: 320 });
    renderOpeningHoursEditor(store.openingHours || {});
}

function normalizeClock(value, fallback = "08:00") {
    const raw = String(value || "").trim();
    const match = raw.match(/^(\d{1,2}):(\d{2})$/);
    if (!match) {
        return fallback;
    }
    const hour = Math.min(23, Math.max(0, Number(match[1])));
    const minute = Math.min(59, Math.max(0, Number(match[2])));
    return `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
}

function hoursValid(hours) {
    return Object.values(hours || {}).every((intervals) => {
        if (!intervals?.length) {
            return true;
        }
        return intervals.every((item) => item.open && item.close && item.open !== item.close);
    });
}

function renderOpeningHoursEditor(hours) {
    const box = $("#opening-hours-editor");
    if (!box) {
        return;
    }
    box.replaceChildren();
    const source = hours || {};
    Object.keys(DAY_LABELS).forEach((day) => {
        const intervals = source[day] || [];
        const first = intervals[0];
        const open = !!first;
        const row = document.createElement("div");
        row.className = `hours-row${open ? " is-open" : " is-closed"}`;
        row.dataset.day = day;

        const toggle = document.createElement("label");
        toggle.className = "hours-toggle";
        const checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.name = `${day}-open-day`;
        checkbox.checked = open;
        const dayName = document.createElement("span");
        dayName.textContent = DAY_LABELS[day];
        toggle.append(checkbox, dayName);

        const times = document.createElement("div");
        times.className = "hours-times";
        const openInput = document.createElement("input");
        openInput.type = "time";
        openInput.name = `${day}-open`;
        openInput.value = normalizeClock(first?.open, "08:00");
        openInput.setAttribute("aria-label", `Abertura ${DAY_LABELS[day]}`);
        const sep = document.createElement("span");
        sep.className = "hours-sep";
        sep.textContent = "às";
        const closeInput = document.createElement("input");
        closeInput.type = "time";
        closeInput.name = `${day}-close`;
        closeInput.value = normalizeClock(first?.close, "18:00");
        closeInput.setAttribute("aria-label", `Fechamento ${DAY_LABELS[day]}`);
        times.append(openInput, sep, closeInput);

        const status = document.createElement("span");
        status.className = "hours-status";

        function syncRow() {
            const enabled = checkbox.checked;
            openInput.disabled = !enabled;
            closeInput.disabled = !enabled;
            row.classList.toggle("is-open", enabled);
            row.classList.toggle("is-closed", !enabled);
            status.textContent = enabled
                ? `${normalizeClock(openInput.value)} – ${normalizeClock(closeInput.value)}`
                : "Fechado";
            updateHoursPreview();
        }

        checkbox.addEventListener("change", syncRow);
        openInput.addEventListener("change", syncRow);
        closeInput.addEventListener("change", syncRow);
        syncRow();

        row.append(toggle, times, status);
        box.append(row);
    });
    updateHoursPreview();
}

function readOpeningHoursFromEditor() {
    const box = $("#opening-hours-editor");
    const result = {};
    if (!box) {
        return result;
    }
    box.querySelectorAll(".hours-row").forEach((row) => {
        const day = row.dataset.day;
        const openDay = row.querySelector(`input[name="${day}-open-day"]`)?.checked;
        if (!openDay) {
            result[day] = [];
            return;
        }
        const open = normalizeClock(row.querySelector(`input[name="${day}-open"]`)?.value, "08:00");
        const close = normalizeClock(row.querySelector(`input[name="${day}-close"]`)?.value, "18:00");
        result[day] = [{ open, close }];
    });
    return result;
}

function updateHoursPreview() {
    const preview = $("#hours-preview");
    if (!preview) {
        return;
    }
    const hours = readOpeningHoursFromEditor();
    const openDays = Object.entries(DAY_LABELS)
        .filter(([key]) => (hours[key] || []).length)
        .map(([key, label]) => {
            const slot = hours[key][0];
            return `${label} ${slot.open}–${slot.close}`;
        });
    preview.textContent = openDays.length
        ? `Automático: ${openDays.join(" · ")}`
        : "Nenhum dia marcado — no modo automático a loja fica fechada.";
}

function wireStoreMediaUploads() {
    bindMediaUpload("#store-logo-file", "#store-logo-url", {
        maxEdge: 800,
        previewBox: "#store-logo-preview",
        previewImg: "#store-logo-preview-img",
        previewSize: { width: 256, height: 256 }
    });
    bindMediaUpload("#store-cover-file", "#store-cover-url", {
        maxEdge: 1600,
        previewBox: "#store-cover-preview",
        previewImg: "#store-cover-preview-img",
        previewSize: { width: 800, height: 320 }
    });
}

function setStoreMediaPreview(boxSelector, imgSelector, url, size) {
    const box = $(boxSelector);
    const img = $(imgSelector);
    if (!box || !img) {
        return;
    }
    if (!url) {
        box.hidden = true;
        img.removeAttribute("src");
        return;
    }
    setPreviewImage(img, url, size);
    box.hidden = false;
}

function bindMediaUpload(fileSelector, urlSelector, options = {}) {
    const fileInput = $(fileSelector);
    const urlInput = $(urlSelector);
    fileInput?.addEventListener("change", async () => {
        const file = fileInput.files?.[0];
        if (!file) {
            return;
        }
        if (!mediaState.enabled) {
            showFormAlert($("#store-profile-alert"), null, "Configure Cloudinary para enviar arquivo.");
            fileInput.value = "";
            return;
        }
        try {
            const compact = await compressImageFile(file, { maxEdge: options.maxEdge ?? 1600 });
            if (compact.size > 5 * 1024 * 1024) {
                showFormAlert($("#store-profile-alert"), null, "Imagem ainda grande demais após compactar. Use outra foto (até 5 MB).");
                fileInput.value = "";
                return;
            }
            const formData = new FormData();
            formData.append("file", compact, compact.name || "loja.jpg");
            const uploaded = await apiUpload("/media/upload", formData);
            if (urlInput) {
                urlInput.value = uploaded.url;
            }
            setStoreMediaPreview(options.previewBox, options.previewImg, uploaded.url, options.previewSize);
            await saveStoreProfile({ skipCarouselValidation: true, silent: true });
            showFormSuccess($("#store-profile-alert"), "Imagem enviada e loja atualizada.");
        } catch (error) {
            showFormAlert($("#store-profile-alert"), error, "Falha ao enviar imagem.");
            fileInput.value = "";
        }
    });
    urlInput?.addEventListener("change", () => {
        setStoreMediaPreview(options.previewBox, options.previewImg, urlInput.value.trim(), options.previewSize);
    });
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

function wireAdminNav() {
    const nav = $("#admin-nav");
    const stage = $("#admin-stage");
    nav?.addEventListener("click", (event) => {
        const tab = event.target.closest(".admin-tab");
        if (tab && !tab.hidden) {
            showAdminPanel(tab.dataset.panel);
        }
    });
    const allowed = visibleAdminPanels();
    const hash = window.location.hash.replace("#", "");
    showAdminPanel(allowed.includes(hash) ? hash : (allowed[0] || "pedidos"));

    let touchStartX = 0;
    let ignoreSwipe = false;
    stage?.addEventListener("touchstart", (event) => {
        touchStartX = event.changedTouches[0].clientX;
        ignoreSwipe = Boolean(event.target.closest("input, textarea, select, button, a, label"));
    }, { passive: true });
    stage?.addEventListener("touchend", (event) => {
        if (ignoreSwipe) {
            return;
        }
        const delta = event.changedTouches[0].clientX - touchStartX;
        if (Math.abs(delta) < 56) {
            return;
        }
        const list = visibleAdminPanels();
        const current = document.querySelector(".admin-panel.is-active")?.dataset.panel;
        const index = list.indexOf(current);
        const next = delta < 0 ? list[index + 1] : list[index - 1];
        if (next) {
            showAdminPanel(next);
        }
    }, { passive: true });
}

function visibleAdminPanels() {
    return [...document.querySelectorAll(".admin-tab")]
        .filter((tab) => !tab.hidden)
        .map((tab) => tab.dataset.panel);
}

function showAdminPanel(id) {
    document.querySelectorAll(".admin-panel").forEach((panel) => {
        panel.classList.toggle("is-active", panel.dataset.panel === id && !panel.hidden);
    });
    document.querySelectorAll(".admin-tab").forEach((tab) => {
        tab.classList.toggle("is-active", tab.dataset.panel === id);
    });
    document.querySelector(`.admin-tab[data-panel="${id}"]`)
        ?.scrollIntoView({ inline: "center", block: "nearest", behavior: "smooth" });
    if (id) {
        history.replaceState(null, "", `#${id}`);
    }
}

function wireFormCarousels() {
    document.querySelectorAll("[data-carousel]").forEach(wireFormCarousel);
}

function resetFormCarousel(form) {
    formCarousels.get(form)?.go(0);
}

function validateFormCarousel(form) {
    return formCarousels.get(form)?.validateAll() ?? true;
}

function wireFormCarousel(form) {
    const slides = [...form.querySelectorAll(".form-slide")];
    if (!slides.length) {
        return;
    }
    const prev = form.querySelector("[data-carousel-prev]");
    const next = form.querySelector("[data-carousel-next]");
    const submit = form.querySelector("[data-carousel-submit]");
    const dotsBox = form.querySelector("[data-carousel-dots]");
    let step = 0;
    if (dotsBox) {
        dotsBox.replaceChildren();
        slides.forEach((_, index) => {
            const dot = document.createElement("button");
            dot.type = "button";
            dot.setAttribute("aria-label", `Passo ${index + 1}`);
            dot.addEventListener("click", () => go(index));
            dotsBox.append(dot);
        });
    }

    function requiredFields(slide) {
        return [...slide.querySelectorAll("[required]")];
    }

    function go(index) {
        step = Math.max(0, Math.min(slides.length - 1, index));
        slides.forEach((slide, i) => slide.classList.toggle("is-active", i === step));
        dotsBox?.querySelectorAll("button").forEach((dot, i) => {
            dot.classList.toggle("is-active", i === step);
        });
        if (prev) {
            prev.disabled = step === 0;
        }
        if (next) {
            next.hidden = step === slides.length - 1;
        }
        if (submit) {
            const onLast = step === slides.length - 1;
            submit.hidden = !onLast;
            submit.setAttribute("aria-hidden", onLast ? "false" : "true");
            if (!onLast) {
                submit.tabIndex = -1;
            } else {
                submit.removeAttribute("tabindex");
            }
        }
    }

    function validateCurrent() {
        const invalid = requiredFields(slides[step]).find((field) => !field.checkValidity());
        if (invalid) {
            invalid.reportValidity();
            return false;
        }
        return true;
    }

    function validateAll() {
        for (let i = 0; i < slides.length; i += 1) {
            const invalid = requiredFields(slides[i]).find((field) => !field.checkValidity());
            if (invalid) {
                go(i);
                invalid.reportValidity();
                return false;
            }
        }
        return true;
    }

    prev?.addEventListener("click", () => go(step - 1));
    next?.addEventListener("click", () => {
        if (validateCurrent()) {
            go(step + 1);
        }
    });
    go(0);
    formCarousels.set(form, { go, validateAll });
}

