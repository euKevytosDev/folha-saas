package com.sacolao.establishment.service;

import com.sacolao.establishment.dto.OpeningInterval;
import com.sacolao.establishment.entity.Establishment;
import com.sacolao.establishment.entity.StoreOpenMode;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StoreAvailabilityService {

    public static final List<String> DAY_KEYS = List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");

    private final JsonMapper jsonMapper;

    public StoreAvailabilityService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public boolean isAcceptingOrders(Establishment establishment) {
        if (!establishment.isActive()) {
            return false;
        }
        StoreOpenMode mode = establishment.getStoreOpenMode() == null
                ? StoreOpenMode.AUTO
                : establishment.getStoreOpenMode();
        return switch (mode) {
            case OPEN -> true;
            case CLOSED -> false;
            case AUTO -> isWithinOpeningHours(establishment, ZonedDateTime.now(zoneOf(establishment)));
        };
    }

    public boolean isWithinOpeningHours(Establishment establishment, ZonedDateTime now) {
        Map<String, List<OpeningInterval>> hours = parseHours(establishment.getOpeningHours());
        String dayKey = dayKey(now.getDayOfWeek());
        List<OpeningInterval> intervals = hours.getOrDefault(dayKey, List.of());
        if (intervals.isEmpty()) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        for (OpeningInterval interval : intervals) {
            LocalTime open = LocalTime.parse(interval.open());
            LocalTime close = LocalTime.parse(interval.close());
            if (!time.isBefore(open) && time.isBefore(close)) {
                return true;
            }
            // close == open treated as closed; overnight rare for hortifruti — skip
            if (close.isBefore(open) && (time.compareTo(open) >= 0 || time.isBefore(close))) {
                return true;
            }
        }
        return false;
    }

    public Map<String, List<OpeningInterval>> parseHours(String raw) {
        Map<String, List<OpeningInterval>> result = defaultHours();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        try {
            JsonNode root = jsonMapper.readTree(raw);
            for (String day : DAY_KEYS) {
                JsonNode arr = root.get(day);
                List<OpeningInterval> intervals = new ArrayList<>();
                if (arr != null && arr.isArray()) {
                    for (JsonNode item : arr) {
                        String open = text(item, "open");
                        String close = text(item, "close");
                        if (open != null && close != null) {
                            intervals.add(new OpeningInterval(open, close));
                        }
                    }
                }
                result.put(day, intervals);
            }
        } catch (Exception ignored) {
            // keep defaults
        }
        return result;
    }

    public String serializeHours(Map<String, List<OpeningInterval>> hours) {
        Map<String, List<OpeningInterval>> normalized = defaultHours();
        if (hours != null) {
            for (String day : DAY_KEYS) {
                List<OpeningInterval> intervals = hours.get(day);
                normalized.put(day, intervals == null ? List.of() : List.copyOf(intervals));
            }
        }
        return jsonMapper.writeValueAsString(normalized);
    }

    public static Map<String, List<OpeningInterval>> defaultHours() {
        Map<String, List<OpeningInterval>> map = new LinkedHashMap<>();
        OpeningInterval day = new OpeningInterval("08:00", "18:00");
        for (String key : DAY_KEYS) {
            if ("sun".equals(key)) {
                map.put(key, List.of());
            } else {
                map.put(key, List.of(day));
            }
        }
        return map;
    }

    public static String dayKey(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "mon";
            case TUESDAY -> "tue";
            case WEDNESDAY -> "wed";
            case THURSDAY -> "thu";
            case FRIDAY -> "fri";
            case SATURDAY -> "sat";
            case SUNDAY -> "sun";
        };
    }

    private static ZoneId zoneOf(Establishment establishment) {
        String tz = establishment.getTimezone();
        try {
            return ZoneId.of(tz == null || tz.isBlank() ? "America/Sao_Paulo" : tz.trim());
        } catch (Exception ex) {
            return ZoneId.of("America/Sao_Paulo");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        String text = value.asString();
        return text == null || text.isBlank() ? null : text.trim();
    }
}
