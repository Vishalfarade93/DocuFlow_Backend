package com.project.docu.flow.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.docu.flow.document.DocumentContent;
import com.project.docu.flow.entity.DocumentMetadata;
import com.project.docu.flow.model.events.DocumentEvent;
import com.project.docu.flow.service.DocumentService;
import com.project.docu.flow.service.pulsar.PulsarEventPublisher;

@RestController
@RequestMapping("/approve")
public class ApproverController {

    private static final Logger logger = LoggerFactory.getLogger(ApproverController.class);

    private final DocumentService docService;
    
    @Autowired(required = false)
    private PulsarEventPublisher pulsarEventPublisher;

    public ApproverController(DocumentService docService) {
        this.docService = docService;
    }

    @RequestMapping("/me")
    public ResponseEntity<?> getDocumentForApprover(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthenticated"));
        }

        List<DocumentMetadata> forApprover = docService.findForApprover();
        return ResponseEntity.ok(forApprover);
    }


    
    @PutMapping("/{documentId}/action")
    public ResponseEntity<?> finalPut(
            @PathVariable long documentId,
            @RequestParam("action") String status,
            @RequestParam(value = "comments", required = false) String comments,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthenticated"));
        }

        Optional<DocumentMetadata> metadataOpt = docService.getMetadata(documentId);
        if (metadataOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Document not found"));
        }

        DocumentMetadata documentMetadata = metadataOpt.get();
        String previousStatus = documentMetadata.getStatus();
        String username = authentication.getName();

        if (status.equalsIgnoreCase("approved")) {
            documentMetadata.setStatus("APPROVED");
            documentMetadata.setApprovedBy(username);
            documentMetadata.setApprovedAt(LocalDateTime.now().toString());

            docService.saveMetadata(documentMetadata);

            // Publish approval event
            publishApprovalEvent(documentMetadata, previousStatus, username, comments);

            return ResponseEntity.ok(Map.of(
                    "message", "Document approved successfully",
                    "documentId", documentId,
                    "status", "APPROVED"
            ));

        } else if (status.equalsIgnoreCase("rejected")) {
            documentMetadata.setStatus("APPROVER_REJECTED");
            documentMetadata.setRejectedBy(username);
            documentMetadata.setRejectedAt(LocalDateTime.now());

            docService.saveMetadata(documentMetadata);

            // Publish rejection event
            publishRejectionEvent(documentMetadata, previousStatus, username, comments);

            return ResponseEntity.ok(Map.of(
                    "message", "Document rejected successfully",
                    "documentId", documentId,
                    "status", "APPROVER_REJECTED"
            ));

        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid action. Use 'approved' or 'rejected'"));
        }
    }


    @GetMapping("/{metadataId}/download")
    public ResponseEntity<?> download(@PathVariable("metadataId") Long metadataId, Authentication authentication) {
        Optional<DocumentMetadata> metaOpt = docService.getMetadata(metadataId);
        if (metaOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Metadata not found"));
        }

        DocumentMetadata metadata = metaOpt.get();
        Optional<DocumentContent> contentOpt = docService.getDocumentContentById(metadata.getDocumentId());
        if (contentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Content not found"));
        }

        DocumentContent content = contentOpt.get();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDispositionFormData("attachment", content.getFilename());
        headers.setContentType(MediaType.parseMediaType(
                content.getContentType() == null ? "application/octet-stream" : content.getContentType()));
        return new ResponseEntity<>(content.getContent(), headers, HttpStatus.OK);
    }


    private void publishApprovalEvent(DocumentMetadata metadata, String previousStatus, 
                                     String approver, String comments) {
        if (pulsarEventPublisher == null) {
            logger.warn("Pulsar publisher not available, skipping event publish");
            return;
        }

        try {
            Map<String, Object> eventMetadata = new HashMap<>();
            eventMetadata.put("source", "approver-controller");
            eventMetadata.put("approver", approver);
            eventMetadata.put("approvedAt", metadata.getApprovedAt());

            DocumentEvent event = DocumentEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(DocumentEvent.EventType.DOCUMENT_APPROVED)
                    .documentId(String.valueOf(metadata.getId()))
                    .documentTitle(metadata.getTitle())
                    .previousStatus(mapToWorkflowStatus(previousStatus))
                    .newStatus(DocumentEvent.WorkflowStatus.APPROVED)
                    .triggeredBy(approver)
                    .triggeredByName(approver)
                    .timestamp(LocalDateTime.now())
                    .comments(comments != null ? comments : "Document approved")
                    .priority(DocumentEvent.EventPriority.HIGH)
                    .metadata(eventMetadata)
                    .build();

            pulsarEventPublisher.publishEventAsync(event)
                    .thenAccept(msgId -> logger.info("Approval event published: {}", msgId))
                    .exceptionally(throwable -> {
                        logger.error("Failed to publish approval event", throwable);
                        return null;
                    });

        } catch (Exception e) {
            logger.error("Error publishing approval event", e);
        }
    }

 
    private void publishRejectionEvent(DocumentMetadata metadata, String previousStatus, 
                                      String rejector, String comments) {
        if (pulsarEventPublisher == null) {
            logger.warn("Pulsar publisher not available, skipping event publish");
            return;
        }

        try {
            Map<String, Object> eventMetadata = new HashMap<>();
            eventMetadata.put("source", "approver-controller");
            eventMetadata.put("rejector", rejector);
            eventMetadata.put("rejectedAt", metadata.getRejectedAt());

            DocumentEvent event = DocumentEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(DocumentEvent.EventType.DOCUMENT_REJECTED)
                    .documentId(String.valueOf(metadata.getId()))
                    .documentTitle(metadata.getTitle())
                    .previousStatus(mapToWorkflowStatus(previousStatus))
                    .newStatus(DocumentEvent.WorkflowStatus.REJECTED)
                    .triggeredBy(rejector)
                    .triggeredByName(rejector)
                    .timestamp(LocalDateTime.now())
                    .comments(comments != null ? comments : "Document rejected by approver")
                    .priority(DocumentEvent.EventPriority.HIGH)
                    .metadata(eventMetadata)
                    .build();

            pulsarEventPublisher.publishEventAsync(event)
                    .thenAccept(msgId -> logger.info("Rejection event published: {}", msgId))
                    .exceptionally(throwable -> {
                        logger.error("Failed to publish rejection event", throwable);
                        return null;
                    });

        } catch (Exception e) {
            logger.error("Error publishing rejection event", e);
        }
    }


    private DocumentEvent.WorkflowStatus mapToWorkflowStatus(String status) {
        if (status == null) {
			return DocumentEvent.WorkflowStatus.UNDER_REVIEW;
		}
        
        switch (status.toUpperCase()) {
            case "DRAFT":
                return DocumentEvent.WorkflowStatus.DRAFT;
            case "SUBMITTED":
                return DocumentEvent.WorkflowStatus.SUBMITTED;
            case "FORWARDED":
                return DocumentEvent.WorkflowStatus.UNDER_REVIEW;
            case "APPROVED":
                return DocumentEvent.WorkflowStatus.APPROVED;
            case "REJECTED":
            case "APPROVER_REJECTED":
                return DocumentEvent.WorkflowStatus.REJECTED;
            case "CHANGES_REQUESTED":
                return DocumentEvent.WorkflowStatus.REVISION_REQUESTED;
            default:
                return DocumentEvent.WorkflowStatus.UNDER_REVIEW;
        }
    }
}