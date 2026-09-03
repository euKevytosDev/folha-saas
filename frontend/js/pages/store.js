import { api, ApiError } from "../api/client.js";
import { currentStoreSlug } from "../utils/nav.js";
import { $, on } from "../utils/dom.js";
import { formatBRL, formatQuantity, unitStep } from "../utils/format.js";
import { addToCart, cartCount, clearCart, loadCart, setCartQuantity } from "../store/cart.js";

const slug = currentStoreSlug();
const state = {
    store: null,
    catalog: null,
    categoryId: null,
    query: "",
    quote: null
};

const els = {
    name: $("#store-name"),
    meta: $("#store-meta"),
    logo: $("#store-logo"),
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
    lines: $("#cart-lines")
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
        document.title = `${state.store.name} — Folha`;
        els.name.textContent = state.store.name;
        els.meta.textContent = [state.store.city, state.store.state].filter(Boolean).join(" · ") || "Pedido pelo celular";
        if (state.store.logoUrl) {
            els.logo.src = state.store.logoUrl;
            els.logo.alt = state.store.name;
        } else {
            els.logo.hidden = true;
        }
        renderCategories();
        renderCatalog();
        await refreshQuote();
    } catch (error) {
        showAlert(error instanceof ApiError ? error.message : "Não foi possível abrir a loja.");
    }
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

async function selectCategory(categoryId) {
    state.categoryId = categoryId;
    await reloadCatalog();
}

async function reloadCatalog() {
    const params = new URLSearchParams();
    if (state.categoryId) {
        params.set("categoryId", state.categoryId);
    }
    if (state.query) {
        params.set("q", state.query);
    }
    const suffix = params.toString() ? `?${params}` : "";
    state.catalog = await api(`/store/${encodeURIComponent(slug)}/catalog${suffix}`);
    renderCategories();
    renderCatalog();
}

function renderCatalog() {
    const products = state.catalog.products;
    const featured = state.categoryId || state.query ? [] : state.catalog.featured;
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
    if (product.imageUrl) {
        const image = document.createElement("img");
        image.className = "product-thumb";
        image.alt = product.name;
        image.src = product.imageUrl;
        return image;
    }
    const placeholder = document.createElement("div");
    placeholder.className = "product-thumb is-empty";
    placeholder.textContent = "🍃";
    return placeholder;
}

function body(product) {
    const wrap = document.createElement("div");
    wrap.className = "product-body";
    const title = document.createElement("h3");
    title.textContent = product.name;
    const unit = document.createElement("p");
    unit.className = "muted";
    unit.textContent = `Por ${product.unit.toLowerCase()}`;
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
    const plus = document.createElement("button");
    plus.type = "button";
    plus.textContent = "+";
    minus.addEventListener("click", () => {
        input.value = nextQty(Number(input.value), -unitStep(product.unit), product);
    });
    plus.addEventListener("click", () => {
        input.value = nextQty(Number(input.value), unitStep(product.unit), product);
    });
    qty.append(minus, input, plus);
    const add = document.createElement("button");
    add.className = "btn btn-primary";
    add.type = "button";
    add.textContent = "Adicionar";
    add.addEventListener("click", async () => {
        addToCart(state.store.id, product.id, Number(input.value));
        await refreshQuote();
    });
    actions.append(qty, add);
    return actions;
}

function nextQty(current, delta, product) {
    const min = Number(product.minimumQuantity || unitStep(product.unit));
    const next = Math.max(min, Math.round((current + delta) * 1000) / 1000);
    return next;
}

async function refreshQuote() {
    const items = loadCart(state.store.id);
    if (!items.length) {
        state.quote = null;
        renderCartBar();
        renderCart();
        return;
    }
    state.quote = await api(`/store/${encodeURIComponent(slug)}/cart/quote`, {
        method: "POST",
        body: { items }
    });
    const valid = state.quote.items.filter((line) => !line.issue);
    if (valid.length !== items.length) {
        setCartFromQuote(valid);
    }
    renderCartBar();
    renderCart();
}

function setCartFromQuote(lines) {
    const items = lines.map((line) => ({ productId: line.productId, quantity: line.quantity }));
    localStorage.setItem(`folha.cart.${state.store.id}`, JSON.stringify(items));
}

function renderCartBar() {
    const items = loadCart(state.store.id);
    const hidden = items.length === 0;
    els.cartBar.hidden = hidden;
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
    $("#quote-total").textContent = formatBRL(state.quote?.total || 0);
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
    qty.append(minus, plus);
    const price = document.createElement("strong");
    price.textContent = formatBRL(line.subtotal);
    const remove = document.createElement("button");
    remove.className = "btn btn-ghost";
    remove.type = "button";
    remove.textContent = "Excluir";
    remove.addEventListener("click", async () => {
        setCartQuantity(state.store.id, line.productId, 0);
        await refreshQuote();
    });
    actions.append(qty, price, remove);
    row.append(info, actions);
    return row;
}

async function changeLine(line, delta) {
    setCartQuantity(state.store.id, line.productId, Number(line.quantity) + delta);
    await refreshQuote();
}

function showAlert(message) {
    els.alert.hidden = false;
    els.alert.className = "alert alert-error";
    els.alert.textContent = message;
}

let searchTimer = 0;
on(els.search, "input", () => {
    window.clearTimeout(searchTimer);
    searchTimer = window.setTimeout(async () => {
        state.query = els.search.value.trim();
        await reloadCatalog();
    }, 250);
});

on($("#open-cart"), "click", () => {
    els.drawer.hidden = false;
});
on($("#close-cart"), "click", () => {
    els.drawer.hidden = true;
});
on(els.drawer, "click", (event) => {
    if (event.target === els.drawer) {
        els.drawer.hidden = true;
    }
});
on($("#clear-cart"), "click", async () => {
    clearCart(state.store.id);
    await refreshQuote();
});
