package com.project.docu.flow.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "document_metadata")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMetadata {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false)
	private String title;
	@Column(nullable = false)
	private String documentId; // Reference to MongoDB document
	@Column(nullable = false)
	private String owner; // Username from LDAP
	@Column(nullable = false)
	private String status; // DRAFT, SUBMITTED, CHANGES_REQUESTED,,FORWORDED APPROVED, REJECTED
	private String description;
	@Column(name = "file_type")
	private String fileType;
	@Column(name = "file_size")
	private Long fileSize;
	@Column(name="file_name")
	private String fileName;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
	@Column(name = "submitted_at")
	private LocalDateTime submittedAt;
	@Column(name = "reviewed_by")
	private String reviewedBy;
	
	
	@Column(name = "forwarded_at")
	private LocalDateTime forwardedAt;
	
	
	@Column(name = "approved_by")
	private String approvedBy;
	@Column(name = "approved_at")
	private String approvedAt;
	@Column(name = "rejected_by")
	private String rejectedBy;
	@Column(name = "rejected_at")
	private LocalDateTime rejectedAt;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "review_comments", columnDefinition = "jsonb")
	private List<String> reviewComments = new ArrayList<>();
	
	

}