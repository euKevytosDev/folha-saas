import { api, ApiError } from "../api/client.js";
import { $ } from "../utils/dom.js";

const header = $(".site-header");

window.addEventListener("scroll", () => {
    header?.classList.toggle("is-scrolled", window.scrollY > 8);
}, { passive: true });

const form = $("#lead-form");
const alertBox = $("#lead-alert");
const done = $("#lead-done");

form?.addEventListener("submit", async (event) => {
    event.preventDefault();
    alertBox.hidden = true;
    const submit = form.querySelector("button[type='submit']");
    submit.disabled = true;
    const body = {
        name: form.name.value.trim(),
        phone: form.phone.value.trim(),
        businessName: form.businessName.value.trim(),
        brief: form.brief.value.trim(),
        website: form.website.value.trim()
    };
    try {
        const response = await api("/leads", { method: "POST", body });
        form.hidden = true;
        done.hidden = false;
        done.textContent = response?.message || "Recebemos. Vamos chamar você nesse WhatsApp.";
    } catch (error) {
        const fieldMessage = error instanceof ApiError ? error.payload?.errors?.[0]?.message : null;
        alertBox.textContent = fieldMessage
            || (error instanceof ApiError ? error.message : "Não foi possível enviar. Tente de novo.");
        alertBox.hidden = false;
        submit.disabled = false;
    }
});
