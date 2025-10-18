package com.project.docu.flow.service.pulsar;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.pulsar.client.api.CompressionType;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.docu.flow.model.events.DocumentEvent;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Service responsible for publishing document workflow events to Pulsar topics
 */
@Service
public class PulsarEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(PulsarEventPublisher.class);

    @Autowired
    private PulsarClient pulsarClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${pulsar.topics.document-events}")
    private String documentEventsTopic;

    @Value("${pulsar.producer.send-timeout-ms:30000}")
    private int sendTimeoutMs;

    @Value("${pulsar.producer.block-if-queue-full:true}")
    private boolean blockIfQueueFull;

    @Value("${pulsar.producer.batching-enabled:true}")
    private boolean batchingEnabled;

    @Value("${pulsar.producer.batching-max-publish-delay-ms:10}")
    private int batchingMaxPublishDelayMs;

    @Value("${pulsar.producer.max-pending-messages:1000}")
    private int maxPendingMessages;

    private Producer<byte[]> producer;
    @PostConstruct
    public void init() {
        try {
            logger.info("Initializing Pulsar producer for topic: {}", documentEventsTopic);
            
            producer = pulsarClient.newProducer()
                    .topic(documentEventsTopic)
                    .sendTimeout(sendTimeoutMs, TimeUnit.MILLISECONDS)
                    .blockIfQueueFull(blockIfQueueFull)
                    .enableBatching(batchingEnabled)
                    .batchingMaxPublishDelay(batchingMaxPublishDelayMs, TimeUnit.MILLISECONDS)
                    .maxPendingMessages(maxPendingMessages)
                    .compressionType(CompressionType.LZ4)
                    .create();
            
            logger.info("Pulsar producer initialized successfully");
        } catch (PulsarClientException e) {
            logger.error("Failed to initialize Pulsar producer", e);
            throw new RuntimeException("Failed to initialize Pulsar producer", e);
        }
    }

 
    public MessageId publishEvent(DocumentEvent event) throws Exception {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(event);
            
            MessageId messageId = producer.newMessage()
                    .key(event.getDocumentId()) 
                    .property("eventType", event.getEventType().name())
                    .property("documentId", event.getDocumentId())
                    .property("triggeredBy", event.getTriggeredBy())
                    .property("priority", event.getPriority().name())
                    .value(payload)
                    .send();
            
            logger.info("Published event {} for document {} with MessageId: {}", 
                    event.getEventType(), event.getDocumentId(), messageId);
            
            return messageId;
            
        } catch (Exception e) {
            logger.error("Failed to publish event {} for document {}", 
                    event.getEventType(), event.getDocumentId(), e);
            throw e;
        }
    }

    public CompletableFuture<MessageId> publishEventAsync(DocumentEvent event) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(event);
            
            CompletableFuture<MessageId> future = producer.newMessage()
                    .key(event.getDocumentId())
                    .property("eventType", event.getEventType().name())
                    .property("documentId", event.getDocumentId())
                    .property("triggeredBy", event.getTriggeredBy())
                    .property("priority", event.getPriority().name())
                    .value(payload)
                    .sendAsync();
            
            future.thenAccept(messageId -> 
                    logger.info("Published event {} for document {} with MessageId: {}", 
                            event.getEventType(), event.getDocumentId(), messageId))
                    .exceptionally(throwable -> {
                        logger.error("Failed to publish event {} for document {}", 
                                event.getEventType(), event.getDocumentId(), throwable);
                        return null;
                    });
            
            return future;
            
        } catch (Exception e) {
            logger.error("Failed to serialize event {} for document {}", 
                    event.getEventType(), event.getDocumentId(), e);
            CompletableFuture<MessageId> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (producer != null) {
            try {
                logger.info("Closing Pulsar producer");
                producer.close();
                logger.info("Pulsar producer closed successfully");
            } catch (PulsarClientException e) {
                logger.error("Error closing Pulsar producer", e);
            }
        }
    }
}