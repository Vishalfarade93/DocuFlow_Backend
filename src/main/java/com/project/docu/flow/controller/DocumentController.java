package com.project.docu.flow.controller;

import java.io.IOException;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.project.docu.flow.document.DocumentContent;
import com.project.docu.flow.dto.DocumentUpdateRequest;
import com.project.docu.flow.entity.DocumentMetadata;
import com.project.docu.flow.model.events.DocumentEvent;
import com.project.docu.flow.service.DocumentService;
import com.project.docu.flow.service.pulsar.PulsarEventPublisher;

@RestController
@RequestMapping("/submit")
public class DocumentController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentService docService;
    
    @Autowired(required = false)
    private PulsarEventPublisher pulsarEventPublisher;

    public DocumentController(DocumentService docService) {
        this.docService = docService;
    }

    /**
     * Upload document
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "action", required = false) String action, 
            Authentication authentication) {
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthenticated"));
        }
        
        String status = null;
        String owner = authentication.getName();
        
        try {
            if ("draft".equalsIgnoreCase(action)) {
                status = "DRAFT";
            } else if ("submit".equalsIgnoreCase(action)) {
                status = "SUBMITTED";
            }

            DocumentMetadata metadata = docService.uploadDocument(file, title, description, owner, status);
            
            return ResponseEntity.ok(metadata);
            
        } catch (IOException e) {
            logger.error("File upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "File save failed", "detail", e.getMessage()));
        }
    }

    /**
     * Get all documents by owner
     */
    @GetMapping("/my")
    public ResponseEntity<?> listMyDocuments(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthenticated"));
        }
        
        String owner = authentication.getName();
        List<DocumentMetadata> list = docService.findByOwner(owner);
        return ResponseEntity.ok(list);
    }

    /**
     * Download document by metadataId
     */
    @GetMapping("/{metadataId}/download")
    public ResponseEntity<?> download(@PathVariable("metadataId") Long metadataId, 
            Authentication authentication) {
        
        Optional<DocumentMetadata> metaOpt = docService.getMetadata(metadataId);
        if (metaOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Metadata not found"));
        }

        DocumentMetadata metadata = metaOpt.get();
        Optional<DocumentContent> contentOpt = docService.getDocumentContentById(metadata.getDocumentId());
        if (contentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Content not found"));
        }

        DocumentContent content = contentOpt.get();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDispositionFormData("attachment", content.getFilename());
        headers.setContentType(MediaType.parseMediaType(
                content.getContentType() == null ? "application/octet-stream" : content.getContentType()));
        return new ResponseEntity<>(content.getContent(), headers, HttpStatus.OK);
    }

    /**
     * Update document with Pulsar event publishing
     */
    @PutMapping("/{metadataId}/update")
    public ResponseEntity<?> updateDocument(
            @PathVariable Long metadataId,
            @ModelAttribute DocumentUpdateRequest updateRequest,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthenticated"));
        }

        String username = authentication.getName();
        Optional<DocumentMetadata> metaOpt = docService.getMetadata(metadataId);

        if (metaOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Document not found"));
        }

        DocumentMetadata metadata = metaOpt.get();

        if (!metadata.getOwner().equalsIgnoreCase(username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You are not authorized to edit this document"));
        }

        try {
            String previousStatus = metadata.getStatus();
            
            // Update metadata fields
            if (updateRequest.getTitle() != null && !updateRequest.getTitle().isBlank()) {
                metadata.setTitle(updateRequest.getTitle());
            }

            if (updateRequest.getDescription() != null) {
                metadata.setDescription(updateRequest.getDescription());
            }

            // Handle status changes
            if (metadata.getStatus().equals("CHANGES_REQUESTED")) {
                metadata.setStatus("SUBMITTED");
                metadata.setSubmittedAt(LocalDateTime.now());
                logger.info("Status changed from CHANGES_REQUESTED to SUBMITTED");
            } else if ("submit".equalsIgnoreCase(updateRequest.getAction())) {
                metadata.setStatus("SUBMITTED");
                metadata.setSubmittedAt(LocalDateTime.now());
            } else {
                metadata.setStatus("DRAFT");
            }

            metadata.setUpdatedAt(LocalDateTime.now());

            // Handle file update
            if (updateRequest.getFile() != null && !updateRequest.getFile().isEmpty()) {
                DocumentContent newContent = new DocumentContent(
                        updateRequest.getFile().getOriginalFilename(),
                        updateRequest.getFile().getContentType(),
                        updateRequest.getFile().getBytes()
                );

                newContent = docService.saveDocumentContent(newContent);
                metadata.setDocumentId(newContent.getId());
                metadata.setFileType(updateRequest.getFile().getContentType());
                metadata.setFileName(updateRequest.getFile().getOriginalFilename());
                metadata.setFileSize(updateRequest.getFile().getSize());
            }

            // Save metadata
            docService.saveMetadata(metadata);

            // Publish event if status changed
            if (!previousStatus.equals(metadata.getStatus())) {
                publishDocumentUpdateEvent(metadata, previousStatus, metadata.getStatus(), 
                        username, "Document updated");
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Document updated successfully",
                    "metadata", metadata
            ));

        } catch (Exception e) {
            logger.error("Failed to update document", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update document", "details", e.getMessage()));
        }
    }

    /**
     * Delete document by metadataId
     */
    @DeleteMapping("/{metadataId}/delete")
    public ResponseEntity<?> deleteDocument(@PathVariable("metadataId") Long metadataId, 
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthenticated"));
        }

        DocumentMetadata metadata = docService.getMetadata(metadataId)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + metadataId));
        
        String documentId = metadata.getDocumentId();
        String username = authentication.getName();
        
        // Publish deletion event before deleting
        publishDocumentDeleteEvent(metadata, username);
        
        docService.deleteDocumentContent(documentId);
        docService.deleteMetadata(metadataId);
        
        return ResponseEntity.ok(Map.of("message", "Document deleted successfully"));
    }

    /**
     * Helper method to publish document update events
     */
    private void publishDocumentUpdateEvent(DocumentMetadata metadata, String previousStatus, 
                                           String newStatus, String userId, String comments) {
        if (pulsarEventPublisher == null) {
            logger.warn("Pulsar publisher not available, skipping event publish");
            return;
        }

        try {
            Map<String, Object> eventMetadata = new HashMap<>();
            eventMetadata.put("source", "document-controller");
            eventMetadata.put("action", "update");

            DocumentEvent.EventType eventType = determineEventType(newStatus);
            DocumentEvent.WorkflowStatus prevStatus = mapToWorkflowStatus(previousStatus);
            DocumentEvent.WorkflowStatus currStatus = mapToWorkflowStatus(newStatus);

            DocumentEvent event = DocumentEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(eventType)
                    .documentId(String.valueOf(metadata.getId()))
                    .documentTitle(metadata.getTitle())
                    .previousStatus(prevStatus)
                    .newStatus(currStatus)
                    .triggeredBy(userId)
                    .triggeredByName(userId)
                    .timestamp(LocalDateTime.now())
                    .comments(comments)
                    .priority(DocumentEvent.EventPriority.NORMAL)
                    .metadata(eventMetadata)
                    .build();

            pulsarEventPublisher.publishEventAsync(event)
                    .thenAccept(msgId -> logger.info("Document update event published: {}", msgId))
                    .exceptionally(throwable -> {
                        logger.error("Failed to publish update event", throwable);
                        return null;
                    });

        } catch (Exception e) {
            logger.error("Error publishing document update event", e);
        }
    }

    /**
     * Helper method to publish document delete events
     */
    private void publishDocumentDeleteEvent(DocumentMetadata metadata, String userId) {
        if (pulsarEventPublisher == null) {
            return;
        }

        try {
            Map<String, Object> eventMetadata = new HashMap<>();
            eventMetadata.put("source", "document-controller");
            eventMetadata.put("action", "delete");

            DocumentEvent event = DocumentEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(DocumentEvent.EventType.DOCUMENT_DELETED)
                    .documentId(String.valueOf(metadata.getId()))
                    .documentTitle(metadata.getTitle())
                    .previousStatus(mapToWorkflowStatus(metadata.getStatus()))
                    .newStatus(DocumentEvent.WorkflowStatus.DELETED)
                    .triggeredBy(userId)
                    .triggeredByName(userId)
                    .timestamp(LocalDateTime.now())
                    .comments("Document deleted by user")
                    .priority(DocumentEvent.EventPriority.LOW)
                    .metadata(eventMetadata)
                    .build();

            pulsarEventPublisher.publishEventAsync(event);

        } catch (Exception e) {
            logger.error("Error publishing document delete event", e);
        }
    }

    /**
     * Map string status to EventType
     */
    private DocumentEvent.EventType determineEventType(String status) {
        if (status == null) {
            return DocumentEvent.EventType.DOCUMENT_UPDATED;
        }
        
        switch (status.toUpperCase()) {
            case "SUBMITTED":
                return DocumentEvent.EventType.DOCUMENT_SUBMITTED;
            case "FORWARDED":
                return DocumentEvent.EventType.DOCUMENT_UNDER_REVIEW;
            case "APPROVED":
                return DocumentEvent.EventType.DOCUMENT_APPROVED;
            case "REJECTED":
                return DocumentEvent.EventType.DOCUMENT_REJECTED;
            case "CHANGES_REQUESTED":
                return DocumentEvent.EventType.DOCUMENT_REVISION_REQUESTED;
            default:
                return DocumentEvent.EventType.DOCUMENT_UPDATED;
        }
    }

    /**
     * Map string status to WorkflowStatus enum
     */
    private DocumentEvent.WorkflowStatus mapToWorkflowStatus(String status) {
        if (status == null) {
            return DocumentEvent.WorkflowStatus.DRAFT;
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
                return DocumentEvent.WorkflowStatus.DRAFT;
        }
    }
}