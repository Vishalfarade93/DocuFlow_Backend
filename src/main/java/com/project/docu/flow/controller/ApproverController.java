package com.project.docu.flow.controller;

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
@RequestMapping("/approve")
public class ApproverController {
	
	 private final DocumentService docService;

	    public ApproverController(DocumentService docService) {
	 	   this.docService = docService;
	    }
	    
	    //get documents for approver
	@RequestMapping("/me")
	public ResponseEntity<?>getDocumentForApprover(Authentication authentication){
		
		 if (authentication == null || !authentication.isAuthenticated()) {
	            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
	                    .body(Map.of("error", "Unauthenticated"));
	        }
		 	List<DocumentMetadata> forApprover = docService.findForApprover(); 
		 
		return ResponseEntity.ok(forApprover);	
	}
	
	
	//approve, reject
	
	@PutMapping("/{documentId}/action")
	public ResponseEntity<?>finalPut(@PathVariable long documentId,@RequestParam("action") String status,Authentication authentication){
		
		if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthenticated"));
        }
		
		DocumentMetadata documentMetadata = docService.getMetadata(documentId).get();
		
		
		if(status.equalsIgnoreCase("approved")) {
			documentMetadata.setStatus("APPROVED");
			documentMetadata.setApprovedBy(authentication.getName());
			documentMetadata.setApprovedAt(java.time.LocalDateTime.now().toString());
		}
		else if(status.equalsIgnoreCase("rejected")) {
			documentMetadata.setStatus("APPROVER_REJECTED");
			documentMetadata.setRejectedBy(authentication.getName());
			documentMetadata.setRejectedAt(java.time.LocalDateTime.now());
		}
		else {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("error", "something went wrong"));
		}
		
		docService.saveMetadata(documentMetadata);
		
		return ResponseEntity.ok("Approved successfully");
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
}
