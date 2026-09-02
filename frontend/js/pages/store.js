const slug = window.location.pathname.split("/").filter(Boolean)[1] || "loja";
const name = document.getElementById("store-name");
if (name) {
    name.textContent = slug.replaceAll("-", " ");
}
