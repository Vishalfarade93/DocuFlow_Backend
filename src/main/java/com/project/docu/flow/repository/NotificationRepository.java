package com.project.docu.flow.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.project.docu.flow.entity.Notification;

/**
 * Repository for Notification entity operations
 * Handles all database queries related to user notifications
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    /**
     * Find all notifications for a user, sorted by newest first
     */
    List<Notification> findByRecipientUserOrderByCreatedAtDesc(String recipientUser);
    
    /**
     * Find only unread notifications for a user
     */
    List<Notification> findByRecipientUserAndIsReadFalseOrderByCreatedAtDesc(String recipientUser);
    
    /**
     * Count unread notifications for a user
     */
    Long countByRecipientUserAndIsReadFalse(String recipientUser);
    
    /**
     * Find notifications by event type for a user
     */
    List<Notification> findByRecipientUserAndEventTypeOrderByCreatedAtDesc(
            String recipientUser, String eventType);
    
    /**
     * Mark a single notification as read
     */
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.id = :notificationId")
    int markAsRead(@Param("notificationId") Long notificationId, @Param("readAt") LocalDateTime readAt);
    
    /**
     * Mark all notifications as read for a specific user
     */
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.recipientUser = :username AND n.isRead = false")
    int markAllAsReadForUser(@Param("username") String username, @Param("readAt") LocalDateTime readAt);
    
    /**
     * Delete a notification by ID and recipient (security check)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.id = :id AND n.recipientUser = :username")
    int deleteByIdAndRecipientUser(@Param("id") Long id, @Param("username") String username);
    
    /**
     * Delete all notifications for a user
     */
    @Modifying
    @Transactional
    void deleteByRecipientUser(String recipientUser);
    
    /**
     * Delete notifications older than specified date
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoffDate")
    int deleteOldNotifications(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Find notifications by document ID
     */
    List<Notification> findByDocumentIdOrderByCreatedAtDesc(String documentId);
    
    /**
     * Count total notifications for a user
     */
    Long countByRecipientUser(String recipientUser);
}