package com.project.docu.flow.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
@RequestMapping("/review")
public class ReviewerController {

    private static final Logger logger = LoggerFactory.getLogger(ReviewerController.class);

    private final DocumentService docService;
    
    @Autowired(required = false)
    private PulsarEventPublisher pulsarEventPublisher;

    public ReviewerController(DocumentService docService) {
        this.docService = docService;
    }

//all events 
    @GetMapping("/me")
    public ResponseEntity<?> getDocumentsForReviewer(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthenticated"));
        }

        List<DocumentMetadata> forReviewer = docService.findForReviewer();
        return ResponseEntity.ok(forReviewer);
    }
    

    @PutMapping("/{documentId}/forward")
    public ResponseEntity<?> reviewDocument(
            @PathVariable Long documentId,
            @RequestParam("action") String action,
            @RequestParam(value = "comment", required = false) String comments,
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

        try {
            if ("forward".equalsIgnoreCase(action)) {
                // Forward to approver
                documentMetadata.setStatus("FORWARDED");
                documentMetadata.setReviewedBy(username);
                documentMetadata.setForwardedAt(LocalDateTime.now());
                
                List<String> reviewComments = documentMetadata.getReviewComments();
                if (reviewComments == null) {
                    reviewComments = new ArrayList<>();
                }
                reviewComments.add(comments);
                documentMetadata.setReviewComments(reviewComments);

                docService.saveMetadata(documentMetadata);

                // Publish forward event
                publishForwardEvent(documentMetadata, previousStatus, username, comments);

                return ResponseEntity.ok(Map.of(
                        "message", "Document forwarded to approver successfully",
                        "documentId", documentId,
                        "status", "FORWARDED"
                ));

            } else if ("reject".equalsIgnoreCase(action)) {
                // Reject document
                documentMetadata.setStatus("REJECTED");
                documentMetadata.setReviewedBy(username);
                documentMetadata.setRejectedBy(username);
                documentMetadata.setRejectedAt(LocalDateTime.now());

                List<String> reviewComments = documentMetadata.getReviewComments();
                if (reviewComments == null) {
                    reviewComments = new ArrayList<>();
                }
                reviewComments.add(comments);
                documentMetadata.setReviewComments(reviewComments);

                docService.saveMetadata(documentMetadata);

                // Publish rejection event
                publishReviewerRejectionEvent(documentMetadata, previousStatus, username, comments);

                return ResponseEntity.ok(Map.of(
                        "message", "Document rejected successfully",
                        "documentId", documentId,
                        "status", "REJECTED"
                ));

            } else if ("request_changes".equalsIgnoreCase(action)) {
                // Request changes
                documentMetadata.setStatus("CHANGES_REQUESTED");
                documentMetadata.setReviewedBy(username);

                List<String> reviewComments = documentMetadata.getReviewComments();
                if (reviewComments == null) {
                    reviewComments = new ArrayList<>();
                }
                reviewComments.add(comments);
                documentMetadata.setReviewComments(reviewComments);

                docService.saveMetadata(documentMetadata);

                // Publish revision request event
                publishRevisionRequestEvent(documentMetadata, previousStatus, username, comments);

                return ResponseEntity.ok(Map.of(
                        "message", "Changes requested successfully",
                        "documentId", documentId,
                        "status", "CHANGES_REQUESTED"
                ));

            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid action. Use 'forward', 'reject', or 'request_changes'"));
            }

        } catch (Exception e) {
            logger.error("Error processing review action", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process review action", "details", e.getMessage()));
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


    private void publishForwardEvent(DocumentMetadata metadata, String previousStatus,
                                     String reviewer, String comments) {
        if (pulsarEventPublisher == null) {
            logger.warn("Pulsar publisher not available, skipping event publish");
            return;
        }

        try {
            Map<String, Object> eventMetadata = new HashMap<>();
            eventMetadata.put("source", "reviewer-controller");
            eventMetadata.put("reviewer", reviewer);
            eventMetadata.put("forwardedAt", metadata.getForwardedAt());

            DocumentEvent event = DocumentEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(DocumentEvent.EventType.DOCUMENT_UNDER_REVIEW)
                    .documentId(String.valueOf(metadata.getId()))
                    .documentTitle(metadata.getTitle())
                    .previousStatus(mapToWorkflowStatus(previousStatus))
                    .newStatus(DocumentEvent.WorkflowStatus.UNDER_REVIEW)
                    .triggeredBy(reviewer)
                    .triggeredByName(reviewer)
                    .timestamp(LocalDateTime.now())
                    .comments(comments != null ? comments : "Document forwarded to approver")
                    .priority(DocumentEvent.EventPriority.NORMAL)
                    .metadata(eventMetadata)
                    .build();

            pulsarEventPublisher.publishEventAsync(event)
                    .thenAccept(msgId -> logger.info("Forward event published: {}", msgId))
                    .exceptionally(throwable -> {
                        logger.error("Failed to publish forward event", throwable);
                        return null;
                    });

        } catch (Exception e) {
            logger.error("Error publishing forward event", e);
        }
    }

    private void publishReviewerRejectionEvent(DocumentMetadata metadata, String previousStatus,
                                               String reviewer, String comments) {
        if (pulsarEventPublisher == null) {
            return;
        }

        try {
            Map<String, Object> eventMetadata = new HashMap<>();
            eventMetadata.put("source", "reviewer-controller");
            eventMetadata.put("reviewer", reviewer);

            DocumentEvent event = DocumentEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(DocumentEvent.EventType.DOCUMENT_REJECTED)
                    .documentId(String.valueOf(metadata.getId()))
                    .documentTitle(metadata.getTitle())
                    .previousStatus(mapToWorkflowStatus(previousStatus))
                    .newStatus(DocumentEvent.WorkflowStatus.REJECTED)
                    .triggeredBy(reviewer)
                    .triggeredByName(reviewer)
                    .timestamp(LocalDateTime.now())
                    .comments(comments != null ? comments : "Document rejected by reviewer")
                    .priority(DocumentEvent.EventPriority.HIGH)
                    .metadata(eventMetadata)
                    .build();

            pulsarEventPublisher.publishEventAsync(event);

        } catch (Exception e) {
            logger.error("Error publishing rejection event", e);
        }
    }


    private void publishRevisionRequestEvent(DocumentMetadata metadata, String previousStatus,
                                            String reviewer, String comments) {
        if (pulsarEventPublisher == null) {
            return;
        }

        try {
            Map<String, Object> eventMetadata = new HashMap<>();
            eventMetadata.put("source", "reviewer-controller");
            eventMetadata.put("reviewer", reviewer);

            DocumentEvent event = DocumentEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(DocumentEvent.EventType.DOCUMENT_REVISION_REQUESTED)
                    .documentId(String.valueOf(metadata.getId()))
                    .documentTitle(metadata.getTitle())
                    .previousStatus(mapToWorkflowStatus(previousStatus))
                    .newStatus(DocumentEvent.WorkflowStatus.REVISION_REQUESTED)
                    .triggeredBy(reviewer)
                    .triggeredByName(reviewer)
                    .timestamp(LocalDateTime.now())
                    .comments(comments != null ? comments : "Changes requested for document")
                    .priority(DocumentEvent.EventPriority.NORMAL)
                    .metadata(eventMetadata)
                    .build();

            pulsarEventPublisher.publishEventAsync(event);

        } catch (Exception e) {
            logger.error("Error publishing revision request event", e);
        }
    }

    private DocumentEvent.WorkflowStatus mapToWorkflowStatus(String status) {
        if (status == null) {
			return DocumentEvent.WorkflowStatus.SUBMITTED;
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
                return DocumentEvent.WorkflowStatus.SUBMITTED;
        }
    }
}