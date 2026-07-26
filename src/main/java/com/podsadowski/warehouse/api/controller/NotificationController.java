package com.podsadowski.warehouse.api.controller;

import com.podsadowski.warehouse.api.dto.NotificationResponse;
import com.podsadowski.warehouse.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/employees/{employeeId}/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> getNotifications(
            @PathVariable Long employeeId,
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) Integer limit
    ) {
        return notificationService.findForEmployee(employeeId, unreadOnly, limit);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount(@PathVariable Long employeeId) {
        return Map.of("count", notificationService.countUnread(employeeId));
    }

    @PostMapping("/{notificationId}/read")
    public NotificationResponse markAsRead(
            @PathVariable Long employeeId,
            @PathVariable Long notificationId
    ) {
        return notificationService.markAsRead(employeeId, notificationId);
    }
}
