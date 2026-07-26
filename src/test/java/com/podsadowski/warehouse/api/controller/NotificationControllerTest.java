package com.podsadowski.warehouse.api.controller;

import com.podsadowski.warehouse.api.dto.NotificationResponse;
import com.podsadowski.warehouse.api.exception.GlobalExceptionHandler;
import com.podsadowski.warehouse.api.exception.ResourceNotFoundException;
import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.NotificationType;
import com.podsadowski.warehouse.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(GlobalExceptionHandler.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void getNotifications_returnsList() throws Exception {
        when(notificationService.findForEmployee(5L, false, null))
                .thenReturn(List.of(sampleNotification(false)));

        mockMvc.perform(get("/api/v1/employees/5/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].type").value("RELOCATION_SCHEDULED"))
                .andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    void getUnreadCount_returnsCount() throws Exception {
        when(notificationService.countUnread(5L)).thenReturn(3L);

        mockMvc.perform(get("/api/v1/employees/5/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    void markAsRead_returnsUpdatedNotification() throws Exception {
        when(notificationService.markAsRead(5L, 1L)).thenReturn(sampleNotification(true));

        mockMvc.perform(post("/api/v1/employees/5/notifications/1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        verify(notificationService).markAsRead(5L, 1L);
    }

    @Test
    void getNotifications_employeeNotFound_returns404() throws Exception {
        when(notificationService.findForEmployee(99L, false, null))
                .thenThrow(new ResourceNotFoundException("Employee not found"));

        mockMvc.perform(get("/api/v1/employees/99/notifications"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Employee not found"));
    }

    private NotificationResponse sampleNotification(boolean read) {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 26, 12, 0);
        return new NotificationResponse(
                1L,
                5L,
                NotificationType.RELOCATION_SCHEDULED,
                "Relocation scheduled: PICKING → PUTAWAY. Finish current work, then walk.",
                10L,
                DepartmentName.PICKING,
                DepartmentName.PUTAWAY,
                createdAt,
                read ? createdAt.plusMinutes(1) : null,
                read
        );
    }
}
