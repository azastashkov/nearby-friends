package com.example.loadtest;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MetricsReporter {
    private final AtomicInteger activeConnections;
    private final Counter failedConnections;
    private final Counter updatesSent;
    private final Counter updatesReceived;
    private final Timer latencyTimer;

    public MetricsReporter(MeterRegistry registry) {
        this.activeConnections = registry.gauge("loadtest.connections.active", new AtomicInteger(0));
        this.failedConnections = registry.counter("loadtest.connections.failed");
        this.updatesSent = registry.counter("loadtest.updates.sent");
        this.updatesReceived = registry.counter("loadtest.updates.received");
        this.latencyTimer = Timer.builder("loadtest.latency")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void connectionOpened() { activeConnections.incrementAndGet(); }
    public void connectionClosed() { activeConnections.decrementAndGet(); }
    public void connectionFailed() { failedConnections.increment(); }
    public void updateSent() { updatesSent.increment(); }
    public void updateReceived() { updatesReceived.increment(); }
    public void recordLatency(Duration duration) { latencyTimer.record(duration); }
}
