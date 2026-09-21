const DEFAULT_MAX_EDGE = 1600;
const DEFAULT_QUALITY = 0.82;

/**
 * Redimensiona e compacta a imagem no aparelho, sem distorcer (mantém proporção).
 * GIFs animados passam direto. Se a compactação falhar, devolve o arquivo original.
 */
export async function compressImageFile(file, options = {}) {
    if (!file || !file.type || !file.type.startsWith("image/")) {
        return file;
    }
    if (file.type === "image/gif") {
        return file;
    }

    const maxEdge = options.maxEdge ?? DEFAULT_MAX_EDGE;
    const quality = options.quality ?? DEFAULT_QUALITY;

    try {
        const bitmap = await loadBitmap(file);
        const width = bitmap.width;
        const height = bitmap.height;
        if (!width || !height) {
            bitmap.close?.();
            return file;
        }
        const scale = Math.min(1, maxEdge / Math.max(width, height));
        const targetW = Math.max(1, Math.round(width * scale));
        const targetH = Math.max(1, Math.round(height * scale));

        const canvas = document.createElement("canvas");
        canvas.width = targetW;
        canvas.height = targetH;
        const ctx = canvas.getContext("2d", { alpha: file.type === "image/png" });
        if (!ctx) {
            bitmap.close?.();
            return file;
        }
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = "high";
        ctx.drawImage(bitmap, 0, 0, targetW, targetH);
        bitmap.close?.();

        const outputType = file.type === "image/png" ? "image/png" : "image/jpeg";
        const blob = await canvasToBlob(canvas, outputType, outputType === "image/jpeg" ? quality : undefined);
        if (!blob) {
            return file;
        }
        if (blob.size >= file.size && scale === 1) {
            return file;
        }
        const name = replaceExtension(file.name || "foto.jpg", outputType === "image/png" ? ".png" : ".jpg");
        return new File([blob], name, { type: outputType, lastModified: Date.now() });
    } catch {
        return file;
    }
}

async function loadBitmap(file) {
    if (typeof createImageBitmap === "function") {
        try {
            return await createImageBitmap(file, { imageOrientation: "from-image" });
        } catch {
            return createImageBitmap(file);
        }
    }
    return loadHtmlImage(file);
}

function loadHtmlImage(file) {
    return new Promise((resolve, reject) => {
        const url = URL.createObjectURL(file);
        const image = new Image();
        image.onload = () => {
            URL.revokeObjectURL(url);
            resolve(image);
        };
        image.onerror = () => {
            URL.revokeObjectURL(url);
            reject(new Error("Não foi possível ler a imagem"));
        };
        image.src = url;
    });
}

function canvasToBlob(canvas, type, quality) {
    return new Promise((resolve) => {
        canvas.toBlob((blob) => resolve(blob), type, quality);
    });
}

function replaceExtension(name, ext) {
    return name.replace(/\.[^.]+$/, "") + ext;
}
