package com.example.websocket.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.retry.RetryNTimes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ZookeeperService {
    private static final Logger log = LoggerFactory.getLogger(ZookeeperService.class);
    private final AtomicReference<ConsistentHashRing> ring = new AtomicReference<>(new ConsistentHashRing());
    private final Gson gson = new Gson();

    @Value("${zookeeper.connect-string}")
    private String connectString;

    @Value("${zookeeper.ring-path}")
    private String ringPath;

    @Value("${redis.pubsub.default-nodes}")
    private String defaultNodes;

    private CuratorFramework client;
    private CuratorCache cache;

    @PostConstruct
    public void init() throws Exception {
        client = CuratorFrameworkFactory.newClient(connectString, new RetryNTimes(5, 2000));
        client.start();
        client.blockUntilConnected(30, TimeUnit.SECONDS);

        if (client.checkExists().forPath(ringPath) == null) {
            client.create().creatingParentsIfNeeded()
                    .forPath(ringPath, defaultNodes.getBytes(StandardCharsets.UTF_8));
        }

        byte[] data = client.getData().forPath(ringPath);
        rebuildRing(data);

        cache = CuratorCache.build(client, ringPath);
        cache.listenable().addListener((type, oldData, newData) -> {
            if (newData != null) rebuildRing(newData.getData());
        });
        cache.start();
    }

    private void rebuildRing(byte[] data) {
        List<String> nodes = gson.fromJson(new String(data, StandardCharsets.UTF_8),
                new TypeToken<List<String>>() {}.getType());
        ConsistentHashRing newRing = new ConsistentHashRing();
        nodes.forEach(newRing::addNode);
        ring.set(newRing);
        log.info("Rebuilt hash ring with nodes: {}", nodes);
    }

    public ConsistentHashRing getRing() { return ring.get(); }

    @PreDestroy
    public void destroy() {
        if (cache != null) cache.close();
        if (client != null) client.close();
    }
}
