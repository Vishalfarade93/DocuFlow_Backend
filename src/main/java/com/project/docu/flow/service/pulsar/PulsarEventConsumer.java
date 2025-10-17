package com.project.docu.flow.service.pulsar;

import java.util.concurrent.TimeUnit;

import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.client.api.SubscriptionType;
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
 * Service responsible for consuming document workflow events from Pulsar topics
 * Processes events asynchronously and triggers appropriate actions
 */
@Service
public class PulsarEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(PulsarEventConsumer.class);

    @Autowired
    private PulsarClient pulsarClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventProcessor eventProcessor;

    @Value("${pulsar.topics.document-events}")
    private String documentEventsTopic;

    @Value("${pulsar.consumer.subscription-name}")
    private String subscriptionName;

    @Value("${pulsar.consumer.subscription-type:Shared}")
    private String subscriptionType;

    @Value("${pulsar.consumer.consumer-name:docuflow-consumer-1}")
    private String consumerName;

    @Value("${pulsar.consumer.ack-timeout-ms:10000}")
    private int ackTimeoutMs;

    @Value("${pulsar.consumer.receive-queue-size:1000}")
    private int receiveQueueSize;

    private Consumer<byte[]> consumer;
    private volatile boolean running = false;

    /**
     * Initialize and start the Pulsar consumer after bean construction
     */
    @PostConstruct
    public void init() {
        try {
            logger.info("Initializing Pulsar consumer for topic: {} with subscription: {}", 
                    documentEventsTopic, subscriptionName);
            
            consumer = pulsarClient.newConsumer()
                    .topic(documentEventsTopic)
                    .subscriptionName(subscriptionName)
                    .subscriptionType(getSubscriptionType())
                    .consumerName(consumerName)
                    .ackTimeout(ackTimeoutMs, TimeUnit.MILLISECONDS)
                    .receiverQueueSize(receiveQueueSize)
                    .subscriptionInitialPosition(SubscriptionInitialPosition.Latest)
                    .subscribe();
            
            logger.info("Pulsar consumer initialized successfully");
            
            // Start consuming messages in a separate thread
            startConsuming();
            
        } catch (PulsarClientException e) {
            logger.error("Failed to initialize Pulsar consumer", e);
            throw new RuntimeException("Failed to initialize Pulsar consumer", e);
        }
    }

    /**
     * Start consuming messages from Pulsar
     */
    private void startConsuming() {
        running = true;
        Thread consumerThread = new Thread(() -> {
            logger.info("Starting Pulsar message consumption");
            
            while (running) {
                try {
                    // Receive message with timeout
                    Message<byte[]> message = consumer.receive(100, TimeUnit.MILLISECONDS);
                    
                    if (message != null) {
                        processMessage(message);
                    }
                    
                } catch (PulsarClientException e) {
                    if (running) {
                        logger.error("Error receiving message from Pulsar", e);
                        // Add exponential backoff if needed
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            
            logger.info("Pulsar message consumption stopped");
        }, "pulsar-consumer-thread");
        
        consumerThread.setDaemon(false);
        consumerThread.start();
    }

    /**
     * Process a received Pulsar message
     */
    private void processMessage(Message<byte[]> message) {
        try {
            // Deserialize the event
            DocumentEvent event = objectMapper.readValue(message.getValue(), DocumentEvent.class);
            
            logger.info("Received event {} for document {} from Pulsar", 
                    event.getEventType(), event.getDocumentId());
            
            // Extract message properties
            String eventType = message.getProperty("eventType");
            String documentId = message.getProperty("documentId");
            
            // Process the event
            eventProcessor.processEvent(event, message);
            
            // Acknowledge the message after successful processing
            consumer.acknowledge(message);
            
            logger.debug("Successfully processed and acknowledged message {} for document {}", 
                    message.getMessageId(), documentId);
            
        } catch (Exception e) {
            logger.error("Failed to process message {}: {}", message.getMessageId(), e.getMessage(), e);
            
            // Negative acknowledge to requeue the message
            try {
                consumer.negativeAcknowledge(message);
                logger.info("Message {} negatively acknowledged for reprocessing", message.getMessageId());
            } catch (Exception nackException) {
                logger.error("Failed to negatively acknowledge message {}", message.getMessageId(), nackException);
            }
        }
    }

    /**
     * Convert string subscription type to enum
     */
    private SubscriptionType getSubscriptionType() {
        try {
            return SubscriptionType.valueOf(subscriptionType);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid subscription type: {}, defaulting to Shared", subscriptionType);
            return SubscriptionType.Shared;
        }
    }

    /**
     * Stop the consumer gracefully on application shutdown
     */
    @PreDestroy
    public void cleanup() {
        running = false;
        
        if (consumer != null) {
            try {
                logger.info("Closing Pulsar consumer");
                consumer.close();
                logger.info("Pulsar consumer closed successfully");
            } catch (PulsarClientException e) {
                logger.error("Error closing Pulsar consumer", e);
            }
        }
    }
}