/** Calcula próxima abertura a partir de openingHours (chaves mon…sun) + timezone. */

const DAY_KEYS = ["sun", "mon", "tue", "wed", "thu", "fri", "sat"];
const DAY_SHORT = {
    sun: "domingo",
    mon: "segunda",
    tue: "terça",
    wed: "quarta",
    thu: "quinta",
    fri: "sexta",
    sat: "sábado"
};

/**
 * @param {Record<string, Array<{open:string, close:string}>>|null} openingHours
 * @param {string|null} timeZone
 * @returns {string|null}
 */
export function nextOpenHint(openingHours, timeZone) {
    if (!openingHours || typeof openingHours !== "object") {
        return null;
    }
    const tz = timeZone || "America/Sao_Paulo";
    const now = zonedParts(new Date(), tz);
    if (!now) {
        return null;
    }

    for (let offset = 0; offset < 8; offset++) {
        const dayIndex = (now.dayIndex + offset) % 7;
        const dayKey = DAY_KEYS[dayIndex];
        const intervals = Array.isArray(openingHours[dayKey]) ? openingHours[dayKey] : [];
        for (const interval of intervals) {
            if (!interval?.open || !interval?.close) {
                continue;
            }
            const openMinutes = toMinutes(interval.open);
            if (openMinutes == null) {
                continue;
            }
            if (offset === 0) {
                if (now.minutes < openMinutes) {
                    return `Abre hoje às ${formatHm(interval.open)}`;
                }
                continue;
            }
            const dayLabel = offset === 1 ? "amanhã" : DAY_SHORT[dayKey];
            return `Abre ${dayLabel} às ${formatHm(interval.open)}`;
        }
    }
    return null;
}

function zonedParts(date, timeZone) {
    try {
        const fmt = new Intl.DateTimeFormat("en-US", {
            timeZone,
            weekday: "short",
            hour: "2-digit",
            minute: "2-digit",
            hour12: false
        });
        const parts = Object.fromEntries(fmt.formatToParts(date).map((p) => [p.type, p.value]));
        const weekdayMap = { Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6 };
        const dayIndex = weekdayMap[parts.weekday];
        const hour = Number(parts.hour === "24" ? "0" : parts.hour);
        const minute = Number(parts.minute);
        if (dayIndex == null || Number.isNaN(hour) || Number.isNaN(minute)) {
            return null;
        }
        return { dayIndex, minutes: hour * 60 + minute };
    } catch {
        return null;
    }
}

function toMinutes(hm) {
    const match = /^(\d{2}):(\d{2})$/.exec(String(hm || ""));
    if (!match) {
        return null;
    }
    return Number(match[1]) * 60 + Number(match[2]);
}

function formatHm(hm) {
    const match = /^(\d{2}):(\d{2})$/.exec(String(hm || ""));
    if (!match) {
        return hm;
    }
    const h = Number(match[1]);
    const m = match[2];
    return m === "00" ? `${h}h` : `${h}h${m}`;
}
