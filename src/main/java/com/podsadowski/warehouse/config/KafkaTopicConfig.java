package com.podsadowski.warehouse.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;
import lombok.RequiredArgsConstructor;

@Configuration
@Profile("demo")
@RequiredArgsConstructor
public class KafkaTopicConfig {

    private final WarehouseProperties warehouseProperties;

    @Bean
    public NewTopic warehouseProgressTopic() {
        WarehouseProperties.Kafka kafka = warehouseProperties.getKafka();
        return TopicBuilder.name(kafka.getProgressTopic())
                .partitions(kafka.getPartitions())
                .replicas(kafka.getReplicas())
                .build();
    }
}
