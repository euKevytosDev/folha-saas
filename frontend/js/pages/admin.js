import { api, apiUpload, ApiError } from "../api/client.js";
import { logout, requirePageAuth } from "../auth/api.js";
import { $ } from "../utils/dom.js";
import { formatBRL, formatQuantity, discountPercent } from "../utils/format.js";
import { compressImageFile } from "../utils/image.js";
import { createThumb, optimizedImageUrl, setPreviewImage } from "../utils/media.js";
import { storeUrl } from "../utils/nav.js";
import { config } from "../config.js";

function roleLabel(role) {
    if (role === "OWNER") {
        return "Admin";
    }
    if (role === "ADMIN") {
        return "Gerente";
    }
    if (role === "STAFF") {
        return "Atendente";
    }
    return role || "";
}

function formatDiscountNumber(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) {
        return String(value ?? "");
    }
    return number.toLocaleString("pt-BR", { maximumFractionDigits: 2 });
}

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
$("#user-role").textContent = roleLabel(user.role);
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
/** @type {any[]} */
let catalogProducts = [];
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

const ORDER_POLL_MS = 3 * 60 * 1000;
const orderState = { status: "OPEN" };
const cashState = { preset: "today" };
let knownOrderIds = new Set();
let freshOrderIds = new Set();
let ordersPollTimer = 0;
let audioCtx = null;
let soundUnlocked = false;
let orderToastTimer = 0;
renderOrderFilters();
$("#dashboard-see-orders")?.addEventListener("click", () => showAdminPanel("pedidos"));
$("#enable-order-sound")?.addEventListener("click", () => {
    unlockOrderSound();
    playNewOrderChime();
});
document.addEventListener("pointerdown", unlockOrderSound);
await refreshOrders().catch((error) => {
    showFormAlert($("#orders-alert"), error, "Não foi possível carregar os pedidos.");
});

function unlockOrderSound() {
    if (soundUnlocked) {
        return;
    }
    soundUnlocked = true;
    const button = $("#enable-order-sound");
    if (button) {
        button.hidden = true;
    }
    try {
        audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
        if (audioCtx.state === "suspended") {
            audioCtx.resume();
        }
    } catch {
        // navegador sem áudio
    }
    if ("Notification" in window && Notification.permission === "default") {
        Notification.requestPermission().catch(() => {});
    }
}

function playNewOrderChime() {
    try {
        audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
        const start = () => {
            const now = audioCtx.currentTime;
            [784, 1046].forEach((freq, index) => {
                const osc = audioCtx.createOscillator();
                const gain = audioCtx.createGain();
                const at = now + index * 0.18;
                osc.type = "sine";
                osc.frequency.value = freq;
                gain.gain.setValueAtTime(0.0001, at);
                gain.gain.exponentialRampToValueAtTime(0.22, at + 0.02);
                gain.gain.exponentialRampToValueAtTime(0.0001, at + 0.32);
                osc.connect(gain);
                gain.connect(audioCtx.destination);
                osc.start(at);
                osc.stop(at + 0.34);
            });
        };
        if (audioCtx.state === "suspended") {
            audioCtx.resume().then(start).catch(() => {});
            return;
        }
        start();
    } catch {
        // ignore autoplay restrictions
    }
}

function showOrderToast(message) {
    const toast = $("#order-toast");
    if (!toast) {
        return;
    }
    toast.hidden = false;
    toast.textContent = message;
    window.clearTimeout(orderToastTimer);
    orderToastTimer = window.setTimeout(() => {
        toast.hidden = true;
    }, 8000);
}

function announceNewOrders(fresh) {
    playNewOrderChime();
    const count = fresh?.length || 1;
    const first = fresh?.[0];
    const text = count === 1 && first
        ? `Novo pedido ${first.publicCode} · ${first.customerName}`
        : `${count} pedidos novos`;
    showOrderToast(text);
    if (document.hidden && "Notification" in window && Notification.permission === "granted") {
        try {
            new Notification("Novo pedido", { body: text });
        } catch {
            // notificação do sistema indisponível
        }
    }
}

async function pollOrdersQuietly() {
    try {
        const result = await refreshOrders({ silent: true, detectNew: true });
        if (result?.hasNew) {
            announceNewOrders(result.fresh);
        }
    } catch {
        // keep polling
    }
}

function scheduleOrdersPoll() {
    window.clearTimeout(ordersPollTimer);
    ordersPollTimer = window.setTimeout(async () => {
        await pollOrdersQuietly();
        scheduleOrdersPoll();
    }, ORDER_POLL_MS);
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
    $("#payment-provider")?.addEventListener("change", () => updatePaymentProviderHelp(loadedPaymentSettings));
    $("#payment-settings-form")?.addEventListener("submit", async (event) => {
        event.preventDefault();
        const form = event.currentTarget;
        const alertBox = $("#payment-settings-alert");
        try {
            const body = {
                provider: form.provider.value,
                pixEnabled: form.pixEnabled.checked,
                mockMode: form.mockMode.checked,
                sandbox: form.sandbox.checked,
                onlineEnabled: false
            };
            if (form.accessToken.value.trim()) {
                body.accessToken = form.accessToken.value.trim();
            }
            if (form.webhookSecret.value.trim()) {
                body.webhookSecret = form.webhookSecret.value.trim();
            }
            await api("/payments/settings", { method: "PUT", body });
            form.accessToken.value = "";
            form.webhookSecret.value = "";
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
        const maximumQuantity = readMaximumQuantity(form);
        if (maximumQuantity === undefined) {
            showFormAlert(alertBox, null, "Informe a quantidade máxima válida ou deixe em branco.");
            formCarousels.get(form)?.go(1);
            return;
        }
        if (maximumQuantity != null && maximumQuantity < minimumQuantity) {
            showFormAlert(alertBox, null, "A quantidade máxima precisa ser maior ou igual à mínima.");
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
            if ((!Number.isFinite(price) || price <= 0) && variants.items.length) {
                const priced = variants.items.map((item) => item.price).filter((value) => value != null && value > 0);
                if (priced.length) {
                    price = Math.min(...priced);
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
                maximumQuantity: variants.items.length
                    ? (maximumQuantity == null ? minimumQuantity : maximumQuantity)
                    : (maximumQuantity == null ? 0 : maximumQuantity),
                variantMinChoices: 1,
                variantMaxChoices: 1,
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
        syncProductFormMode();
    }

    $("#bulk-kg-min-btn")?.addEventListener("click", () => applyBulkKgMinimum());

    let productSearchTimer = 0;
    $("#product-search")?.addEventListener("input", () => {
        window.clearTimeout(productSearchTimer);
        productSearchTimer = window.setTimeout(() => {
            renderProducts(catalogProducts);
        }, 120);
    });
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
    toggle?.addEventListener("change", () => {
        syncProductVariantFields(form);
        syncMinQtyField(form);
    });
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
    priceLabel.textContent = "Preço extra (opc.)";
    const priceInput = document.createElement("input");
    priceInput.type = "number";
    priceInput.name = "variantPrice";
    priceInput.min = "0.01";
    priceInput.step = "0.01";
    priceInput.placeholder = "Soma no preço";
    priceInput.value = variant?.price != null ? String(variant.price) : "";
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
        const priceRaw = String(row.querySelector('[name="variantPrice"]')?.value ?? "").trim();
        if (!name && !priceRaw) {
            continue;
        }
        if (!name) {
            return { ok: false, error: "Informe o nome de cada sabor." };
        }
        let price = null;
        if (priceRaw) {
            price = Number(priceRaw.replace(",", "."));
            if (!Number.isFinite(price) || price <= 0) {
                return { ok: false, error: `Preço inválido no sabor "${name}". Deixe vazio para usar o preço do produto.` };
            }
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

function productHasVariants(form) {
    return !!(form?.hasVariants?.checked || $("#product-has-variants")?.checked);
}

function syncMinQtyField(form, options = {}) {
    if (!form) {
        return;
    }
    const input = control(form, "minimumQuantityInput") || $("#product-min-qty");
    const label = $("#product-min-qty-label");
    const hint = $("#product-min-qty-hint");
    const maxInput = control(form, "maximumQuantityInput") || $("#product-max-qty");
    const maxLabel = $("#product-max-qty-label");
    const maxHint = $("#product-max-qty-hint");
    if (!input) {
        return;
    }
    const unit = String(form.unit?.value || "UN").toUpperCase();
    if (productHasVariants(form)) {
        if (label) {
            label.textContent = "Mínimo de opções no combo";
        }
        if (hint) {
            hint.textContent = "Ex.: 3 sucos por R$ 12,99. A pessoa escolhe 3 opções e paga o preço do produto. Pode repetir o mesmo sabor.";
        }
        input.min = "1";
        input.step = "1";
        if (options.resetValue || !input.value || (unit === "KG" && Number(input.value) >= 50)) {
            input.value = "1";
        }
        if (maxLabel) {
            maxLabel.textContent = "Máximo de opções no combo";
        }
        if (maxHint) {
            maxHint.textContent = "Use o mesmo número do mínimo para obrigar essa quantidade (3 e 3 = exatamente 3). Valor preenchido no sabor soma no preço.";
        }
        if (maxInput) {
            maxInput.min = "1";
            maxInput.step = "1";
            maxInput.placeholder = "Igual ao mínimo";
            if (options.resetValue) {
                maxInput.value = "";
            }
        }
        return;
    }
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
        if (maxLabel) {
            maxLabel.textContent = "Quantidade máxima de compra (gramas)";
        }
        if (maxHint) {
            maxHint.textContent = "Opcional. Ex.: máx. 2000 g (2 kg). Deixe em branco sem limite.";
        }
        if (maxInput) {
            maxInput.min = "50";
            maxInput.step = "50";
            maxInput.placeholder = "Sem limite";
            if (options.resetValue) {
                maxInput.value = "";
            }
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
        if (maxLabel) {
            maxLabel.textContent = "Quantidade máxima de compra";
        }
        if (maxHint) {
            maxHint.textContent = "Opcional. Deixe em branco sem limite por pedido deste item.";
        }
        if (maxInput) {
            maxInput.min = "1";
            maxInput.step = "1";
            maxInput.placeholder = "Sem limite";
            if (options.resetValue) {
                maxInput.value = "";
            }
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
    if (productHasVariants(form)) {
        return Math.max(1, Math.round(raw));
    }
    const unit = String(form.unit?.value || "UN").toUpperCase();
    if (unit === "KG") {
        return Math.round(raw) / 1000;
    }
    return Math.round(raw * 1000) / 1000;
}

/** null = sem máximo; undefined = valor inválido. */
function readMaximumQuantity(form) {
    const input = control(form, "maximumQuantityInput") || $("#product-max-qty");
    const rawText = String(input?.value ?? "").trim();
    if (!rawText) {
        return null;
    }
    const raw = Number(rawText);
    if (!Number.isFinite(raw) || raw <= 0) {
        return undefined;
    }
    if (productHasVariants(form)) {
        return Math.max(1, Math.round(raw));
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
    const flavored = Array.isArray(item?.variants) && item.variants.length > 0;
    const legacyMax = Math.max(1, Number(item?.variantMaxChoices) || 1);
    const legacyMin = Math.max(1, Number(item?.variantMinChoices) || 1);
    const unit = String(item?.unit || form.unit?.value || "UN").toUpperCase();
    let min = Number(item?.minimumQuantity);
    const useLegacySlots = flavored && legacyMax > 1 && (!Number.isFinite(min) || min <= 1);
    if (useLegacySlots) {
        min = legacyMin;
    }
    if (!Number.isFinite(min) || min <= 0) {
        input.value = !flavored && unit === "KG" ? "100" : "1";
    } else if (!flavored && unit === "KG") {
        input.value = String(Math.round(min * 1000));
    } else {
        input.value = String(Math.round(min));
    }
    fillMaximumQuantityInput(form, item);
    if (useLegacySlots) {
        const maxInput = control(form, "maximumQuantityInput") || $("#product-max-qty");
        if (maxInput) {
            maxInput.value = String(legacyMax);
        }
    }
}

function fillMaximumQuantityInput(form, item) {
    const input = control(form, "maximumQuantityInput") || $("#product-max-qty");
    if (!input) {
        return;
    }
    const flavored = Array.isArray(item?.variants) && item.variants.length > 0;
    const unit = String(item?.unit || form.unit?.value || "UN").toUpperCase();
    const max = Number(item?.maximumQuantity);
    if (!Number.isFinite(max) || max <= 0) {
        input.value = "";
        return;
    }
    if (flavored) {
        input.value = String(Math.round(max));
        return;
    }
    if (unit === "KG") {
        input.value = String(Math.round(max * 1000));
    } else {
        input.value = String(max);
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
        meta.textContent = `${item.email} · ${roleLabel(item.role)}`;
        identity.append(name, meta);
        row.append(identity);
        list.append(row);
    });
}

async function refreshCatalog() {
    const [categories, products] = await Promise.all([api("/categories"), api("/products")]);
    catalogCategories = Array.isArray(categories) ? categories : [];
    catalogProducts = Array.isArray(products) ? products : [];
    renderCategoryOptions(catalogCategories);
    renderCategories(catalogCategories);
    renderProducts(catalogProducts);
}

function filteredCatalogProducts(products) {
    const query = ($("#product-search")?.value || "").trim().toLowerCase();
    if (!query) {
        return products || [];
    }
    return (products || []).filter((item) => {
        const haystack = [
            item.name,
            item.categoryName,
            item.unit,
            ...(Array.isArray(item.variants) ? item.variants.map((variant) => variant.name) : [])
        ]
            .filter(Boolean)
            .join(" ")
            .toLowerCase();
        return haystack.includes(query);
    });
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

function syncProductFormMode(name = "") {
    const block = $("#product-form-block");
    const mode = $("#product-form-mode");
    const title = $("#product-form-title");
    const cancelBtn = $("#product-cancel-edit");
    const form = $("#product-form");
    const submitBtn = form?.querySelector("[data-carousel-submit]");
    const editing = editingProductId != null;
    block?.classList.toggle("is-editing", editing);
    if (mode) {
        mode.textContent = editing ? "Editando produto" : "Cadastrando novo";
    }
    if (title) {
        title.textContent = editing
            ? (name ? `Editar · ${name}` : "Editar produto")
            : "Novo produto";
    }
    if (cancelBtn) {
        cancelBtn.hidden = !editing;
    }
    if (submitBtn) {
        submitBtn.textContent = editing ? "Salvar alterações" : "Salvar produto";
    }
}

function clearProductForm(form) {
    if (!form) {
        return;
    }
    editingProductId = null;
    syncProductFormMode();
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
    renderProducts(catalogProducts);
}

function beginEditProduct(item) {
    const form = $("#product-form");
    if (!form || !item) {
        return;
    }
    editingProductId = item.id;
    syncProductFormMode(item.name || "");
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
    fillProductVariants(item.variants || [], item);
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
    renderProducts(catalogProducts);
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
    const visible = filteredCatalogProducts(products);
    const query = ($("#product-search")?.value || "").trim();
    if (!products.length) {
        const empty = document.createElement("p");
        empty.className = "muted";
        empty.textContent = "Nenhum produto ainda.";
        list.append(empty);
        return;
    }
    if (!visible.length) {
        const empty = document.createElement("p");
        empty.className = "muted";
        empty.textContent = query
            ? `Nenhum produto encontrado para “${query}”.`
            : "Nenhum produto ainda.";
        list.append(empty);
        return;
    }
    visible.forEach((item) => {
        const row = document.createElement("div");
        row.className = "product-admin-row";
        if (editingProductId === item.id) {
            row.classList.add("is-editing");
        }
        const title = document.createElement("strong");
        title.textContent = item.name;
        const meta = document.createElement("div");
        meta.className = "product-admin-meta";
        const chips = [];
        chips.push({ text: item.categoryName || "Sem categoria" });
        chips.push({ text: `${formatBRL(item.price)} / ${item.unit}`, cls: "is-price" });
        if (!item.available) {
            chips.push({ text: "Oculto", cls: "is-hidden" });
        }
        if (item.stockControlled) {
            chips.push({ text: `Estoque ${item.stockQuantity ?? 0}` });
        }
        const variantCount = Array.isArray(item.variants) ? item.variants.length : 0;
        const minKg = Number(item.minimumQuantity || 0);
        if (!variantCount && item.unit === "KG" && minKg > 0.1 + 1e-9) {
            chips.push({ text: `Mín. ${Math.round(minKg * 1000)} g` });
        } else if (!variantCount && item.unit !== "KG" && Number(item.minimumQuantity) > 1) {
            chips.push({ text: `Mín. ${item.minimumQuantity}` });
        }
        const maxQty = Number(item.maximumQuantity);
        if (!variantCount && Number.isFinite(maxQty) && maxQty > 0) {
            chips.push({
                text: item.unit === "KG"
                    ? `Máx. ${Math.round(maxQty * 1000)} g`
                    : `Máx. ${maxQty}`
            });
        }
        const pct = discountPercent(item.price, item.compareAtPrice);
        if (pct != null) {
            chips.push({ text: `Promo −${pct}%`, cls: "is-promo" });
        }
        if (variantCount > 0) {
            let minC = Math.max(1, Math.round(Number(item.minimumQuantity) || 1));
            const maxRaw = Number(item.maximumQuantity);
            let maxC = Number.isFinite(maxRaw) && maxRaw > 0 ? Math.max(minC, Math.round(maxRaw)) : minC;
            const legacyMax = Math.max(1, Number(item.variantMaxChoices) || 1);
            const legacyMin = Math.max(1, Number(item.variantMinChoices) || 1);
            if (legacyMax > 1 && minC <= 1 && maxC <= 1) {
                minC = legacyMin;
                maxC = legacyMax;
            }
            chips.push({
                text: minC === maxC
                    ? `${variantCount} sabores · escolha ${minC}`
                    : `${variantCount} sabores · ${minC}–${maxC}`
            });
        }
        chips.forEach(({ text, cls }) => {
            const chip = document.createElement("span");
            chip.className = cls ? `product-admin-chip ${cls}` : "product-admin-chip";
            chip.textContent = text;
            meta.append(chip);
        });
        const body = document.createElement("div");
        const actions = document.createElement("div");
        actions.className = "product-admin-actions";
        const editBtn = document.createElement("button");
        editBtn.type = "button";
        editBtn.className = editingProductId === item.id ? "btn btn-primary" : "btn btn-secondary";
        editBtn.textContent = editingProductId === item.id ? "Editando" : "Editar";
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
            promoBtn.className = "btn btn-secondary";
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
    const fresh = options.detectNew && knownOrderIds.size > 0
        ? orders.filter((order) => !knownOrderIds.has(order.id))
        : [];
    knownOrderIds = nextIds;
    freshOrderIds = new Set(fresh.map((order) => order.id));
    renderOrderSummary(summary);
    renderOrders(filtered);
    renderDashboard(summary, orders);
    await refreshCash();
    return { hasNew: fresh.length > 0, fresh };
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

function renderDashboard(summary, orders) {
    const list = $("#dashboard-open-list");
    const badge = $("#orders-tab-count");
    const waiting = Number(summary?.pending || 0);
    const preparing = Number(summary?.confirmed || 0) + Number(summary?.preparing || 0);
    const onTheWay = Number(summary?.dispatched || 0);
    if (badge) {
        const attention = waiting + preparing + onTheWay;
        badge.hidden = attention <= 0;
        badge.textContent = String(attention);
    }
    if (!list) {
        return;
    }
    list.replaceChildren();
    const open = (orders || []).filter((order) => !["DELIVERED", "CANCELLED"].includes(order.status)).slice(0, 6);
    if (!open.length) {
        const empty = document.createElement("p");
        empty.className = "muted";
        empty.textContent = "Nenhum pedido em aberto.";
        list.append(empty);
        return;
    }
    open.forEach((order) => {
        const row = document.createElement("button");
        row.type = "button";
        row.className = "dashboard-order";
        if (freshOrderIds.has(order.id)) {
            row.classList.add("is-fresh");
        }
        const body = document.createElement("span");
        const title = document.createElement("strong");
        title.textContent = `${order.publicCode} · ${order.customerName}`;
        const meta = document.createElement("span");
        meta.className = "muted";
        meta.textContent = `${statusLabel(order.status)} · ${fulfillmentLabel(order.fulfillmentType)}`;
        body.append(title, meta);
        const total = document.createElement("strong");
        total.textContent = formatBRL(order.total);
        row.append(body, total);
        row.addEventListener("click", () => showAdminPanel("pedidos"));
        list.append(row);
    });
}

function saoPauloToday() {
    return new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo" }).format(new Date());
}

function shiftIsoDate(iso, days) {
    const [year, month, day] = iso.split("-").map(Number);
    return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}

function cashBounds(preset) {
    const today = saoPauloToday();
    if (preset === "week") {
        return { from: shiftIsoDate(today, -6), to: today };
    }
    if (preset === "month") {
        return { from: `${today.slice(0, 8)}01`, to: today };
    }
    if (preset === "last-month") {
        const [year, month] = today.split("-").map(Number);
        const start = new Date(Date.UTC(year, month - 2, 1));
        const end = new Date(Date.UTC(year, month - 1, 0));
        const iso = (date) => date.toISOString().slice(0, 10);
        return { from: iso(start), to: iso(end) };
    }
    return { from: today, to: today };
}

function renderCashRange() {
    const row = $("#cash-range");
    if (!row || row.childElementCount) {
        return;
    }
    [
        ["today", "Hoje"],
        ["week", "7 dias"],
        ["month", "Este mês"],
        ["last-month", "Mês passado"]
    ].forEach(([value, label]) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = `chip${cashState.preset === value ? " is-active" : ""}`;
        button.dataset.preset = value;
        button.textContent = label;
        button.addEventListener("click", async () => {
            cashState.preset = value;
            row.querySelectorAll(".chip").forEach((chip) => {
                chip.classList.toggle("is-active", chip.dataset.preset === value);
            });
            try {
                await refreshCash();
            } catch (error) {
                showFormAlert($("#orders-alert"), error, "Não foi possível atualizar o caixa.");
            }
        });
        row.append(button);
    });
}

function barHeight(amount, max) {
    const value = Number(amount) || 0;
    if (value <= 0 || max <= 0) {
        return "0";
    }
    return `${Math.max(8, (value / max) * 100)}%`;
}

function renderCash(report) {
    const stats = $("#dashboard-stats");
    const chart = $("#cash-chart");
    if (stats) {
        stats.replaceChildren();
        [
            ["is-received", "Entrou no caixa", report.receivedAmount, countLabel(report.receivedCount, "pagamento", "pagamentos")],
            ["is-receivable", "A receber", report.receivableAmount, countLabel(report.receivableCount, "pedido", "pedidos")],
            ["is-undelivered", "Sem entrega", report.undeliveredAmount, countLabel(report.undeliveredCount, "pedido", "pedidos")],
            ["is-cancelled", "Canceladas", report.cancelledAmount, countLabel(report.cancelledCount, "venda", "vendas")]
        ].forEach(([kind, label, amount, meta]) => {
            const card = document.createElement("article");
            card.className = `cash-card ${kind}`;
            const name = document.createElement("span");
            name.className = "cash-label";
            name.textContent = label;
            const strong = document.createElement("strong");
            strong.textContent = formatBRL(amount);
            const note = document.createElement("span");
            note.className = "cash-meta";
            note.textContent = meta;
            card.append(name, strong, note);
            stats.append(card);
        });
    }
    if (!chart) {
        return;
    }
    chart.replaceChildren();
    const days = Array.isArray(report.days) ? report.days : [];
    const max = Math.max(0, ...days.flatMap((day) => [Number(day.receivedAmount) || 0, Number(day.cancelledAmount) || 0]));
    const step = days.length > 16 ? 5 : days.length > 10 ? 2 : 1;
    days.forEach((day, index) => {
        const col = document.createElement("div");
        col.className = "cash-col";
        const bars = document.createElement("div");
        bars.className = "cash-col-bars";
        const received = document.createElement("span");
        received.className = "is-received";
        received.style.height = barHeight(day.receivedAmount, max);
        received.title = `Entrou ${formatBRL(day.receivedAmount)}`;
        const cancelled = document.createElement("span");
        cancelled.className = "is-cancelled";
        cancelled.style.height = barHeight(day.cancelledAmount, max);
        cancelled.title = `Cancelado ${formatBRL(day.cancelledAmount)}`;
        bars.append(received, cancelled);
        const label = document.createElement("small");
        const parts = String(day.date || "").split("-");
        label.textContent = index % step === 0 || index === days.length - 1
            ? `${parts[2] || ""}/${parts[1] || ""}`
            : "";
        col.append(bars, label);
        chart.append(col);
    });
}

function countLabel(count, one, many) {
    const total = Number(count) || 0;
    return `${total} ${total === 1 ? one : many}`;
}

async function refreshCash() {
    renderCashRange();
    const { from, to } = cashBounds(cashState.preset);
    const report = await api(`/orders/cash-report?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
    renderCash(report);
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
        if (freshOrderIds.has(order.id)) {
            row.classList.add("is-fresh");
        }

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

const RECEIPT_TIME_ZONE = "America/Sao_Paulo";

function parseInstant(value) {
    if (!value) {
        return null;
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
}

function formatInBrazil(value, options) {
    const date = value instanceof Date ? value : parseInstant(value);
    if (!date) {
        return "—";
    }
    return date.toLocaleString("pt-BR", {
        timeZone: RECEIPT_TIME_ZONE,
        ...options
    });
}

function formatOrderDate(value) {
    return formatInBrazil(value, {
        day: "2-digit",
        month: "2-digit",
        year: "numeric"
    });
}

function formatOrderClock(value) {
    return formatInBrazil(value, {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit"
    });
}

function formatOrderDateTime(value) {
    const date = formatOrderDate(value);
    const clock = formatOrderClock(value);
    if (date === "—" || clock === "—") {
        return "—";
    }
    return `${date} ${clock}`;
}

function formatCpfDisplay(value) {
    const digits = String(value || "").replace(/\D+/g, "");
    if (digits.length !== 11) {
        return value || "";
    }
    return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6, 9)}-${digits.slice(9)}`;
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

function receiptItemName(item) {
    const full = String(item.productName || "").trim();
    const flavors = String(item.variantName || "").trim();
    if (flavors && full.endsWith(flavors)) {
        const base = full.slice(0, full.length - flavors.length).replace(/[·\s]+$/u, "").trim();
        return { name: base || full, flavors };
    }
    return { name: full || "Item", flavors };
}

function receiptAddressLines(order) {
    return [
        [order.addressStreet, order.addressNumber].filter(Boolean).join(", "),
        order.addressComplement,
        order.addressNeighborhood,
        [order.addressCity, order.addressState].filter(Boolean).join(" - "),
        order.addressZipCode ? `CEP ${order.addressZipCode}` : ""
    ].map((line) => String(line || "").trim()).filter(Boolean);
}

/** Cupom / notinha sem valor fiscal — separação e entrega (NFC-e no caixa/balança). */
function printNonFiscalReceipt(order) {
    const store = establishment || {};
    const isDelivery = String(order.fulfillmentType || "").toUpperCase() === "DELIVERY";
    const addressLines = isDelivery ? receiptAddressLines(order) : [];
    const items = Array.isArray(order.items) ? order.items : [];
    const printedAt = new Date();
    const discount = Number(order.discount || 0);
    const deliveryFee = Number(order.deliveryFee || 0);
    const cpf = formatCpfDisplay(order.customerCpf);
    const paymentMethod = paymentMethodLabel(order.paymentMethod || order.payment?.method);
    const paymentStatus = order.payment?.status ? paymentStatusLabel(order.payment.status) : "";

    const itemsHtml = items.map((item, index) => {
        const qty = formatQuantity(item.quantity, item.productUnit);
        const named = receiptItemName(item);
        const flavorLine = named.flavors
            ? `<div class="flavor">Sabores: ${escapeHtml(named.flavors)}</div>`
            : "";
        return `<tr>
            <td class="idx">${index + 1}</td>
            <td class="name">
              <div>${escapeHtml(named.name)}</div>
              ${flavorLine}
              <div class="detail">${escapeHtml(qty)} × ${escapeHtml(formatBRL(item.unitPrice))}</div>
            </td>
            <td class="money">${escapeHtml(formatBRL(item.subtotal))}</td>
        </tr>`;
    }).join("");

    const html = `<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<title>Notinha ${escapeHtml(order.publicCode)}</title>
<style>
  @page { margin: 4mm; size: 80mm auto; }
  * { box-sizing: border-box; }
  body {
    margin: 0;
    font-family: "Courier New", Courier, monospace;
    font-size: 12px;
    line-height: 1.35;
    color: #111;
    background: #fff;
  }
  .ticket {
    width: 72mm;
    max-width: 100%;
    margin: 0 auto;
    padding: 2mm 1mm 8mm;
  }
  .center { text-align: center; }
  .muted { color: #333; }
  h1 {
    margin: 0 0 2px;
    font-size: 16px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.02em;
  }
  .code {
    margin: 4px 0 0;
    font-size: 20px;
    font-weight: 700;
    letter-spacing: 0.04em;
  }
  .banner {
    margin: 8px 0;
    padding: 5px 0;
    border-top: 1px dashed #111;
    border-bottom: 1px dashed #111;
    font-weight: 700;
    letter-spacing: 0.06em;
    text-transform: uppercase;
  }
  .line { margin: 2px 0; }
  .pair {
    display: flex;
    justify-content: space-between;
    gap: 8px;
    margin: 2px 0;
  }
  .pair span:last-child { text-align: right; font-weight: 700; }
  .sep { border: 0; border-top: 1px dashed #111; margin: 8px 0; }
  table { width: 100%; border-collapse: collapse; }
  th, td { padding: 3px 0; vertical-align: top; }
  th { font-size: 10px; text-align: left; border-bottom: 1px solid #111; }
  td.idx { width: 1.2rem; }
  td.name { word-break: break-word; }
  td.money { width: 4.6rem; text-align: right; white-space: nowrap; font-weight: 700; }
  .flavor, .detail { font-size: 11px; color: #222; }
  .totals .label { text-align: left; }
  .totals .value { text-align: right; font-weight: 700; white-space: nowrap; }
  .totals .grand td { font-size: 15px; padding-top: 4px; }
  .box {
    margin-top: 6px;
    padding: 4px 0;
    border-top: 1px solid #111;
    border-bottom: 1px solid #111;
  }
  .foot {
    margin-top: 10px;
    font-size: 10px;
    text-align: center;
  }
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
    <div class="center code">${escapeHtml(order.publicCode || "—")}</div>
    <div class="pair"><span>Data do pedido</span><span>${escapeHtml(formatOrderDate(order.createdAt))}</span></div>
    <div class="pair"><span>Hora do pedido</span><span>${escapeHtml(formatOrderClock(order.createdAt))}</span></div>
    <div class="line muted">Horário de Brasília</div>
    <div class="pair"><span>Status</span><span>${escapeHtml(statusLabel(order.status))}</span></div>
    <div class="pair"><span>Tipo</span><span>${escapeHtml(fulfillmentLabel(order.fulfillmentType))}</span></div>
    <hr class="sep">
    <div class="line"><strong>Cliente</strong></div>
    <div class="line">${escapeHtml(order.customerName || "—")}</div>
    <div class="line">Tel: ${escapeHtml(formatPhoneDisplay(order.customerPhone) || "—")}</div>
    ${order.customerEmail ? `<div class="line">E-mail: ${escapeHtml(order.customerEmail)}</div>` : ""}
    ${cpf ? `<div class="line">CPF: ${escapeHtml(cpf)}</div>` : ""}
    ${isDelivery
        ? `<div class="line" style="margin-top:6px"><strong>Entrega</strong></div>${
            addressLines.length
                ? addressLines.map((line) => `<div class="line">${escapeHtml(line)}</div>`).join("")
                : `<div class="line">Endereço não informado</div>`
        }`
        : `<div class="banner center">Retirada na loja</div>`}
    ${order.notes ? `<div class="line" style="margin-top:6px"><strong>Obs. do cliente</strong></div><div class="line">${escapeHtml(order.notes)}</div>` : ""}
    <hr class="sep">
    <table>
      <thead>
        <tr>
          <th>#</th>
          <th>Item</th>
          <th style="text-align:right">Total</th>
        </tr>
      </thead>
      <tbody>
        ${itemsHtml || `<tr><td colspan="3">Sem itens</td></tr>`}
      </tbody>
    </table>
    <div class="line muted">${items.length} ${items.length === 1 ? "item" : "itens"}</div>
    <hr class="sep">
    <table class="totals">
      <tr><td class="label">Subtotal</td><td class="value">${escapeHtml(formatBRL(order.subtotal))}</td></tr>
      ${discount > 0 ? `<tr><td class="label">Desconto${order.couponCode ? ` (${escapeHtml(order.couponCode)})` : ""}</td><td class="value">− ${escapeHtml(formatBRL(discount))}</td></tr>` : ""}
      <tr><td class="label">${isDelivery ? "Taxa de entrega" : "Entrega"}</td><td class="value">${escapeHtml(formatBRL(deliveryFee))}</td></tr>
      <tr class="grand"><td class="label">TOTAL</td><td class="value">${escapeHtml(formatBRL(order.total))}</td></tr>
    </table>
    <div class="box">
      <div class="pair"><span>Pagamento</span><span>${escapeHtml(paymentMethod)}</span></div>
      ${paymentStatus ? `<div class="pair"><span>Situação</span><span>${escapeHtml(paymentStatus)}</span></div>` : ""}
    </div>
    <div class="foot">
      Impresso em ${escapeHtml(formatOrderDate(printedAt))} às ${escapeHtml(formatOrderClock(printedAt))}<br>
      Horário de Brasília<br><br>
      SEM VALOR FISCAL<br>
      NFC-e / cupom fiscal emitidos no caixa após pesagem.
    </div>
  </div>
  <script>
    window.addEventListener("load", () => setTimeout(() => window.print(), 250));
  </script>
</body>
</html>`;

    const blob = new Blob([html], { type: "text/html;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const popup = window.open(url, "_blank", "width=420,height=720");
    if (!popup) {
        URL.revokeObjectURL(url);
        window.alert("Permita pop-ups para imprimir a notinha.");
        return;
    }
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

let loadedPaymentSettings = {};

async function loadPaymentSettings() {
    try {
        const settings = await api("/payments/settings");
        loadedPaymentSettings = settings;
        const form = $("#payment-settings-form");
        if (!form) {
            return;
        }
        form.provider.value = settings.provider || "MERCADO_PAGO";
        form.mockMode.checked = !!settings.mockMode;
        form.pixEnabled.checked = !!settings.pixEnabled;
        form.sandbox.checked = !!settings.sandbox;
        updatePaymentProviderHelp(settings);
    } catch (error) {
        showFormAlert($("#payment-settings-alert"), error, "Não foi possível carregar pagamentos.");
    }
}

function updatePaymentProviderHelp(settings = {}) {
    const provider = $("#payment-provider")?.value || "MERCADO_PAGO";
    const help = $("#payment-provider-help");
    const label = $("#payment-token-label");
    const sandboxRow = $("#payment-sandbox-row");
    const hint = $("#payment-token-hint");
    if (sandboxRow) {
        sandboxRow.hidden = provider !== "ASAAS";
    }
    const copy = {
        MERCADO_PAGO: {
            title: "O Pix cai na conta Mercado Pago da loja.",
            field: "Access token do Mercado Pago"
        },
        ASAAS: {
            title: "O Pix cai na conta Asaas da loja.",
            field: "Chave de API da Asaas"
        },
        MANUAL: {
            title: "Sem cobrança online. Dinheiro e pagamento na entrega são confirmados no balcão.",
            field: "Chave da conta"
        },
        MOCK: {
            title: "Simulação. Nenhum Pix real é gerado.",
            field: "Chave da conta"
        }
    }[provider] || {
        title: "O Pix cai na conta que a loja conectar.",
        field: "Chave da conta"
    };
    if (help) {
        help.textContent = copy.title;
    }
    if (label) {
        label.textContent = copy.field;
    }
    if (!hint) {
        return;
    }
    const origin = config.apiBaseUrl || window.location.origin;
    const lines = [];
    if (provider === "MERCADO_PAGO" || provider === "ASAAS") {
        const path = provider === "ASAAS" ? "asaas" : "mercadopago";
        lines.push(`Aviso de pagamento: ${origin}/api/v1/webhooks/${path}`);
    }
    lines.push(settings.accessTokenConfigured
        ? "Chave já configurada. Deixe em branco para manter."
        : "Nenhuma chave configurada.");
    if (settings.webhookSecretConfigured) {
        lines.push("Segredo do aviso já configurado. Deixe em branco para manter.");
    }
    hint.textContent = lines.join(" ");
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
            row.className = "coupon-row";
            const title = document.createElement("strong");
            title.textContent = coupon.code;
            const meta = document.createElement("p");
            meta.className = "muted";
            const valueLabel = coupon.discountType === "PERCENT"
                ? `${formatDiscountNumber(coupon.discountValue)}%`
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

