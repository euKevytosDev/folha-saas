export function formatBRL(value) {
    return new Intl.NumberFormat("pt-BR", {
        style: "currency",
        currency: "BRL"
    }).format(Number(value || 0));
}

export function formatQuantity(value, unit) {
    const amount = Number(value);
    const decimals = ["KG", "G", "L", "ML"].includes(unit) ? 3 : 0;
    const formatted = amount.toLocaleString("pt-BR", {
        minimumFractionDigits: 0,
        maximumFractionDigits: decimals
    });
    return `${formatted} ${String(unit || "").toLowerCase()}`;
}

export function unitStep(unit) {
    return ["KG", "G", "L", "ML"].includes(unit) ? 0.1 : 1;
}
