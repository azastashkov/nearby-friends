package com.example.location.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> httpHistogramCustomizer() {
        return registry -> registry.config().meterFilter(
                new MeterFilter() {
                    @Override
                    public DistributionStatisticConfig configure(io.micrometer.core.instrument.Meter.Id id,
                                                                 DistributionStatisticConfig config) {
                        if (id.getName().startsWith("http.server.requests")) {
                            return DistributionStatisticConfig.builder()
                                    .percentilesHistogram(true)
                                    .build()
                                    .merge(config);
                        }
                        return config;
                    }
                });
    }
}
