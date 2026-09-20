package com.dogdog.nomat.domain.room.config;

import com.dogdog.nomat.domain.room.service.RoomEventSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
public class RoomEventRedisConfig {

    private final RoomEventProperties eventProperties;

    @Bean
    public RedisMessageListenerContainer roomEventRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RoomEventSubscriber eventSubscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(eventSubscriber, new ChannelTopic(eventProperties.getChannel()));
        return container;
    }
}
