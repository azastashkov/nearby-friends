package com.example.websocket.service

import spock.lang.Specification

class ConsistentHashRingSpec extends Specification {

    def 'single node should receive all keys'() {
        given:
        def ring = new ConsistentHashRing()
        ring.addNode('redis-1:6379')
        when:
        def results = (1..100).collect { ring.getNode(UUID.randomUUID().toString()) }
        then:
        results.every { it == 'redis-1:6379' }
    }

    def 'three nodes should distribute keys roughly uniformly'() {
        given:
        def ring = new ConsistentHashRing()
        ring.addNode('redis-1:6379')
        ring.addNode('redis-2:6379')
        ring.addNode('redis-3:6379')
        when:
        def counts = [:].withDefault { 0 }
        10000.times {
            def node = ring.getNode(UUID.randomUUID().toString())
            counts[node]++
        }
        then:
        counts.every { _, count -> count > 2500 && count < 4000 }
    }

    def 'same key always returns same node'() {
        given:
        def ring = new ConsistentHashRing()
        ring.addNode('redis-1:6379')
        ring.addNode('redis-2:6379')
        def key = 'test-user-id'
        when:
        def results = (1..100).collect { ring.getNode(key) }
        then:
        results.every { it == results[0] }
    }

    def 'adding a node remaps limited keys'() {
        given:
        def ring = new ConsistentHashRing()
        ring.addNode('redis-1:6379')
        ring.addNode('redis-2:6379')
        def keys = (1..1000).collect { UUID.randomUUID().toString() }
        def before = keys.collectEntries { [it, ring.getNode(it)] }
        when:
        ring.addNode('redis-3:6379')
        def after = keys.collectEntries { [it, ring.getNode(it)] }
        def changed = keys.count { before[it] != after[it] }
        then:
        changed < 500
    }

    def 'empty ring should throw IllegalStateException'() {
        given:
        def ring = new ConsistentHashRing()
        when:
        ring.getNode('any-key')
        then:
        thrown(IllegalStateException)
    }

    def 'ring size should be 150 virtual nodes per physical node'() {
        given:
        def ring = new ConsistentHashRing()
        when:
        ring.addNode('redis-1:6379')
        then:
        ring.size() == 150
    }
}
