package com.fooddelivery.orderservice.outbox;

import com.fooddelivery.orderservice.config.ApplicationLogger;
import com.fooddelivery.orderservice.kafka.KafkaOrderEventPublisher;
import com.fooddelivery.orderservice.model.OutboxEvent;
import com.fooddelivery.orderservice.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Polls the outbox_events table and publishes unpublished events to Kafka.
 * Runs every 5 seconds. Safe to run across multiple service instances
 * because the query uses SELECT FOR UPDATE SKIP LOCKED.
 */
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private static final ApplicationLogger log = ApplicationLogger.getLogger(OutboxPoller.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxRepository outboxRepository;
    private final KafkaOrderEventPublisher kafkaPublisher;

    /**
     * Reads a batch of unpublished events and publishes each to Kafka.
     * Marks each event as published only after a successful Kafka send.
     * If Kafka send fails, the event remains unpublished and is retried next cycle.
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void pollAndPublish() {
        List<OutboxEvent> unpublished = outboxRepository
                .findUnpublishedBatch(PageRequest.of(0, BATCH_SIZE));

        if (unpublished.isEmpty()) {
            return;
        }

        log.info("pollAndPublish — found {} unpublished events", unpublished.size());

        for (OutboxEvent event : unpublished) {
            publish(event);
        }

        log.info("pollAndPublish completed — processed {} events", unpublished.size());
    }

    /**
     * Publishes a single outbox event to Kafka.
     * Per-event exception handling ensures one failed event does not block the rest.
     */
    private void publish(OutboxEvent event) {
        log.info("publish started — eventId={} orderId={} type={}",
                event.getId(), event.getOrderId(), event.getEventType());
        try {
            kafkaPublisher.publish(event.getOrderId(), event.getPayload());

            event.markPublished();
            outboxRepository.save(event);

            log.info("publish completed — eventId={} orderId={}",
                    event.getId(), event.getOrderId());

        } catch (Exception e) {
            // Log and continue — this event will be retried on the next poll cycle
            log.error("publish failed — eventId={} orderId={} will retry on next cycle",
                    event.getId(), event.getOrderId(), e);
        }
    }
}