package com.project.docu.flow.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import com.project.docu.flow.service.DocumentService;

@RestController
@RequestMapping("/submit")
public class DocumentController {

	private final DocumentService docService;

	public DocumentController(DocumentService docService) {
		this.docService = docService;
	}
	
	// upload document
	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
			@RequestParam(value = "title", required = false) String title,
			@RequestParam(value = "description", required = false) String description,
			@RequestParam(value = "action", required = false) String action, Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthenticated"));
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
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "File save failed", "detail", e.getMessage()));
		}
	}
		
	// all document by owner
	@GetMapping("/my")
	public ResponseEntity<?> listMyDocuments(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthenticated"));
		}
		String owner = authentication.getName();
		List<DocumentMetadata> list = docService.findByOwner(owner);
		return ResponseEntity.ok(list);
	}
	
	
// download document by metadataId
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
	
	
	//getupdaterequest here 
	
	@PutMapping("{metadataId}/update")
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
	        //  Update metadata fields
	        if (updateRequest.getTitle() != null && !updateRequest.getTitle().isBlank()) {
	            metadata.setTitle(updateRequest.getTitle());
	        }

	        if (updateRequest.getDescription() != null) {
	            metadata.setDescription(updateRequest.getDescription());
	        }


	        	
	        System.out.println("Current Status: " + metadata.getStatus());
	        if(metadata.getStatus().equals("CHANGES_REQUESTED")) {
	        	metadata.setStatus("SUBMITTED");
	        	System.out.println("Status was CHANGES_REQUESTED, now set to SUBMITTED");
	        }
	        else if ("submit".equalsIgnoreCase(updateRequest.getAction())) {
	            metadata.setStatus("SUBMITTED");
	                metadata.setSubmittedAt(java.time.LocalDateTime.now());
	                System.out.println("Status was CHANGES_REQUESTED, submittedAt not updated");
	            }else {
	            metadata.setStatus("DRAFT");
	        }

	        metadata.setUpdatedAt(java.time.LocalDateTime.now());


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

	        
	        docService.saveMetadata(metadata);

	        return ResponseEntity.ok(Map.of(
	                "message", " Document updated successfully",
	                "metadata", metadata
	        ));

	    } catch (Exception e) {
	        e.printStackTrace();
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body(Map.of("error", "Failed to update document", "details", e.getMessage()));
	    }
	}
	//delete document by metadataId
	@DeleteMapping("/{metadataId}/delete")
	public  ResponseEntity<?> deleteDocument(@PathVariable("metadataId") Long metadataId, Authentication authentication) {
		
		if (authentication == null || !authentication.isAuthenticated()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthenticated"));
		}
		  	DocumentMetadata metadata = docService.getMetadata(metadataId).
		  			orElseThrow(() -> new RuntimeException("Document not found with id: " + metadataId));
			metadata.getDocumentId();
			docService.deleteDocumentContent(metadata.getDocumentId());
			docService.deleteMetadata(metadataId);
		return ResponseEntity.ok(Map.of("message", "Document deleted successfully"));
	}

}
