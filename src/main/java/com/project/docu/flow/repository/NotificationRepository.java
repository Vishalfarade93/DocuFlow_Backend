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



@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    

    List<Notification> findByRecipientUserOrderByCreatedAtDesc(String recipientUser);
    
 
    List<Notification> findByRecipientUserAndIsReadFalseOrderByCreatedAtDesc(String recipientUser);
    
  
    Long countByRecipientUserAndIsReadFalse(String recipientUser);
    

    List<Notification> findByRecipientUserAndEventTypeOrderByCreatedAtDesc(
            String recipientUser, String eventType);
    
 
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.id = :notificationId")
    int markAsRead(@Param("notificationId") Long notificationId, @Param("readAt") LocalDateTime readAt);
    
 
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.recipientUser = :username AND n.isRead = false")
    int markAllAsReadForUser(@Param("username") String username, @Param("readAt") LocalDateTime readAt);
    

    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.id = :id AND n.recipientUser = :username")
    int deleteByIdAndRecipientUser(@Param("id") Long id, @Param("username") String username);
    

    @Modifying
    @Transactional
    void deleteByRecipientUser(String recipientUser);
    

    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoffDate")
    int deleteOldNotifications(@Param("cutoffDate") LocalDateTime cutoffDate);

    List<Notification> findByDocumentIdOrderByCreatedAtDesc(String documentId);
    
    Long countByRecipientUser(String recipientUser);
}