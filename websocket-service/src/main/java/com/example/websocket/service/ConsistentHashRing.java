package com.example.websocket.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class ConsistentHashRing {
    private static final int VIRTUAL_NODES = 150;
    private final TreeMap<Long, String> ring = new TreeMap<>();
    private final Map<String, List<Long>> nodeHashes = new HashMap<>();

    public void addNode(String address) {
        List<Long> hashes = new ArrayList<>();
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            long hash = hash(address + "#" + i);
            ring.put(hash, address);
            hashes.add(hash);
        }
        nodeHashes.put(address, hashes);
    }

    public void removeNode(String address) {
        List<Long> hashes = nodeHashes.remove(address);
        if (hashes != null) { hashes.forEach(ring::remove); }
    }

    public String getNode(String key) {
        if (ring.isEmpty()) throw new IllegalStateException("Hash ring is empty");
        long hash = hash(key);
        Map.Entry<Long, String> entry = ring.ceilingEntry(hash);
        return (entry != null) ? entry.getValue() : ring.firstEntry().getValue();
    }

    public int size() { return ring.size(); }

    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            long hash = 0;
            for (int i = 0; i < 8; i++) { hash = (hash << 8) | (digest[i] & 0xFF); }
            return hash;
        } catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }
    }
}
