package com.podsadowski.warehouse.notification;

import com.podsadowski.warehouse.api.dto.NotificationResponse;
import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.EmployeeNotification;
import com.podsadowski.warehouse.domain.entity.NotificationType;
import com.podsadowski.warehouse.domain.repository.EmployeeNotificationRepository;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private EmployeeNotificationRepository employeeNotificationRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private NotificationService notificationService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                employeeNotificationRepository,
                employeeRepository,
                messagingTemplate,
                clock
        );
    }

    @Test
    void notifyRelocationScheduled_savesAndPushesToWebSocket() {
        when(employeeNotificationRepository.save(any(EmployeeNotification.class))).thenAnswer(invocation -> {
            EmployeeNotification notification = invocation.getArgument(0);
            notification.setId(42L);
            return notification;
        });

        NotificationResponse response = notificationService.notifyRelocationScheduled(
                5L, 10L, DepartmentName.PICKING, DepartmentName.PUTAWAY);

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.type()).isEqualTo(NotificationType.RELOCATION_SCHEDULED);
        assertThat(response.message()).contains("PICKING").contains("PUTAWAY");

        ArgumentCaptor<EmployeeNotification> saved = ArgumentCaptor.forClass(EmployeeNotification.class);
        verify(employeeNotificationRepository).save(saved.capture());
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(LocalDateTime.now(clock));

        ArgumentCaptor<NotificationResponse> pushed = ArgumentCaptor.forClass(NotificationResponse.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/employees/5/notifications"),
                pushed.capture()
        );
        assertThat(pushed.getValue().id()).isEqualTo(42L);
    }
}
