package com.podsadowski.warehouse.config;



import com.podsadowski.warehouse.domain.entity.DepartmentName;

import lombok.Getter;

import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;



import java.time.LocalTime;

import java.util.EnumMap;

import java.util.Map;



/**

 * Typed configuration bound from {@code warehouse.*} properties.

 * Java field defaults apply when a key is absent; {@code application-demo.yml} overrides demo-specific values.

 */

@Getter

@Setter

@ConfigurationProperties(prefix = "warehouse")

public class WarehouseProperties {



    private Simulation simulation = new Simulation();

    private Scheduler scheduler = new Scheduler();

    private Performance performance = new Performance();

    private Relocation relocation = new Relocation();

    private Breaks breaks = new Breaks();

    private Seeding seeding = new Seeding();

    private Kafka kafka = new Kafka();



    @Getter

    @Setter

    public static class Simulation {

        private long tickIntervalMs = 10_000;

        private long simulatedMinutesPerTick = 10;

        private long workIntervalSecondsMin = 45;

        private long workIntervalSecondsMax = 90;

        private LocalTime startTime = LocalTime.of(6, 0);

        private long breakPollMs = 1_000;

        private double throughputRandomMin = 0.85;

        private double throughputRandomMax = 1.15;

    }



    @Getter

    @Setter

    public static class Scheduler {

        private long tickIntervalMs = 10_000;

        private long workLogPurgeIntervalMs = 60_000;

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

            // Inbound putaway absorbs former PREP_UNPACKING capacity norms
            defaults.put(DepartmentName.PUTAWAY, 55.0);

            return defaults;

        }

    }



    @Getter

    @Setter

    public static class Relocation {

        private int maxMovesPerTick = 5;

        private double balanceToleranceIph = 10.0;

        private long cooldownMinutes = 60;

        private int maxRelocationsPerDay = 3;

        private long travelMinutes = 5;

        private int orderItemsMin = 5;

        private int orderItemsMax = 15;

        private int topPerformerSampleSize = 3;

    }



    @Getter

    @Setter

    public static class Breaks {

        private ShiftBreakSchedule firstShift = ShiftBreakSchedule.firstShiftDefaults();

        private ShiftBreakSchedule secondShift = ShiftBreakSchedule.secondShiftDefaults();

    }



    @Getter

    @Setter

    public static class ShiftBreakSchedule {

        private LocalTime firstBreakStart;

        private LocalTime firstBreakEnd;

        private LocalTime secondBreakStart;

        private LocalTime secondBreakEnd;



        static ShiftBreakSchedule firstShiftDefaults() {

            ShiftBreakSchedule schedule = new ShiftBreakSchedule();

            schedule.firstBreakStart = LocalTime.of(9, 0);

            schedule.firstBreakEnd = LocalTime.of(9, 30);

            schedule.secondBreakStart = LocalTime.of(12, 0);

            schedule.secondBreakEnd = LocalTime.of(12, 30);

            return schedule;

        }



        static ShiftBreakSchedule secondShiftDefaults() {

            ShiftBreakSchedule schedule = new ShiftBreakSchedule();

            schedule.firstBreakStart = LocalTime.of(9, 30);

            schedule.firstBreakEnd = LocalTime.of(10, 0);

            schedule.secondBreakStart = LocalTime.of(12, 30);

            schedule.secondBreakEnd = LocalTime.of(13, 0);

            return schedule;

        }

    }



    @Getter

    @Setter

    public static class Seeding {

        private int employeeCount = 40;

        private int targetDailyItems = 1_000;

        private double requiredIphMinMultiplier = 1.05;

        private double requiredIphMaxMultiplier = 1.25;

        private double performanceMultiplier = 1.0;

    }



    @Getter

    @Setter

    public static class Kafka {

        private String progressTopic = "warehouse-progress";

        private String consumerGroupId = "warehouse-analytics-group";

        private int partitions = 3;

        private int replicas = 1;

    }

}


