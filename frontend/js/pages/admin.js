import { api, apiUpload, ApiError } from "../api/client.js";
import { logout, requirePageAuth } from "../auth/api.js";
import { $ } from "../utils/dom.js";
import { formatBRL, formatQuantity } from "../utils/format.js";
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
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.textContent = "Salvando…";
        }
        try {
            const stockRaw = form.stockQuantity.value.trim();
            const hasStock = stockRaw !== "";
            const body = {
                name: control(form, "name")?.value,
                categoryId: form.categoryId.value,
                price: Number(form.price.value),
                unit: form.unit.value,
                imageUrl: form.imageUrl.value || null,
                featured: form.featured.checked,
                stockControlled: hasStock,
                stockQuantity: hasStock ? Number(stockRaw) : null,
                minimumQuantity: form.unit.value === "KG" ? 0.2 : 1,
                ncm: form.ncm?.value?.trim() || null
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
    form.featured.checked = false;
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
    form.featured.checked = !!item.featured;
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
    formCarousels.get(form)?.go(0);
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
        meta.textContent = `${item.categoryName} · ${formatBRL(item.price)} / ${item.unit} · ${item.available ? "à venda" : "oculto"}${stockLabel}`;
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
            flagButton(item.available ? "Pausar" : "Publicar", `/products/${item.id}/availability`, !item.available),
            flagButton(item.featured ? "Tirar destaque" : "Destacar", `/products/${item.id}/featured`, !item.featured)
        );
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

