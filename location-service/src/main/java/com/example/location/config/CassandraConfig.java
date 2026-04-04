package com.example.location.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration;
import org.springframework.data.cassandra.config.SchemaAction;

import java.util.List;

@Configuration
public class CassandraConfig extends AbstractCassandraConfiguration {

    @Value("${spring.cassandra.contact-points:cassandra}")
    private String contactPoints;

    @Value("${spring.cassandra.port:9042}")
    private int port;

    @Value("${spring.cassandra.keyspace-name:nearby_friends}")
    private String keyspaceName;

    @Value("${spring.cassandra.local-datacenter:datacenter1}")
    private String localDatacenter;

    @Override protected String getKeyspaceName() { return keyspaceName; }
    @Override protected String getContactPoints() { return contactPoints; }
    @Override protected int getPort() { return port; }
    @Override protected String getLocalDataCenter() { return localDatacenter; }
    @Override public SchemaAction getSchemaAction() { return SchemaAction.CREATE_IF_NOT_EXISTS; }

    @Override
    protected List<String> getStartupScripts() {
        return List.of(
            "CREATE KEYSPACE IF NOT EXISTS " + keyspaceName +
            " WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}"
        );
    }
}
