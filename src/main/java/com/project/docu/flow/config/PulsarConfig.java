package com.project.docu.flow.config;

import java.util.concurrent.TimeUnit;

import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PulsarConfig {

    private static final Logger logger = LoggerFactory.getLogger(PulsarConfig.class);

    @Value("${pulsar.service-url}")
    private String serviceUrl;

    @Value("${pulsar.connection.operation-timeout-ms:30000}")
    private int operationTimeoutMs;

    @Value("${pulsar.connection.connection-timeout-ms:10000}")
    private int connectionTimeoutMs;

    @Value("${pulsar.connection.io-threads:10}")
    private int ioThreads;

    @Value("${pulsar.connection.listener-threads:10}")
    private int listenerThreads;

    @Value("${pulsar.connection.enable-tls:false}")
    private boolean enableTls;

    @Value("${pulsar.connection.enable-authentication:false}")
    private boolean enableAuthentication;

    @Bean
    public PulsarClient pulsarClient() throws PulsarClientException {
        logger.info("Initializing Pulsar client with service URL: {}", serviceUrl);
        
        try {
            PulsarClient client = PulsarClient.builder()
                    .serviceUrl(serviceUrl)
                    .operationTimeout(operationTimeoutMs, TimeUnit.MILLISECONDS)
                    .connectionTimeout(connectionTimeoutMs, TimeUnit.MILLISECONDS)
                    .ioThreads(ioThreads)
                    .listenerThreads(listenerThreads)
                    .enableTls(enableTls)
                    .build();
            
            logger.info("Pulsar client initialized successfully");
            return client;
            
        } catch (PulsarClientException e) {
            logger.error("Failed to initialize Pulsar client", e);
            throw e;
        }
    }
}