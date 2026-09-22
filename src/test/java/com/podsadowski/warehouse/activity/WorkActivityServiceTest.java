package com.podsadowski.warehouse.activity;

import com.podsadowski.warehouse.activity.WorkActivityService.WorkActivityDraft;
import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.WorkActivity;
import com.podsadowski.warehouse.domain.entity.WorkActivityType;
import com.podsadowski.warehouse.domain.repository.WorkActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkActivityServiceTest {

    @Mock
    private WorkActivityRepository workActivityRepository;

    private WorkActivityService workActivityService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneId.of("UTC"));
        workActivityService = new WorkActivityService(workActivityRepository, clock);
        when(workActivityRepository.save(any(WorkActivity.class))).thenAnswer(invocation -> {
            WorkActivity activity = invocation.getArgument(0);
            activity.setId(99L);
            return activity;
        });
    }

    @Test
    void record_persistsPickItemDraft() {
        WorkActivityDraft draft = WorkActivityDraft.pickItem(
                5L, 30L, "OUT-1", 11L, "SKU-1001", 21L, "A-01-02", 3);

        WorkActivity saved = workActivityService.record(draft);

        ArgumentCaptor<WorkActivity> captor = ArgumentCaptor.forClass(WorkActivity.class);
        verify(workActivityRepository).save(captor.capture());
        WorkActivity activity = captor.getValue();

        assertThat(saved.getId()).isEqualTo(99L);
        assertThat(activity.getType()).isEqualTo(WorkActivityType.PICK_ITEM);
        assertThat(activity.getDepartmentName()).isEqualTo(DepartmentName.PICKING);
        assertThat(activity.getQuantity()).isEqualTo(3);
        assertThat(activity.getLocationCode()).isEqualTo("A-01-02");
        assertThat(activity.getMessage()).contains("SKU-1001").contains("A-01-02");
    }
}
