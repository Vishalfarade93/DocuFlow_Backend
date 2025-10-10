package com.project.docu.flow.dto;

import org.springframework.web.multipart.MultipartFile;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUpdateRequest {
    private String title;
    private String description;
    private String action; 
    private MultipartFile file; 
}
