export function formatBRL(value) {
    return new Intl.NumberFormat("pt-BR", {
        style: "currency",
        currency: "BRL"
    }).format(Number(value || 0));
}

/**
 * Percentual de desconto (preço antigo → preço atual), arredondado.
 * Retorna null se não houver promoção válida.
 */
export function discountPercent(price, compareAtPrice) {
    const current = Number(price);
    const previous = Number(compareAtPrice);
    if (!Number.isFinite(current) || !Number.isFinite(previous) || previous <= 0 || current <= 0) {
        return null;
    }
    if (previous <= current) {
        return null;
    }
    const pct = Math.round((1 - current / previous) * 100);
    return pct > 0 ? pct : null;
}

/**
 * Exibe quantidade de forma clara para o cliente.
 * Em KG, frações viram gramas para evitar confusão (0,1 kg → 100 g).
 */
export function formatQuantity(value, unit) {
    const amount = Number(value);
    if (!Number.isFinite(amount)) {
        return `0 ${String(unit || "").toLowerCase()}`;
    }
    const normalized = String(unit || "").toUpperCase();

    if (normalized === "KG") {
        return formatKg(amount);
    }
    if (normalized === "G") {
        return `${formatNumber(amount, 0)} g`;
    }
    if (normalized === "L") {
        return formatLiters(amount);
    }
    if (normalized === "ML") {
        return `${formatNumber(amount, 0)} ml`;
    }

    const decimals = ["KG", "G", "L", "ML"].includes(normalized) ? 3 : 0;
    return `${formatNumber(amount, decimals)} ${normalized.toLowerCase()}`;
}

/** Texto curto só com a equivalência em gramas/ml (para dicas ao lado do seletor). */
export function quantityHint(value, unit) {
    const amount = Number(value);
    if (!Number.isFinite(amount)) {
        return "";
    }
    const normalized = String(unit || "").toUpperCase();
    if (normalized === "KG") {
        const grams = Math.round(amount * 1000);
        if (grams <= 0) {
            return "";
        }
        if (Number.isInteger(amount) || almostEqual(amount, Math.round(amount))) {
            return amount === 1 ? "1 quilo" : `${formatNumber(amount, 3)} quilos`;
        }
        return `${formatNumber(grams, 0)} gramas`;
    }
    if (normalized === "L") {
        const ml = Math.round(amount * 1000);
        if (ml <= 0) {
            return "";
        }
        if (Number.isInteger(amount) || almostEqual(amount, Math.round(amount))) {
            return amount === 1 ? "1 litro" : `${formatNumber(amount, 3)} litros`;
        }
        return `${formatNumber(ml, 0)} ml`;
    }
    return "";
}

export function unitStep(unit) {
    return ["KG", "G", "L", "ML"].includes(String(unit || "").toUpperCase()) ? 0.1 : 1;
}

function formatKg(amount) {
    const grams = Math.round(amount * 1000);
    if (grams <= 0) {
        return "0 kg";
    }
    // Inteiro em kg: "1 kg", "2 kg"
    if (Number.isInteger(amount) || almostEqual(amount, Math.round(amount))) {
        return `${formatNumber(Math.round(amount), 0)} kg`;
    }
    // Fração: destaque em gramas + kg entre parênteses
    // Ex.: 0,1 → "100 g (0,1 kg)" | 1,5 → "1,5 kg (1.500 g)"
    if (amount < 1) {
        return `${formatNumber(grams, 0)} g (${formatNumber(amount, 3)} kg)`;
    }
    return `${formatNumber(amount, 3)} kg (${formatNumber(grams, 0)} g)`;
}

function formatLiters(amount) {
    const ml = Math.round(amount * 1000);
    if (ml <= 0) {
        return "0 L";
    }
    if (Number.isInteger(amount) || almostEqual(amount, Math.round(amount))) {
        return `${formatNumber(Math.round(amount), 0)} L`;
    }
    if (amount < 1) {
        return `${formatNumber(ml, 0)} ml (${formatNumber(amount, 3)} L)`;
    }
    return `${formatNumber(amount, 3)} L (${formatNumber(ml, 0)} ml)`;
}

function formatNumber(value, maxFractionDigits) {
    return Number(value).toLocaleString("pt-BR", {
        minimumFractionDigits: 0,
        maximumFractionDigits: maxFractionDigits
    });
}

function almostEqual(a, b) {
    return Math.abs(a - b) < 1e-9;
}
