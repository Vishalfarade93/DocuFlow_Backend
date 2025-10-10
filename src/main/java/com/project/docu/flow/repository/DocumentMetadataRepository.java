package com.project.docu.flow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.project.docu.flow.entity.DocumentMetadata;

@Repository
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, Long> {

	List<DocumentMetadata> findByOwner(String owner);

	List<DocumentMetadata> findByStatus(String status);

	Optional<DocumentMetadata> findByDocumentId(String documentId);

	List<DocumentMetadata> findByOwnerAndStatus(String owner, String status);
	
	List<DocumentMetadata> findByStatusIn(List<String> statuses);
}