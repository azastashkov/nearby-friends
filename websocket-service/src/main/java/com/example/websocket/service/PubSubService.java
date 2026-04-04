package com.example.websocket.service;

import com.example.websocket.dto.LocationUpdate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

@Service
public class PubSubService {
    private static final Logger log = LoggerFactory.getLogger(PubSubService.class);
    private static final String CHANNEL_PREFIX = "location:";

    private final ZookeeperService zookeeperService;
    private final ObjectMapper objectMapper;
    private final Map<String, LettuceConnectionFactory> connectionFactories = new ConcurrentHashMap<>();
    private final Map<String, RedisMessageListenerContainer> containers = new ConcurrentHashMap<>();
    private final Map<String, StringRedisTemplate> templates = new ConcurrentHashMap<>();
    private final Map<String, SubscriptionInfo> activeSubscriptions = new ConcurrentHashMap<>();

    @Value("${redis.pubsub.nodes}")
    private List<String> pubsubNodes;

    private record SubscriptionInfo(String nodeAddress, MessageListener listener, ChannelTopic topic) {}

    public PubSubService(ZookeeperService zookeeperService, ObjectMapper objectMapper) {
        this.zookeeperService = zookeeperService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        for (String nodeAddress : pubsubNodes) {
            String[] parts = nodeAddress.split(":");
            LettuceConnectionFactory factory = new LettuceConnectionFactory(parts[0], Integer.parseInt(parts[1]));
            factory.afterPropertiesSet();
            connectionFactories.put(nodeAddress, factory);

            StringRedisTemplate template = new StringRedisTemplate(factory);
            template.afterPropertiesSet();
            templates.put(nodeAddress, template);

            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(factory);
            container.setTaskExecutor(Executors.newFixedThreadPool(20));
            container.afterPropertiesSet();
            container.start();
            containers.put(nodeAddress, container);
        }
    }

    public void publish(String userId, LocationUpdate update) {
        String nodeAddress = zookeeperService.getRing().getNode(userId);
        StringRedisTemplate template = templates.get(nodeAddress);
        try {
            String json = objectMapper.writeValueAsString(update);
            template.convertAndSend(CHANNEL_PREFIX + userId, json);
        } catch (JsonProcessingException e) { log.error("Failed to serialize location update", e); }
    }

    public void subscribe(String userId, MessageListener listener) {
        String nodeAddress = zookeeperService.getRing().getNode(userId);
        ChannelTopic topic = new ChannelTopic(CHANNEL_PREFIX + userId);
        containers.get(nodeAddress).addMessageListener(listener, topic);
        activeSubscriptions.put(userId, new SubscriptionInfo(nodeAddress, listener, topic));
    }

    public void unsubscribe(String userId) {
        SubscriptionInfo info = activeSubscriptions.remove(userId);
        if (info != null) containers.get(info.nodeAddress()).removeMessageListener(info.listener(), info.topic());
    }

    @PreDestroy
    public void destroy() {
        containers.values().forEach(RedisMessageListenerContainer::stop);
        connectionFactories.values().forEach(LettuceConnectionFactory::destroy);
    }
}
