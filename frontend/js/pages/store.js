import { api, ApiError } from "../api/client.js";
import { currentStoreSlug, checkoutUrl } from "../utils/nav.js";
import { $, on } from "../utils/dom.js";
import { formatBRL, formatQuantity, quantityHint, unitStep } from "../utils/format.js";
import { nextOpenHint } from "../utils/hours.js";
import { createThumb, optimizedImageUrl } from "../utils/media.js";
import { addToCart, cartCount, clearCart, loadCart, setCartQuantity } from "../store/cart.js";

const slug = currentStoreSlug();
const state = {
    store: null,
    catalog: null,
    /** catálogo completo (uma carga); filtro de categoria/busca é local */
    allProducts: [],
    categoryId: null,
    query: "",
    /** @type {Map<string, any>} */
    productMap: new Map(),
    /** preview local do carrinho (sem bater no servidor a cada clique) */
    quote: null
};

const els = {
    name: $("#store-name"),
    meta: $("#store-meta"),
    logo: $("#store-logo"),
    cover: $("#store-cover"),
    openBadge: $("#store-open-badge"),
    stats: $("#store-stats"),
    closedBanner: $("#store-closed-banner"),
    search: $("#search"),
    categories: $("#category-row"),
    featuredSection: $("#featured-section"),
    featuredGrid: $("#featured-grid"),
    grid: $("#product-grid"),
    empty: $("#empty-state"),
    alert: $("#store-alert"),
    cartBar: $("#cart-bar"),
    cartCount: $("#cart-count"),
    cartSubtotal: $("#cart-subtotal"),
    drawer: $("#cart-drawer"),
    lines: $("#cart-lines"),
    cartBlock: $("#cart-block-reason"),
    goCheckout: $("#go-checkout")
};

if (!slug) {
    showAlert("Loja não encontrada.");
} else {
    await boot();
}

async function boot() {
    try {
        state.catalog = await api(`/store/${encodeURIComponent(slug)}/catalog`);
        state.store = state.catalog.store;
        state.allProducts = state.catalog.products || [];
        rememberProducts(state.allProducts);
        rememberProducts(state.catalog.featured);
        document.title = `${state.store.name} — Folha`;
        renderStorefrontHeader(state.store);
        renderCategories();
        renderCatalog();
        refreshLocalCart();
    } catch (error) {
        showAlert(error instanceof ApiError ? error.message : "Não foi possível abrir a loja.");
    }
}

function renderStorefrontHeader(store) {
    els.name.textContent = store.name;
    els.meta.textContent = [
        store.address,
        [store.city, store.state].filter(Boolean).join("/")
    ].filter(Boolean).join(" · ") || "Pedido pelo celular";

    const open = !!store.acceptingOrders;
    if (els.openBadge) {
        els.openBadge.textContent = open ? "Aberta" : "Fechada";
        els.openBadge.className = `store-status-badge ${open ? "is-open" : "is-closed"}`;
    }
    if (els.closedBanner) {
        els.closedBanner.hidden = open;
        if (!open) {
            const hint = nextOpenHint(store.openingHours, store.timezone);
            els.closedBanner.textContent = hint
                ? `Loja fechada no momento. ${hint}. Você pode olhar o cardápio, mas não finalizar pedido.`
                : "Loja fechada no momento. Você pode olhar o cardápio, mas não finalizar pedido.";
        }
    }
    if (els.cover) {
        if (store.coverUrl) {
            els.cover.style.backgroundImage = `url("${optimizedImageUrl(store.coverUrl, { width: 1600, height: 700, mode: "fill" })}")`;
            els.cover.classList.add("has-image");
        } else {
            els.cover.style.backgroundImage = "";
            els.cover.classList.remove("has-image");
        }
    }
    if (store.logoUrl) {
        els.logo.src = optimizedImageUrl(store.logoUrl, { width: 256, height: 256 });
        els.logo.alt = store.name;
        els.logo.hidden = false;
    } else {
        els.logo.hidden = true;
    }
    if (els.stats) {
        els.stats.replaceChildren();
        const delivery = store.delivery || {};
        const chips = [];
        if (store.ratingCount > 0 && store.ratingAvg != null) {
            chips.push(["Avaliações", `${store.ratingAvg} ★`]);
        }
        if (delivery.pickupEtaMinutes != null) {
            chips.push(["Retirada", `${delivery.pickupEtaMinutes} min`]);
        }
        if (delivery.deliveryEtaMinutes != null) {
            chips.push(["Entrega", `${delivery.deliveryEtaMinutes} min`]);
        }
        if (delivery.minOrderAmount != null) {
            chips.push(["Mínimo", formatBRL(delivery.minOrderAmount)]);
        }
        chips.forEach(([label, value]) => {
            const item = document.createElement("div");
            item.className = "storefront-stat";
            const strong = document.createElement("strong");
            strong.textContent = value;
            const span = document.createElement("span");
            span.className = "muted";
            span.textContent = label;
            item.append(strong, span);
            els.stats.append(item);
        });
    }
}

function rememberProducts(products) {
    (products || []).forEach((product) => {
        if (product?.id) {
            state.productMap.set(product.id, product);
        }
    });
}

function renderCategories() {
    els.categories.replaceChildren();
    const all = chip("Tudo", state.categoryId == null);
    all.addEventListener("click", () => selectCategory(null));
    els.categories.append(all);
    state.catalog.categories.forEach((category) => {
        const button = chip(category.name, state.categoryId === category.id);
        button.addEventListener("click", () => selectCategory(category.id));
        els.categories.append(button);
    });
}

function chip(label, active) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `chip${active ? " is-active" : ""}`;
    button.textContent = label;
    return button;
}

function selectCategory(categoryId) {
    if (state.categoryId === categoryId) {
        return;
    }
    state.categoryId = categoryId;
    renderCategories();
    renderCatalog();
}

function visibleProducts() {
    let products = state.allProducts;
    if (state.categoryId) {
        products = products.filter((product) => product.categoryId === state.categoryId);
    }
    const query = state.query.trim().toLowerCase();
    if (query) {
        products = products.filter((product) => (product.name || "").toLowerCase().includes(query));
    }
    return products;
}

function renderCatalog() {
    const products = visibleProducts();
    const featured = state.categoryId || state.query.trim()
        ? []
        : state.allProducts.filter((product) => product.featured);
    els.featuredSection.hidden = featured.length === 0;
    fillGrid(els.featuredGrid, featured);
    fillGrid(els.grid, products);
    els.empty.hidden = products.length > 0;
}

function fillGrid(root, products) {
    root.replaceChildren();
    products.forEach((product) => root.append(productCard(product)));
}

function productCard(product) {
    const card = document.createElement("article");
    card.className = "product-card";
    card.append(thumb(product), body(product));
    return card;
}

function thumb(product) {
    return createThumb(product.imageUrl, product.name || "Produto");
}

function body(product) {
    const wrap = document.createElement("div");
    wrap.className = "product-body";
    const title = document.createElement("h3");
    title.textContent = product.name;
    const unit = document.createElement("p");
    unit.className = "muted";
    unit.textContent = product.unit === "KG"
        ? "Preço por kg · escolha em gramas ou quilos"
        : `Por ${product.unit.toLowerCase()}`;
    const price = document.createElement("div");
    price.className = "product-price";
    const current = document.createElement("span");
    current.textContent = formatBRL(product.price);
    price.append(current);
    if (product.compareAtPrice) {
        const old = document.createElement("s");
        old.textContent = formatBRL(product.compareAtPrice);
        price.append(old);
    }
    wrap.append(title, unit, price, addControl(product));
    return wrap;
}

function addControl(product) {
    const actions = document.createElement("div");
    actions.className = "product-actions";

    const qtyBlock = document.createElement("div");
    qtyBlock.className = "qty-block";

    const qty = document.createElement("div");
    qty.className = "qty-control";
    const minus = document.createElement("button");
    minus.type = "button";
    minus.textContent = "−";
    const input = document.createElement("input");
    input.type = "number";
    input.min = product.minimumQuantity;
    input.step = unitStep(product.unit);
    input.value = product.minimumQuantity;
    input.setAttribute("aria-label", "Quantidade");
    const plus = document.createElement("button");
    plus.type = "button";
    plus.textContent = "+";

    const hint = document.createElement("p");
    hint.className = "muted qty-hint";
    const syncHint = () => {
        const text = quantityHint(Number(input.value), product.unit);
        hint.textContent = text;
        hint.hidden = !text;
    };
    syncHint();

    minus.addEventListener("click", () => {
        input.value = nextQty(Number(input.value), -unitStep(product.unit), product);
        syncHint();
    });
    plus.addEventListener("click", () => {
        input.value = nextQty(Number(input.value), unitStep(product.unit), product);
        syncHint();
    });
    input.addEventListener("input", syncHint);
    input.addEventListener("change", () => {
        input.value = nextQty(Number(input.value), 0, product);
        syncHint();
    });
    qty.append(minus, input, plus);
    qtyBlock.append(qty, hint);

    const add = document.createElement("button");
    add.className = "btn btn-primary";
    add.type = "button";
    add.textContent = "Adicionar";
    add.addEventListener("click", () => {
        addToCart(state.store.id, product.id, Number(input.value));
        refreshLocalCart();
    });
    actions.append(qtyBlock, add);
    return actions;
}

function nextQty(current, delta, product) {
    const min = Number(product.minimumQuantity || unitStep(product.unit));
    return Math.max(min, Math.round((current + delta) * 1000) / 1000);
}

/** Monta totais no cliente a partir do catálogo já carregado + localStorage. */
function refreshLocalCart() {
    const items = loadCart(state.store.id);
    if (!items.length) {
        state.quote = null;
        renderCartBar();
        renderCart();
        return;
    }
    const lines = [];
    let subtotal = 0;
    items.forEach((item) => {
        const product = state.productMap.get(item.productId);
        const quantity = Number(item.quantity) || 0;
        if (!product) {
            lines.push({
                productId: item.productId,
                name: "Produto indisponível",
                unit: null,
                quantity,
                unitPrice: 0,
                subtotal: 0,
                issue: "Atualize o carrinho no checkout"
            });
            return;
        }
        const unitPrice = Number(product.price) || 0;
        const lineTotal = Math.round(unitPrice * quantity * 100) / 100;
        subtotal += lineTotal;
        lines.push({
            productId: product.id,
            name: product.name,
            imageUrl: product.imageUrl,
            unit: product.unit,
            quantity,
            unitPrice,
            subtotal: lineTotal,
            minimumQuantity: Number(product.minimumQuantity) || unitStep(product.unit),
            issue: null
        });
    });
    subtotal = Math.round(subtotal * 100) / 100;
    state.quote = {
        items: lines,
        subtotal,
        discount: 0,
        deliveryFee: 0,
        total: subtotal,
        local: true
    };
    renderCartBar();
    renderCart();
}

function renderCartBar() {
    const items = loadCart(state.store.id);
    const hidden = items.length === 0;
    els.cartBar.hidden = hidden;
    syncCheckoutLink();
    if (hidden) {
        return;
    }
    const count = cartCount(items);
    els.cartCount.textContent = count === 1 ? "1 item" : `${formatCount(count)} itens`;
    els.cartSubtotal.textContent = formatBRL(state.quote?.subtotal || 0);
}

function formatCount(value) {
    return Number(value).toLocaleString("pt-BR", { maximumFractionDigits: 3 });
}

function renderCart() {
    els.lines.replaceChildren();
    const lines = state.quote?.items || [];
    if (!lines.length) {
        const empty = document.createElement("p");
        empty.className = "muted";
        empty.textContent = "Seu carrinho está vazio.";
        els.lines.append(empty);
    } else {
        lines.forEach((line) => els.lines.append(cartLine(line)));
    }
    $("#quote-subtotal").textContent = formatBRL(state.quote?.subtotal || 0);
    $("#quote-discount").textContent = formatBRL(state.quote?.discount || 0);
    const fee = Number(state.store?.delivery?.fixedFee || 0);
    const deliveryEl = $("#quote-delivery");
    if (deliveryEl) {
        deliveryEl.textContent = fee > 0 ? `a partir de ${formatBRL(fee)}` : "Grátis / a calcular";
    }
    $("#quote-total").textContent = formatBRL(state.quote?.total || 0);
    const note = $("#cart-note");
    if (note) {
        note.textContent = "Totais estimados no aparelho. O preço final é confirmado pelo servidor no checkout.";
    }
    syncCheckoutLink();
}

function cartLine(line) {
    const row = document.createElement("div");
    row.className = "cart-line";
    row.append(thumb(line));
    const info = document.createElement("div");
    const name = document.createElement("strong");
    name.textContent = line.name || "Produto";
    const meta = document.createElement("p");
    meta.className = "muted";
    meta.textContent = line.issue || `${formatQuantity(line.quantity, line.unit)} · ${formatBRL(line.unitPrice)}`;
    info.append(name, meta);
    const actions = document.createElement("div");
    const qty = document.createElement("div");
    qty.className = "qty-control";
    const minus = document.createElement("button");
    minus.type = "button";
    minus.textContent = "−";
    const plus = document.createElement("button");
    plus.type = "button";
    plus.textContent = "+";
    minus.addEventListener("click", () => changeLine(line, -unitStep(line.unit)));
    plus.addEventListener("click", () => changeLine(line, unitStep(line.unit)));
    const minQty = Number(line.minimumQuantity) || unitStep(line.unit);
    if (Number(line.quantity) <= minQty + 1e-9) {
        minus.disabled = true;
        minus.setAttribute("aria-disabled", "true");
        minus.title = "Quantidade mínima do produto";
    }
    qty.append(minus, plus);
    const price = document.createElement("strong");
    price.textContent = formatBRL(line.subtotal);
    const remove = document.createElement("button");
    remove.className = "btn btn-ghost";
    remove.type = "button";
    remove.textContent = "Excluir";
    remove.addEventListener("click", () => {
        setCartQuantity(state.store.id, line.productId, 0);
        refreshLocalCart();
    });
    actions.append(qty, price, remove);
    row.append(info, actions);
    return row;
}

function changeLine(line, delta) {
    setCartQuantity(state.store.id, line.productId, Number(line.quantity) + delta);
    refreshLocalCart();
}

function showAlert(message) {
    els.alert.hidden = false;
    els.alert.className = "alert alert-error";
    els.alert.textContent = message;
}

function syncCheckoutLink() {
    const link = $("#go-checkout");
    if (!link || !state.store) {
        return;
    }
    const items = loadCart(state.store.id);
    const open = state.store.acceptingOrders !== false;
    const minOrder = Number(state.store.delivery?.minOrderAmount || 0);
    const subtotal = Number(state.quote?.subtotal || 0);
    const belowMin = minOrder > 0 && subtotal < minOrder;
    let reason = "";
    if (!open) {
        reason = "Loja fechada — não é possível finalizar o pedido agora.";
    } else if (belowMin) {
        reason = `Pedido mínimo de ${formatBRL(minOrder)}.`;
    }
    const enabled = items.length > 0 && open && !belowMin;
    if (els.cartBlock) {
        els.cartBlock.hidden = !reason;
        els.cartBlock.textContent = reason;
    }
    link.href = enabled ? checkoutUrl(slug) : "#";
    link.setAttribute("aria-disabled", enabled ? "false" : "true");
    link.style.pointerEvents = enabled ? "" : "none";
    link.style.opacity = enabled ? "" : "0.6";
    link.textContent = !open ? "Loja fechada" : belowMin ? "Abaixo do mínimo" : "Finalizar pedido";
}

let searchTimer = 0;
on(els.search, "input", () => {
    window.clearTimeout(searchTimer);
    searchTimer = window.setTimeout(() => {
        state.query = els.search.value.trim();
        renderCatalog();
    }, 120);
});

on($("#open-cart"), "click", () => {
    els.drawer.hidden = false;
    refreshLocalCart();
});
on($("#close-cart"), "click", () => {
    els.drawer.hidden = true;
});
on(els.drawer, "click", (event) => {
    if (event.target === els.drawer) {
        els.drawer.hidden = true;
    }
});
on($("#clear-cart"), "click", () => {
    clearCart(state.store.id);
    refreshLocalCart();
});

