package com.project.docu.flow.service.pulsar;

import org.apache.pulsar.client.api.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.project.docu.flow.model.events.DocumentEvent;
import com.project.docu.flow.service.AuditLogService;
import com.project.docu.flow.service.NotificationService;

/**
 * Processes document events received from Pulsar
 * Routes events to appropriate handlers (notifications, audit logs, etc.)
 */
@Service
public class EventProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EventProcessor.class);

    @Autowired(required = false)
    private NotificationService notificationService;

    @Autowired(required = false)
    private AuditLogService auditLogService;

    /**
     * Main event processing method
     * Routes events to appropriate handlers based on event type
     */
    public void processEvent(DocumentEvent event, Message<byte[]> message) {
        logger.info("Processing event: {} for document: {}", event.getEventType(), event.getDocumentId());

        try {
            // Log the event for audit trail
            logAuditEvent(event);

            // Route to appropriate handler based on event type
            switch (event.getEventType()) {
                case DOCUMENT_SUBMITTED:
                    handleDocumentSubmitted(event);
                    break;
                    
                case DOCUMENT_UNDER_REVIEW:
                    handleDocumentUnderReview(event);
                    break;
                    
                case DOCUMENT_APPROVED:
                    handleDocumentApproved(event);
                    break;
                    
                case DOCUMENT_REJECTED:
                    handleDocumentRejected(event);
                    break;
                    
                case DOCUMENT_REVISION_REQUESTED:
                    handleRevisionRequested(event);
                    break;
                    
                case REVIEWER_ASSIGNED:
                    handleReviewerAssigned(event);
                    break;
                    
                case APPROVER_ASSIGNED:
                    handleApproverAssigned(event);
                    break;
                    
                case COMMENT_ADDED:
                    handleCommentAdded(event);
                    break;
                    
                default:
                    handleGenericEvent(event);
                    break;
            }

            logger.info("Successfully processed event {} for document {}", 
                    event.getEventType(), event.getDocumentId());

        } catch (Exception e) {
            logger.error("Error processing event {} for document {}", 
                    event.getEventType(), event.getDocumentId(), e);
            throw new RuntimeException("Event processing failed", e);
        }
    }

    /**
     * Handle document submitted event
     */
    private void handleDocumentSubmitted(DocumentEvent event) {
        logger.info("Handling DOCUMENT_SUBMITTED event for document {}", event.getDocumentId());
        
        // Send notification to reviewers/approvers
        if (notificationService != null) {
            notificationService.notifyDocumentSubmitted(event);
        }
        
        // Additional business logic can be added here
        // e.g., trigger workflow automation, send emails, etc.
    }

    /**
     * Handle document under review event
     */
    private void handleDocumentUnderReview(DocumentEvent event) {
        logger.info("Handling DOCUMENT_UNDER_REVIEW event for document {}", event.getDocumentId());
        
        if (notificationService != null) {
            notificationService.notifyDocumentUnderReview(event);
        }
    }

    /**
     * Handle document approved event
     */
    private void handleDocumentApproved(DocumentEvent event) {
        logger.info("Handling DOCUMENT_APPROVED event for document {}", event.getDocumentId());
        
        if (notificationService != null) {
            notificationService.notifyDocumentApproved(event);
        }
        
        // Additional logic: trigger next stage in workflow, archive document, etc.
    }

    /**
     * Handle document rejected event
     */
    private void handleDocumentRejected(DocumentEvent event) {
        logger.info("Handling DOCUMENT_REJECTED event for document {}", event.getDocumentId());
        
        if (notificationService != null) {
            notificationService.notifyDocumentRejected(event);
        }
    }

    /**
     * Handle revision requested event
     */
    private void handleRevisionRequested(DocumentEvent event) {
        logger.info("Handling DOCUMENT_REVISION_REQUESTED event for document {}", event.getDocumentId());
        
        if (notificationService != null) {
            notificationService.notifyRevisionRequested(event);
        }
    }

    /**
     * Handle reviewer assigned event
     */
    private void handleReviewerAssigned(DocumentEvent event) {
        logger.info("Handling REVIEWER_ASSIGNED event for document {}", event.getDocumentId());
        
        if (notificationService != null) {
            notificationService.notifyReviewerAssigned(event);
        }
    }

    /**
     * Handle approver assigned event
     */
    private void handleApproverAssigned(DocumentEvent event) {
        logger.info("Handling APPROVER_ASSIGNED event for document {}", event.getDocumentId());
        
        if (notificationService != null) {
            notificationService.notifyApproverAssigned(event);
        }
    }

    /**
     * Handle comment added event
     */
    private void handleCommentAdded(DocumentEvent event) {
        logger.info("Handling COMMENT_ADDED event for document {}", event.getDocumentId());
        
        if (notificationService != null) {
            notificationService.notifyCommentAdded(event);
        }
    }

    /**
     * Handle generic events (fallback)
     */
    private void handleGenericEvent(DocumentEvent event) {
        logger.info("Handling generic event {} for document {}", 
                event.getEventType(), event.getDocumentId());
        
        // Log to console or perform default action
        logEventDetails(event);
    }

    /**
     * Log event to audit trail
     */
    private void logAuditEvent(DocumentEvent event) {
        if (auditLogService != null) {
            auditLogService.logEvent(event);
        } else {
            // Fallback: log to console
            logger.info("AUDIT: Event={}, Document={}, User={}, Status={} -> {}", 
                    event.getEventType(), 
                    event.getDocumentId(), 
                    event.getTriggeredBy(), 
                    event.getPreviousStatus(), 
                    event.getNewStatus());
        }
    }

    /**
     * Log detailed event information
     */
    private void logEventDetails(DocumentEvent event) {
        logger.info("Event Details: " +
                "EventID={}, " +
                "EventType={}, " +
                "DocumentID={}, " +
                "DocumentTitle={}, " +
                "PreviousStatus={}, " +
                "NewStatus={}, " +
                "TriggeredBy={}, " +
                "Timestamp={}, " +
                "Priority={}", 
                event.getEventId(),
                event.getEventType(),
                event.getDocumentId(),
                event.getDocumentTitle(),
                event.getPreviousStatus(),
                event.getNewStatus(),
                event.getTriggeredBy(),
                event.getTimestamp(),
                event.getPriority());
    }
}