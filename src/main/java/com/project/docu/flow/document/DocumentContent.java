package com.project.docu.flow.document;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class DocumentContent {
	@Id
	private String id;
	private String filename;
	private String contentType;
	private byte[] content;
	private LocalDateTime uploadedAt;

	public DocumentContent(String filename, String contentType, byte[] content) {
		this.filename = filename;
		this.contentType = contentType;
		this.content = content;
		this.uploadedAt = LocalDateTime.now();
	}
}