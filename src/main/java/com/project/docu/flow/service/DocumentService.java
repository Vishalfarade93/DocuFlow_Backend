package com.project.docu.flow.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.project.docu.flow.document.DocumentContent;
import com.project.docu.flow.entity.DocumentMetadata;
import com.project.docu.flow.model.events.DocumentEvent;
import com.project.docu.flow.repository.DocumentContentRepository;
import com.project.docu.flow.repository.DocumentMetadataRepository;
import com.project.docu.flow.service.pulsar.PulsarEventPublisher;

@Service
public class DocumentService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);
    
    private final DocumentContentRepository contentRepo;
    private final DocumentMetadataRepository metadataRepo;
    
    @Autowired(required = false)
    private PulsarEventPublisher pulsarEventPublisher;

    public DocumentService(DocumentContentRepository contentRepo, DocumentMetadataRepository metadataRepo) {
        this.contentRepo = contentRepo;
        this.metadataRepo = metadataRepo;
    }
    
    
    public DocumentMetadata uploadDocument(MultipartFile file, String title, String description, String owner,
                                           String status) throws IOException {
        
        DocumentContent content = new DocumentContent(file.getOriginalFilename(), file.getContentType(),
                file.getBytes());
        DocumentContent savedContent = contentRepo.save(content);
        
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setTitle(title != null ? title : file.getOriginalFilename());
        metadata.setDocumentId(savedContent.getId());
        metadata.setOwner(owner);
        metadata.setStatus(status);
        
        if (status != null && status.equalsIgnoreCase("SUBMITTED")) {
            metadata.setSubmittedAt(LocalDateTime.now());
        }
        
        metadata.setDescription(description);
        metadata.setFileType(file.getContentType());
        metadata.setFileName(file.getOriginalFilename());
        metadata.setFileSize(file.getSize());
        metadata.setCreatedAt(LocalDateTime.now());
        metadata.setUpdatedAt(LocalDateTime.now());
        
        DocumentMetadata savedMetadata = metadataRepo.save(metadata);
        
        // Publish event to Pulsar
        if ("SUBMITTED".equalsIgnoreCase(status)) {
            publishDocumentEvent(
                savedMetadata,
                DocumentEvent.EventType.DOCUMENT_SUBMITTED,
                null,
                DocumentEvent.WorkflowStatus.SUBMITTED,
                owner,
                owner,
                "Document submitted for review"
            );
        } else if ("DRAFT".equalsIgnoreCase(status)) {
            publishDocumentEvent(
                savedMetadata,
                DocumentEvent.EventType.DOCUMENT_CREATED,
                null,
                DocumentEvent.WorkflowStatus.DRAFT,
                owner,
                owner,
                "Document created as draft"
            );
        }
        
        return savedMetadata;
    }

    /**
     * Save document content
     */
    public DocumentContent saveDocumentContent(DocumentContent content) {
        return contentRepo.save(content);
    }

    /**
     * Save metadata with event publishing
     */
    public DocumentMetadata saveMetadata(DocumentMetadata metadata) {
        return metadataRepo.save(metadata);
    }
    
    /**
     * Update document status and publish event
     */
    public DocumentMetadata updateDocumentStatus(Long metadataId, String newStatus, String userId, String userName, String comments) {
        Optional<DocumentMetadata> metaOpt = metadataRepo.findById(metadataId);
        
        if (metaOpt.isEmpty()) {
            throw new RuntimeException("Document not found with id: " + metadataId);
        }
        
        DocumentMetadata metadata = metaOpt.get();
        String previousStatus = metadata.getStatus();
        metadata.setStatus(newStatus);
        metadata.setUpdatedAt(LocalDateTime.now());
        
        DocumentMetadata updated = metadataRepo.save(metadata);
        
        // Publish status change event
        DocumentEvent.EventType eventType = mapStatusToEventType(newStatus);
        publishDocumentEvent(
            updated,
            eventType,
            mapStringToWorkflowStatus(previousStatus),
            mapStringToWorkflowStatus(newStatus),
            userId,
            userName,
            comments
        );
        
        return updated;
    }

    /**
     * Get document content by id
     */
    public Optional<DocumentContent> getDocumentContentById(String mongoId) {
        return contentRepo.findById(mongoId);
    }

    /**
     * Get metadata by id
     */
    public Optional<DocumentMetadata> getMetadata(Long id) {
        return metadataRepo.findById(id);
    }

    /**
     * Find all metadata by owner
     */
    public List<DocumentMetadata> findByOwner(String owner) {
        return metadataRepo.findByOwner(owner);
    }

    /**
     * Delete metadata
     */
    public void deleteMetadata(Long id) {
        metadataRepo.deleteById(id);
    }

    /**
     * Delete document content
     */
    public void deleteDocumentContent(String id) {
        contentRepo.deleteById(id);
    }

    /**
     * Find documents by statuses
     */
    public List<DocumentMetadata> findByStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        return metadataRepo.findByStatusIn(statuses);
    }

    /**
     * Find documents for reviewer
     */
    public List<DocumentMetadata> findForReviewer() {
        return findByStatuses(Arrays.asList("SUBMITTED", "FORWARDED", "CHANGES_REQUESTED", "REJECTED"));
    }

    /**
     * Find documents for approver
     */
    public List<DocumentMetadata> findForApprover() {
        return findByStatuses(Arrays.asList("FORWARDED", "APPROVER_REJECTED", "APPROVED"));
    }

    /**
     * Helper method to publish document events to Pulsar
     */
    private void publishDocumentEvent(
            DocumentMetadata metadata,
            DocumentEvent.EventType eventType,
            DocumentEvent.WorkflowStatus previousStatus,
            DocumentEvent.WorkflowStatus newStatus,
            String userId,
            String userName,
            String comments) {
        
        if (pulsarEventPublisher == null) {
            logger.warn("Pulsar publisher not available, skipping event publish");
            return;
        }
        
        try {
            Map<String, Object> eventMetadata = new HashMap<>();
            eventMetadata.put("documentType", metadata.getFileType());
            eventMetadata.put("fileSize", metadata.getFileSize());
            eventMetadata.put("source", "document-service");
            
            DocumentEvent event = DocumentEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(eventType)
                    .documentId(String.valueOf(metadata.getId()))
                    .documentTitle(metadata.getTitle())
                    .previousStatus(previousStatus)
                    .newStatus(newStatus)
                    .triggeredBy(userId)
                    .triggeredByName(userName)
                    .timestamp(LocalDateTime.now())
                    .comments(comments)
                    .priority(determinePriority(eventType))
                    .metadata(eventMetadata)
                    .build();
            
            pulsarEventPublisher.publishEventAsync(event)
                    .thenAccept(messageId -> 
                        logger.info("Event published successfully: {} for document: {}", eventType, metadata.getId()))
                    .exceptionally(throwable -> {
                        logger.error("Failed to publish event: {} for document: {}", eventType, metadata.getId(), throwable);
                        return null;
                    });
            
        } catch (Exception e) {
            logger.error("Error creating event for document: {}", metadata.getId(), e);
        }
    }

    private DocumentEvent.EventType mapStatusToEventType(String status) {
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
            case "APPROVER_REJECTED":
                return DocumentEvent.EventType.DOCUMENT_REJECTED;
            case "CHANGES_REQUESTED":
                return DocumentEvent.EventType.DOCUMENT_REVISION_REQUESTED;
            default:
                return DocumentEvent.EventType.DOCUMENT_UPDATED;
        }
    }

    /**
     * Map status string to WorkflowStatus enum
     */
    private DocumentEvent.WorkflowStatus mapStringToWorkflowStatus(String status) {
        if (status == null) {
			return null;
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


    private DocumentEvent.EventPriority determinePriority(DocumentEvent.EventType eventType) {
        switch (eventType) {
            case DOCUMENT_APPROVED:
            case DOCUMENT_REJECTED:
                return DocumentEvent.EventPriority.HIGH;
            case DOCUMENT_SUBMITTED:
            case DOCUMENT_REVISION_REQUESTED:
                return DocumentEvent.EventPriority.NORMAL;
            default:
                return DocumentEvent.EventPriority.LOW;
        }
    }
}