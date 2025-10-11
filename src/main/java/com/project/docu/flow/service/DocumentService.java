package com.project.docu.flow.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.project.docu.flow.document.DocumentContent;
import com.project.docu.flow.entity.DocumentMetadata;
import com.project.docu.flow.repository.DocumentContentRepository;
import com.project.docu.flow.repository.DocumentMetadataRepository;

@Service
public class DocumentService {

    private final DocumentContentRepository contentRepo;
    private final DocumentMetadataRepository metadataRepo;

    public DocumentService(DocumentContentRepository contentRepo, DocumentMetadataRepository metadataRepo) {
        this.contentRepo = contentRepo;
        this.metadataRepo = metadataRepo;
    }

    //Upload new document 
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
        if(status != null && status.equals("SUBMITTED")) {
			metadata.setSubmittedAt(LocalDateTime.now());
		}
        metadata.setDescription(description);
        metadata.setFileType(file.getContentType());
        metadata.setFileName(file.getOriginalFilename());
        metadata.setFileSize(file.getSize());
        metadata.setCreatedAt(LocalDateTime.now());
        metadata.setUpdatedAt(LocalDateTime.now());

        return metadataRepo.save(metadata);
    }

    // save 
    public DocumentContent saveDocumentContent(DocumentContent content) {
        return contentRepo.save(content);
    }
// save metadata
    public DocumentMetadata saveMetadata(DocumentMetadata metadata) {
        return metadataRepo.save(metadata);
    }


    // get document content by id
    public Optional<DocumentContent> getDocumentContentById(String mongoId) {
        return contentRepo.findById(mongoId);
    }

    	// get metadata by id
    public Optional<DocumentMetadata> getMetadata(Long id) {
        return metadataRepo.findById(id);
    }
	// find all metadata
    public List<DocumentMetadata> findByOwner(String owner) {
        return metadataRepo.findByOwner(owner);
    }
    
    //delete metadata
    public void deleteMetadata(Long id) {
		metadataRepo.deleteById(id);
	}
    public void deleteDocumentContent(String id) {
    			contentRepo.deleteById(id);
    }
    
    
    
    // for reviewer
    
    public List<DocumentMetadata> findByStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        return metadataRepo.findByStatusIn(statuses);
    }
    
    public List<DocumentMetadata> findForReviewer() {
        return findByStatuses(Arrays.asList("SUBMITTED", "FORWARDED", "CHANGES_REQUESTED","REJECTED"));
    }
    
    //for approver
    public List<DocumentMetadata> findForApprover() {
        return findByStatuses(Arrays.asList( "FORWARDED","APPROVER_REJECTED","APPROVED"));
    }
    

}
