package com.podsadowski.warehouse.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@Profile("demo")
public class KafkaTopicConfig {

    @Bean
    public NewTopic warehouseProgressTopic() {
        return TopicBuilder.name("warehouse-progress")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
