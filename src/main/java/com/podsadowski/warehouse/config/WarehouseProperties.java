package com.podsadowski.warehouse.config;

import com.podsadowski.warehouse.entity.DepartmentName;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "warehouse")
public class WarehouseProperties {

    private Simulation simulation = new Simulation();
    private Scheduler scheduler = new Scheduler();
    private Performance performance = new Performance();
    private Relocation relocation = new Relocation();

    @Getter
    @Setter
    public static class Simulation {
        private long tickIntervalMs = 10_000;
        private long simulatedMinutesPerTick = 10;
        private long workIntervalSecondsMin = 45;
        private long workIntervalSecondsMax = 90;
    }

    @Getter
    @Setter
    public static class Scheduler {
        private long tickIntervalMs = 10_000;
    }

    @Getter
    @Setter
    public static class Performance {
        private int observationWindowDays = 7;
        private double minWorkedHours = 0.1;
        private Map<DepartmentName, Double> defaultIph = defaultIphValues();

        private static Map<DepartmentName, Double> defaultIphValues() {
            Map<DepartmentName, Double> defaults = new EnumMap<>(DepartmentName.class);
            defaults.put(DepartmentName.PICKING, 85.0);
            defaults.put(DepartmentName.PACKING, 55.0);
            defaults.put(DepartmentName.PUTAWAY, 40.0);
            defaults.put(DepartmentName.PREP_UNPACKING, 75.0);
            return defaults;
        }
    }

    @Getter
    @Setter
    public static class Relocation {
        private int maxMovesPerTick = 5;
        private double balanceToleranceIph = 10.0;
        private double balanceImbalanceIph = 25.0;
        private long cooldownMinutes = 60;
        private int maxRelocationsPerDay = 3;
        private long travelMinutes = 5;
        private int orderItemsMin = 5;
        private int orderItemsMax = 15;
    }
}
