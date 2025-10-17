package com.project.docu.flow.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity to store notifications generated from document events
 */
@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "document_title")
    private String documentTitle;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "recipient_user", nullable = false)
    private String recipientUser;

    @Column(name = "triggered_by")
    private String triggeredBy;

    @Column(name = "triggered_by_name")
    private String triggeredByName;

    @Column(name = "priority")
    private String priority;

    
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}