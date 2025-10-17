package com.project.docu.flow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.project.docu.flow.model.events.DocumentEvent;

@Service
public class AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

    public void logEvent(DocumentEvent event) {
        logger.info(" AUDIT LOG: EventType={}, DocumentID={}, User={}, Status={} -> {}, Timestamp={}", 
                event.getEventType(), 
                event.getDocumentId(), 
                event.getTriggeredBy(),
                event.getPreviousStatus(),
                event.getNewStatus(),
                event.getTimestamp());
        
        // TODO: Implement actual audit log persistence
        saveAuditLogToDatabase(event);
    }

    /**
     * Save audit log entry to database (PostgreSQL)
     * TODO: Implement actual database persistence
     */
    private void saveAuditLogToDatabase(DocumentEvent event) {
        logger.debug("Persisting audit log entry for event: {}", event.getEventId());
        
        /*
        
        AuditLog auditLog = AuditLog.builder()
            .eventId(event.getEventId())
            .eventType(event.getEventType().name())
            .documentId(event.getDocumentId())
            .documentTitle(event.getDocumentTitle())
            .previousStatus(event.getPreviousStatus() != null ? event.getPreviousStatus().name() : null)
            .newStatus(event.getNewStatus().name())
            .triggeredBy(event.getTriggeredBy())
            .triggeredByName(event.getTriggeredByName())
            .timestamp(event.getTimestamp())
            .comments(event.getComments())
            .metadata(event.getMetadata())
            .build();
        
        auditLogRepository.save(auditLog);
        */
    }
}