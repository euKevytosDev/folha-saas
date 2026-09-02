import { $, on } from "../utils/dom.js";

function bindAuthForm(formId, message) {
    const form = $(formId);
    const alertBox = $(".alert");

    on(form, "submit", (event) => {
        event.preventDefault();
        if (!alertBox) {
            return;
        }
        alertBox.hidden = false;
        alertBox.className = "alert alert-success";
        alertBox.textContent = message;
    });
}

bindAuthForm("#login-form", "O login com JWT entra na próxima fase. A estrutura da tela já está pronta.");
bindAuthForm("#signup-form", "O cadastro do estabelecimento entra na próxima fase.");
bindAuthForm("#recover-form", "A recuperação de senha entra na próxima fase.");
