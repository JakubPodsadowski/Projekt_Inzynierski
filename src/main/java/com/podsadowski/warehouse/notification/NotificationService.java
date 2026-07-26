package com.podsadowski.warehouse.notification;

import com.podsadowski.warehouse.api.dto.NotificationResponse;
import com.podsadowski.warehouse.api.exception.InvalidRequestException;
import com.podsadowski.warehouse.api.exception.ResourceNotFoundException;
import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.EmployeeNotification;
import com.podsadowski.warehouse.domain.entity.NotificationType;
import com.podsadowski.warehouse.domain.repository.EmployeeNotificationRepository;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final EmployeeNotificationRepository employeeNotificationRepository;
    private final EmployeeRepository employeeRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    @Transactional
    public NotificationResponse notifyRelocationScheduled(
            Long employeeId,
            Long relocationLogId,
            DepartmentName fromDepartment,
            DepartmentName toDepartment
    ) {
        String message = "Relocation scheduled: %s → %s. Finish current work, then walk."
                .formatted(fromDepartment, toDepartment);
        return createAndPublish(
                employeeId,
                NotificationType.RELOCATION_SCHEDULED,
                message,
                relocationLogId,
                fromDepartment,
                toDepartment
        );
    }

    @Transactional
    public NotificationResponse notifyRelocationInTransit(
            Long employeeId,
            Long relocationLogId,
            DepartmentName fromDepartment,
            DepartmentName toDepartment
    ) {
        String message = "Walking to %s (from %s)."
                .formatted(toDepartment, fromDepartment);
        return createAndPublish(
                employeeId,
                NotificationType.RELOCATION_IN_TRANSIT,
                message,
                relocationLogId,
                fromDepartment,
                toDepartment
        );
    }

    @Transactional
    public NotificationResponse notifyRelocationArrived(
            Long employeeId,
            Long relocationLogId,
            DepartmentName fromDepartment,
            DepartmentName toDepartment
    ) {
        String message = "Arrived at %s (from %s). You can start working."
                .formatted(toDepartment, fromDepartment);
        return createAndPublish(
                employeeId,
                NotificationType.RELOCATION_ARRIVED,
                message,
                relocationLogId,
                fromDepartment,
                toDepartment
        );
    }

    private NotificationResponse createAndPublish(
            Long employeeId,
            NotificationType type,
            String message,
            Long relocationLogId,
            DepartmentName fromDepartment,
            DepartmentName toDepartment
    ) {
        EmployeeNotification notification = new EmployeeNotification();
        notification.setEmployeeId(employeeId);
        notification.setType(type);
        notification.setMessage(message);
        notification.setRelocationLogId(relocationLogId);
        notification.setFromDepartment(fromDepartment);
        notification.setToDepartment(toDepartment);
        notification.setCreatedAt(LocalDateTime.now(clock));

        EmployeeNotification saved = employeeNotificationRepository.save(notification);
        NotificationResponse response = toResponse(saved);

        String destination = "/topic/employees/" + employeeId + "/notifications";
        messagingTemplate.convertAndSend(destination, response);
        log.debug("Published notification {} to {}", saved.getId(), destination);

        return response;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> findForEmployee(Long employeeId, boolean unreadOnly, Integer limit) {
        ensureEmployeeExists(employeeId);
        if (unreadOnly) {
            return employeeNotificationRepository
                    .findByEmployeeIdAndReadAtIsNullOrderByCreatedAtDesc(employeeId)
                    .stream()
                    .limit(resolveLimit(limit))
                    .map(NotificationService::toResponse)
                    .toList();
        }
        return employeeNotificationRepository
                .findByEmployeeIdOrderByCreatedAtDesc(employeeId, PageRequest.of(0, resolveLimit(limit)))
                .stream()
                .map(NotificationService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countUnread(Long employeeId) {
        ensureEmployeeExists(employeeId);
        return employeeNotificationRepository.countByEmployeeIdAndReadAtIsNull(employeeId);
    }

    @Transactional
    public NotificationResponse markAsRead(Long employeeId, Long notificationId) {
        ensureEmployeeExists(employeeId);
        EmployeeNotification notification = employeeNotificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (!notification.getEmployeeId().equals(employeeId)) {
            throw new ResourceNotFoundException("Notification not found");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now(clock));
            notification = employeeNotificationRepository.save(notification);
        }
        return toResponse(notification);
    }

    private void ensureEmployeeExists(Long employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee not found");
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return 50;
        }
        if (limit < 1) {
            throw new InvalidRequestException("limit must be at least 1");
        }
        return Math.min(limit, 200);
    }

    public static NotificationResponse toResponse(EmployeeNotification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getEmployeeId(),
                notification.getType(),
                notification.getMessage(),
                notification.getRelocationLogId(),
                notification.getFromDepartment(),
                notification.getToDepartment(),
                notification.getCreatedAt(),
                notification.getReadAt(),
                notification.getReadAt() != null
        );
    }
}
