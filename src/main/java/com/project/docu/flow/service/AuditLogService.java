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
        
        saveAuditLogToDatabase(event);
    }


    private void saveAuditLogToDatabase(DocumentEvent event) {
        logger.debug("Persisting audit log entry for event: {}", event.getEventId());
        
      //save log to the database future scope.
        
    }
}