package com.project.docu.flow.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import com.project.docu.flow.service.DocumentService;

@RestController
@RequestMapping("/review")
public class ReviewerController {

    private final DocumentService docService;

    public ReviewerController(DocumentService docService) {
        this.docService = docService;
    }
    @GetMapping("/me")
    public ResponseEntity<?> getDocumentsForApprover(
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthenticated"));
        }
        List<DocumentMetadata> forApprover = docService.findForApprover();
        return ResponseEntity.ok(forApprover);
        
    }
    
    @PutMapping("/{documentId}/forward")
    public ResponseEntity<?> requestChanges(
            @PathVariable long documentId, 
            @RequestParam("comment") String comment,
            @RequestParam("action") String action,
            Authentication authentication) {
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthenticated"));
        }
        
        try {
            DocumentMetadata documentMetadata = docService.getMetadata(documentId).get();
            
            if (action.equalsIgnoreCase("forwarded")) {
                documentMetadata.setStatus("FORWARDED");
            } else if (action.equalsIgnoreCase("changes requested")) {
                documentMetadata.setStatus("CHANGES_REQUESTED");
            } else if (action.equalsIgnoreCase("rejected")) {
                documentMetadata.setStatus("REJECTED");
            }
            
            // Simpler list handling
            List<String> reviewComments = documentMetadata.getReviewComments();
            if (reviewComments == null) {
                reviewComments = new ArrayList<>();
            }
            reviewComments.add(comment);
            documentMetadata.setReviewComments(reviewComments);
            documentMetadata.setReviewedBy(authentication.getName());
         
            docService.saveMetadata(documentMetadata);
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update document", "details", e.getMessage()));
        }
        
        if (action.equalsIgnoreCase("forwarded")) {
			return ResponseEntity.ok(Map.of("message", "Document Forwarded Successfully"));
		} else if (action.equalsIgnoreCase("changes requested")) {
			return ResponseEntity.ok(Map.of("message", "Document Send Back To Submitter Successfully"));
		}
        
        return ResponseEntity.ok(Map.of("message", "Document Rejected Successfully"));
    }
    
    //download
    
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
    
    
}
