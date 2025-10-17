package com.project.docu.flow.model.events;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentEvent {


    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("event_type")
    private EventType eventType;

    @JsonProperty("document_id")
    private String documentId;
    
    @JsonProperty("document_title")
    private String documentTitle;

 
    @JsonProperty("previous_status")
    private WorkflowStatus previousStatus;


    @JsonProperty("new_status")
    private WorkflowStatus newStatus;

    @JsonProperty("triggered_by")
    private String triggeredBy;


    @JsonProperty("triggered_by_name")
    private String triggeredByName;


    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    
    @JsonProperty("metadata")
    private Map<String, Object> metadata;


    @JsonProperty("comments")
    private String comments;


    @JsonProperty("priority")
    private EventPriority priority;


    public enum EventType {
        DOCUMENT_CREATED,
        DOCUMENT_SUBMITTED,
        DOCUMENT_UNDER_REVIEW,
        DOCUMENT_APPROVED,
        DOCUMENT_REJECTED,
        DOCUMENT_REVISION_REQUESTED,
        DOCUMENT_RESUBMITTED,
        DOCUMENT_WITHDRAWN,
        DOCUMENT_DELETED,
        REVIEWER_ASSIGNED,
        APPROVER_ASSIGNED,
        COMMENT_ADDED,
        DOCUMENT_UPDATED
    }


    public enum WorkflowStatus {
        DRAFT,
        SUBMITTED,
        UNDER_REVIEW,
        APPROVED,
        REJECTED,
        REVISION_REQUESTED,
        WITHDRAWN,
        DELETED
    }


    public enum EventPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }
}