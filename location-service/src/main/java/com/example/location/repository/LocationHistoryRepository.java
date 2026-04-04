package com.example.location.repository;

import com.example.location.model.LocationHistory;
import org.springframework.data.cassandra.repository.CassandraRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LocationHistoryRepository extends CassandraRepository<LocationHistory, UUID> {
    List<LocationHistory> findByUserIdAndTimestampBetween(UUID userId, Instant start, Instant end);
}
