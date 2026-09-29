const IDLE_MS = 15 * 60 * 1000;

/** Quando a pessoa volta depois de 15 minutos sem mexer, chama de novo. */
export function whenIdle(onReturn) {
    let last = Date.now();
    const mark = () => {
        last = Date.now();
    };
    ["pointerdown", "keydown", "touchstart"].forEach((eventName) => {
        window.addEventListener(eventName, mark, { passive: true });
    });
    document.addEventListener("visibilitychange", () => {
        if (document.visibilityState !== "visible") {
            return;
        }
        if (Date.now() - last < IDLE_MS) {
            return;
        }
        mark();
        onReturn();
    });
}
