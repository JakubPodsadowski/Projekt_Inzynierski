package com.podsadowski.warehouse.api.service;

import com.podsadowski.warehouse.api.dto.BalanceResponse;
import com.podsadowski.warehouse.api.dto.ForceRelocationRequest;
import com.podsadowski.warehouse.api.dto.RelocationLogResponse;
import com.podsadowski.warehouse.api.mapper.WorkforceMapper;
import com.podsadowski.warehouse.domain.entity.RelocationLog;
import com.podsadowski.warehouse.relocation.RelocationEngine;
import com.podsadowski.warehouse.relocation.RelocationFulfillmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RelocationWriteService {

    private final RelocationEngine relocationEngine;
    private final RelocationFulfillmentService relocationFulfillmentService;
    private final WorkforceMapper workforceMapper;

    public RelocationLogResponse forceSchedule(ForceRelocationRequest request) {
        RelocationLog relocationLog = relocationEngine.forceSchedule(
                request.employeeId(),
                request.toDepartmentId()
        );
        return workforceMapper.toRelocationLogResponse(relocationLog);
    }

    public RelocationLogResponse cancel(Long relocationLogId) {
        RelocationLog relocationLog = relocationFulfillmentService.cancelOpenRelocation(relocationLogId);
        return workforceMapper.toRelocationLogResponse(relocationLog);
    }

    public BalanceResponse balance() {
        int scheduled = relocationEngine.checkAndBalanceWorkforce();
        return new BalanceResponse(scheduled);
    }
}
