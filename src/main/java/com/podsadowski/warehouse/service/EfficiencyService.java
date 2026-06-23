package com.podsadowski.warehouse.service;

import com.podsadowski.warehouse.entity.DepartmentName;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EfficiencyService {

    private final Map<DepartmentName, Map<Integer, Double>> matrix = Map.of(
            DepartmentName.PICKING, Map.of(
                    1, 15.0,
                    2, 45.0,
                    3, 80.0
            ),
            DepartmentName.PACKING, Map.of(
                    1, 10.0,
                    2, 30.0,
                    3, 50.0
            ),
            DepartmentName.PUTAWAY, Map.of(
                    1, 8.0,
                    2, 20.0,
                    3, 35.0
            ),
            DepartmentName.PREP_UNPACKING, Map.of(
                    1, 20.0,
                    2, 45.0,
                    3, 70.0
            )
    );

    public double getIphContribution(DepartmentName dept, int skillLevel) {
        return matrix.getOrDefault(dept, Map.of())
                .getOrDefault(skillLevel, 5.0);
    }
}
