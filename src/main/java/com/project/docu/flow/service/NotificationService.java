package com.project.docu.flow.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.project.docu.flow.entity.DocumentMetadata;
import com.project.docu.flow.entity.Notification;
import com.project.docu.flow.model.events.DocumentEvent;
import com.project.docu.flow.repository.DocumentMetadataRepository;
import com.project.docu.flow.repository.NotificationRepository;


@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Autowired(required = false)
    private NotificationRepository notificationRepository;
    
    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired(required = false)
    private DocumentMetadataRepository documentMetadataRepository;
    
    @Autowired
    private EmailService emailService;
    
    
    private String resolveEmailForUser(String username) {
        switch (username) {
            case "reviewer":
                return "vishalfarade6844@gmail.com";
            case "approver":
                return "faradevishal@gmail.com";
            case "submitter":
                return "faradevishal@gmail.com";
            default:
                return null;
        }
    }


    //  Event Handlers


    public void notifyDocumentSubmitted(DocumentEvent event) {
        log.info(" Notification: Document '{}' submitted by {}", 
                event.getDocumentTitle(), event.getTriggeredByName());
        
        String message = String.format("New document '%s' submitted by %s and awaiting review", 
                event.getDocumentTitle(), event.getTriggeredByName());
        
        // Notify all
        List<String> reviewers = getReviewersList();
        for (String reviewer : reviewers) {
            sendNotification(event, reviewer, message, "DOCUMENT_SUBMITTED", "/review");
        }
    }


    public void notifyDocumentUnderReview(DocumentEvent event) {
        log.info(" Notification: Document '{}' under review", event.getDocumentTitle());
        
        // Notify approvers
        String approverMessage = String.format("Document '%s' forwarded for your approval", 
                event.getDocumentTitle());
        
        List<String> approvers = getApproversList();
        for (String approver : approvers) {
            sendNotification(event, approver, approverMessage, "DOCUMENT_UNDER_REVIEW", "/approve");
        }
        
        // Notify document owner
        String owner = getDocumentOwner(event.getDocumentId());
        if (owner != null) {
            String ownerMessage = String.format("Your document '%s' has been forwarded to approvers", 
                    event.getDocumentTitle());
            sendNotification(event, owner, ownerMessage, "DOCUMENT_UNDER_REVIEW", "/submit");
        }
    }


    public void notifyDocumentApproved(DocumentEvent event) {
        log.info(" Notification: Document '{}' approved", event.getDocumentTitle());
        
        String owner = getDocumentOwner(event.getDocumentId());
        if (owner != null) {
            String message = String.format("Congratulations! Your document '%s' has been approved by %s", 
                    event.getDocumentTitle(), event.getTriggeredByName());
            sendNotification(event, owner, message, "DOCUMENT_APPROVED", "/submit");
        }
    }


    public void notifyDocumentRejected(DocumentEvent event) {
        log.info(" Notification: Document '{}' rejected", event.getDocumentTitle());
        
        String owner = getDocumentOwner(event.getDocumentId());
        if (owner != null) {
            String reason = (event.getComments() != null && !event.getComments().isEmpty()) 
                    ? event.getComments() : "No specific reason provided";
            
            String message = String.format("Document '%s' was rejected by %s. Reason: %s", 
                    event.getDocumentTitle(), event.getTriggeredByName(), reason);
            
            sendNotification(event, owner, message, "DOCUMENT_REJECTED", "/submit");
        }
    }

    /**
     * Handle revision requested event
     */
    public void notifyRevisionRequested(DocumentEvent event) {
        log.info(" Notification: Changes requested for '{}'", event.getDocumentTitle());
        
        String owner = getDocumentOwner(event.getDocumentId());
        if (owner != null) {
            String comments = (event.getComments() != null && !event.getComments().isEmpty()) 
                    ? event.getComments() : "Please review the document";
            
            String message = String.format("Changes requested for '%s' by %s. Comments: %s", 
                    event.getDocumentTitle(), event.getTriggeredByName(), comments);
            
            sendNotification(event, owner, message, "DOCUMENT_REVISION_REQUESTED", "/submit");
        }
    }


    public void notifyReviewerAssigned(DocumentEvent event) {
        log.info(" Notification: Reviewer assigned to '{}'", event.getDocumentTitle());
        
        String message = String.format("You have been assigned to review document '%s'", 
                event.getDocumentTitle());
        
        sendNotification(event, event.getTriggeredBy(), message, "REVIEWER_ASSIGNED", "/review");
    }

 
    public void notifyApproverAssigned(DocumentEvent event) {
        log.info(" Notification: Approver assigned to '{}'", event.getDocumentTitle());
        
        String message = String.format("You have been assigned to approve document '%s'", 
                event.getDocumentTitle());
        
        sendNotification(event, event.getTriggeredBy(), message, "APPROVER_ASSIGNED", "/approve");
    }

    /**
     * Handle comment added event
     */
    public void notifyCommentAdded(DocumentEvent event) {
        log.info(" Notification: Comment added to '{}'", event.getDocumentTitle());
        
        String owner = getDocumentOwner(event.getDocumentId());
        if (owner != null) {
            String message = String.format("%s added a comment to document '%s'", 
                    event.getTriggeredByName(), event.getDocumentTitle());
            
            sendNotification(event, owner, message, "COMMENT_ADDED", "/submit");
        }
    }

    private void sendNotification(DocumentEvent event, String recipientUser, 
                                  String message, String eventType, String actionUrl) {
        try {
            Notification notification = Notification.builder()
                    .eventId(event.getEventId())
                    .documentId(event.getDocumentId())
                    .documentTitle(event.getDocumentTitle())
                    .eventType(eventType)
                    .message(message)
                    .recipientUser(recipientUser)
                    .triggeredBy(event.getTriggeredBy())
                    .triggeredByName(event.getTriggeredByName())
                    .priority(event.getPriority().name())
                    .isRead(false)
                    .createdAt(LocalDateTime.now())
                    .metadata(buildMetadataJson(event, actionUrl))
                    .build();
            
            // Save to database
            if (notificationRepository != null) {
                notification = notificationRepository.save(notification);
                log.debug(" Notification saved to DB for user: {}", recipientUser);
            } else {
                log.warn(" NotificationRepository not available, notification not persisted");
            }
            
            // Send via WebSocket
            
            if (messagingTemplate != null) {
                messagingTemplate.convertAndSendToUser(
                        recipientUser, 
                        "/queue/notifications", 
                        notification
                );
                log.debug(" WebSocket notification sent to user: {}", recipientUser);
            } else {
                log.warn(" WebSocket not available, real-time notification not sent");
            }
            
        } catch (Exception e) {
            log.error(" Failed to send notification to user: {}", recipientUser, e);
        }
        
     // After saving notification and sending WebSocket...
        try {
            String subject = "DocuFlow: " + eventType.replace('_', ' ') + " - " + event.getDocumentTitle();
            StringBuilder sb = new StringBuilder();
            sb.append(message).append("\n\n");
            sb.append("Document: ").append(event.getDocumentTitle()).append("\n");
            sb.append("By: ").append(event.getTriggeredByName()).append("\n");
            sb.append("Time: ").append(java.time.LocalDateTime.now().toString()).append("\n\n");
            sb.append("Open the application to view details.");

            // resolve recipient's email
            String emailAddress = resolveEmailForUser(recipientUser);  

            if (emailAddress != null && emailService != null) {
                
                emailService.sendEmail(emailAddress, subject, sb.toString());
            } else {
                log.debug("No email address found for user {} — skipping email", recipientUser);
            }
        } catch (Exception e) {
            log.error("Error while sending email notification", e);
        }

        
        
    }

  
    private String buildMetadataJson(DocumentEvent event, String actionUrl) {
        return String.format(
                "{\"actionUrl\":\"%s\",\"documentId\":\"%s\",\"eventType\":\"%s\",\"priority\":\"%s\"}", 
                actionUrl, 
                event.getDocumentId(), 
                event.getEventType().name(),
                event.getPriority().name()
        );
    }

    private String getDocumentOwner(String documentId) {
        try {
            if (documentMetadataRepository == null) {
                log.warn(" DocumentMetadataRepository not available");
                return null;
            }
            
            Long id = Long.parseLong(documentId);
            Optional<DocumentMetadata> metadata = documentMetadataRepository.findById(id);
            
            if (metadata.isPresent()) {
                String owner = metadata.get().getOwner();
                log.debug(" Document owner found: {}", owner);
                return owner;
            } else {
                log.warn(" Document not found with ID: {}", documentId);
            }
            
        } catch (NumberFormatException e) {
            log.error(" Invalid document ID format: {}", documentId);
        } catch (Exception e) {
            log.error(" Error fetching document owner for ID: {}", documentId, e);
        }
        
        return null;
    }

    private List<String> getReviewersList() {
        List<String> reviewers = new ArrayList<>();
        
        // For now, it is hardcoded
       
        reviewers.add("reviewer");
        
        log.debug(" Retrieved {} reviewers", reviewers.size());
        return reviewers;
    }


    private List<String> getApproversList() {
        List<String> approvers = new ArrayList<>();
        
     
        approvers.add("approver");
        
        log.debug(" Retrieved {} approvers", approvers.size());
        return approvers;
    }

    //  Utility Methods 
  
    public List<Notification> getNotificationsForUser(String username) {
        if (notificationRepository != null) {
            return notificationRepository.findByRecipientUserOrderByCreatedAtDesc(username);
        }
        return new ArrayList<>();
    }

    public List<Notification> getUnreadNotifications(String username) {
        if (notificationRepository != null) {
            return notificationRepository.findByRecipientUserAndIsReadFalseOrderByCreatedAtDesc(username);
        }
        return new ArrayList<>();
    }

 
    public Long getUnreadCount(String username) {
        if (notificationRepository != null) {
            return notificationRepository.countByRecipientUserAndIsReadFalse(username);
        }
        return 0L;
    }

 
    public boolean markAsRead(Long notificationId) {
        if (notificationRepository != null) {
            int updated = notificationRepository.markAsRead(notificationId, LocalDateTime.now());
            return updated > 0;
        }
        return false;
    }


    public int markAllAsRead(String username) {
        if (notificationRepository != null) {
            return notificationRepository.markAllAsReadForUser(username, LocalDateTime.now());
        }
        return 0;
    }


    public boolean deleteNotification(Long notificationId, String username) {
        if (notificationRepository != null) {
            int deleted = notificationRepository.deleteByIdAndRecipientUser(notificationId, username);
            return deleted > 0;
        }
        return false;
    }

 
    public void deleteAllNotifications(String username) {
        if (notificationRepository != null) {
            notificationRepository.deleteByRecipientUser(username);
        }
    }
}