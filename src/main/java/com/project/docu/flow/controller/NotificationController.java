package com.project.docu.flow.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.docu.flow.entity.Notification;
import com.project.docu.flow.service.NotificationService;
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    @Autowired
    private NotificationService notificationService;

    /**
     * Get all notifications for authenticated user
     */
    @GetMapping
    public ResponseEntity<?> getAllNotifications(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Unauthenticated"));
        }

        try {
            String username = authentication.getName();
            List<Notification> notifications = notificationService.getNotificationsForUser(username);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("notifications", notifications);
            response.put("total", notifications.size());

            log.info(" Retrieved {} notifications for user: {}", notifications.size(), username);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error(" Error fetching notifications", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to fetch notifications"));
        }
    }


    @GetMapping("/unread")
    public ResponseEntity<?> getUnreadNotifications(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Unauthenticated"));
        }

        try {
            String username = authentication.getName();
            List<Notification> unreadNotifications = notificationService.getUnreadNotifications(username);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("notifications", unreadNotifications);
            response.put("count", unreadNotifications.size());

            log.info(" Retrieved {} unread notifications for user: {}", unreadNotifications.size(), username);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error(" Error fetching unread notifications", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to fetch unread notifications"));
        }
    }

 
    @GetMapping("/unread/count")
    public ResponseEntity<?> getUnreadCount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Unauthenticated"));
        }

        try {
            String username = authentication.getName();
            Long count = notificationService.getUnreadCount(username);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", count);

            log.debug(" Unread count for user {}: {}", username, count);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error(" Error fetching unread count", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to fetch unread count"));
        }
    }


    @PutMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(
            @PathVariable Long id,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Unauthenticated"));
        }

        try {
            String username = authentication.getName();
            boolean success = notificationService.markAsRead(id);

            if (success) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Notification marked as read");
                response.put("notificationId", id);

                log.info(" Notification {} marked as read by user: {}", id, username);
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(createErrorResponse("Notification not found"));
            }

        } catch (Exception e) {
            log.error(" Error marking notification as read", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to mark notification as read"));
        }
    }

    
    @PutMapping("/mark-all-read")
    public ResponseEntity<?> markAllAsRead(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Unauthenticated"));
        }

        try {
            String username = authentication.getName();
            int updatedCount = notificationService.markAllAsRead(username);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "All notifications marked as read");
            response.put("updatedCount", updatedCount);

            log.info(" Marked {} notifications as read for user: {}", updatedCount, username);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error(" Error marking all notifications as read", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to mark all as read"));
        }
    }

    /**
     * Delete a specific notification
     * DELETE /api/notifications/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNotification(
            @PathVariable Long id,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Unauthenticated"));
        }

        try {
            String username = authentication.getName();
            boolean success = notificationService.deleteNotification(id, username);

            if (success) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Notification deleted successfully");
                response.put("notificationId", id);

                log.info("Notification {} deleted by user: {}", id, username);
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(createErrorResponse("Notification not found or unauthorized"));
            }

        } catch (Exception e) {
            log.error(" Error deleting notification", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to delete notification"));
        }
    }

   
    @DeleteMapping("/clear-all")
    public ResponseEntity<?> clearAllNotifications(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(createErrorResponse("Unauthenticated"));
        }

        try {
            String username = authentication.getName();
            notificationService.deleteAllNotifications(username);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "All notifications cleared");

            log.info(" All notifications cleared for user: {}", username);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error(" Error clearing notifications", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to clear notifications"));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("status", "healthy");
        response.put("service", "NotificationController");
        response.put("timestamp", java.time.LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }


    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        error.put("timestamp", java.time.LocalDateTime.now().toString());
        return error;
    }
}