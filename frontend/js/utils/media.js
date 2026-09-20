const UPLOAD_MARKER = "/image/upload/";

/**
 * Entrega a imagem pelo Cloudinary já no tamanho da tela, sem distorcer.
 * URLs que não são do Cloudinary (cole manual) passam direto.
 */
export function optimizedImageUrl(url, { width = 400, height, mode = "limit" } = {}) {
    if (!url || typeof url !== "string") {
        return url;
    }
    const idx = url.indexOf(UPLOAD_MARKER);
    if (idx < 0) {
        return url;
    }
    const rest = url.slice(idx + UPLOAD_MARKER.length);
    if (/^(c_|w_|h_|f_auto|q_auto|t_)/.test(rest)) {
        return url;
    }
    const size = height ? `w_${width},h_${height}` : `w_${width}`;
    const transform = mode === "fill"
        ? `c_fill,g_auto,${size}/f_auto/q_auto`
        : `c_limit,${size}/f_auto/q_auto`;
    return `${url.slice(0, idx + UPLOAD_MARKER.length)}${transform}/${rest}`;
}

export function createThumb(url, name, className = "product-thumb") {
    if (url) {
        const image = document.createElement("img");
        image.className = className;
        image.alt = name || "";
        image.loading = "lazy";
        image.src = optimizedImageUrl(url, { width: 400, height: 400 });
        return image;
    }
    const placeholder = document.createElement("div");
    placeholder.className = `${className} is-empty`;
    placeholder.textContent = "🍃";
    placeholder.setAttribute("aria-hidden", "true");
    return placeholder;
}

export function setPreviewImage(img, url, { width = 400, height = 400 } = {}) {
    if (!img) {
        return;
    }
    if (!url) {
        img.removeAttribute("src");
        return;
    }
    img.src = optimizedImageUrl(url, { width, height });
}
