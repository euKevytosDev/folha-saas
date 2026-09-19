package com.sacolao.establishment;

import com.sacolao.establishment.dto.OpeningInterval;
import com.sacolao.establishment.entity.Establishment;
import com.sacolao.establishment.entity.StoreOpenMode;
import com.sacolao.establishment.service.StoreAvailabilityService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreAvailabilityServiceTest {

    private final StoreAvailabilityService service = new StoreAvailabilityService(JsonMapper.builder().build());

    @Test
    void forcedClosedAlwaysBlocks() {
        Establishment store = store(StoreOpenMode.CLOSED, null);
        assertFalse(service.isAcceptingOrders(store));
    }

    @Test
    void forcedOpenAlwaysAllows() {
        Establishment store = store(StoreOpenMode.OPEN, null);
        assertTrue(service.isAcceptingOrders(store));
    }

    @Test
    void autoRespectsWeekdayHours() {
        String hours = service.serializeHours(Map.of(
                "mon", List.of(new OpeningInterval("09:00", "12:00")),
                "tue", List.of(),
                "wed", List.of(),
                "thu", List.of(),
                "fri", List.of(),
                "sat", List.of(),
                "sun", List.of()
        ));
        Establishment store = store(StoreOpenMode.AUTO, hours);
        ZonedDateTime mondayMorning = ZonedDateTime.of(2026, 9, 21, 10, 0, 0, 0, ZoneId.of("America/Sao_Paulo"));
        ZonedDateTime mondayAfternoon = ZonedDateTime.of(2026, 9, 21, 15, 0, 0, 0, ZoneId.of("America/Sao_Paulo"));
        assertTrue(service.isWithinOpeningHours(store, mondayMorning));
        assertFalse(service.isWithinOpeningHours(store, mondayAfternoon));
    }

    private static Establishment store(StoreOpenMode mode, String hours) {
        Establishment establishment = new Establishment();
        establishment.setActive(true);
        establishment.setStoreOpenMode(mode);
        establishment.setTimezone("America/Sao_Paulo");
        establishment.setOpeningHours(hours);
        return establishment;
    }
}
