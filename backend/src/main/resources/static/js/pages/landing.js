import { $ } from "../utils/dom.js";
import { getHealth } from "../api/health.js";

const header = $(".site-header");

window.addEventListener("scroll", () => {
    header?.classList.toggle("is-scrolled", window.scrollY > 8);
}, { passive: true });

const status = $("#api-status");
if (status) {
    getHealth()
        .then((data) => {
            status.textContent = data.status === "UP" ? "API online" : "API instável";
        })
        .catch(() => {
            status.textContent = "API offline";
        });
}
