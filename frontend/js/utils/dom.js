export function $(selector, root = document) {
    return root.querySelector(selector);
}

export function on(element, event, handler) {
    element?.addEventListener(event, handler);
}
