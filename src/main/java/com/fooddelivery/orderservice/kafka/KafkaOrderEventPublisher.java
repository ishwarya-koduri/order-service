package com.fooddelivery.orderservice.kafka;

import com.fooddelivery.orderservice.config.ApplicationLogger;
import com.fooddelivery.orderservice.exception.KafkaPublishException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Publishes order events to Kafka.
 * Partition key is orderId — guarantees all events for the same order
 * go to the same partition and are consumed in order.
 */
@Component
@RequiredArgsConstructor
public class KafkaOrderEventPublisher {

    private static final ApplicationLogger log = ApplicationLogger.getLogger(KafkaOrderEventPublisher.class);
    private static final int TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.topic.order-status-changed}")
    private String topic;

    /**
     * Publishes a JSON payload to the order-status-changed topic.
     * Blocks until broker acknowledges or timeout is reached.
     * Throws KafkaPublishException if send fails — caller decides retry strategy.
     */
    public void publish(UUID orderId, String payload) {
        log.info("publish started — topic={} orderId={}", topic, orderId);

        try {
            kafkaTemplate
                    .send(topic, orderId.toString(), payload)
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("publish completed — topic={} orderId={}", topic, orderId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("publish interrupted — orderId={}", orderId, e);
            throw new KafkaPublishException(
                    "Kafka publish interrupted for orderId: " + orderId, e
            );
        } catch (Exception e) {
            log.error("publish failed — topic={} orderId={}", topic, orderId, e);
            throw new KafkaPublishException(
                    "Kafka publish failed for orderId: " + orderId, e
            );
        }
    }
}