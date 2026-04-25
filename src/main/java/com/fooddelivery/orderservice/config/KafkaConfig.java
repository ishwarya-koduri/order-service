package com.fooddelivery.orderservice.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer and consumer configuration.
 */
@Configuration
public class KafkaConfig {

    private static final ApplicationLogger log = ApplicationLogger.getLogger(KafkaConfig.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroupId;

    /**
     * Producer factory with idempotent configuration.
     * acks=all ensures no data loss even if the leader broker crashes.
     * enable.idempotence prevents duplicate messages on retry.
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        log.info("producerFactory started — bootstrapServers={}", bootstrapServers);

        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        log.info("producerFactory completed — producer configured with acks=all idempotence=true");
        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * KafkaTemplate used by KafkaOrderEventPublisher to send events.
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Consumer factory for @KafkaListener beans.
     * auto.offset.reset=earliest ensures no events are missed on first startup.
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        log.info("consumerFactory started — bootstrapServers={} groupId={}",
                bootstrapServers, consumerGroupId);

        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        log.info("consumerFactory completed — groupId={}", consumerGroupId);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Listener container factory used by @KafkaListener in DeliveryNotificationConsumer.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}