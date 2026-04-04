package com.example.location.service

import com.example.location.model.LocationHistory
import com.example.location.repository.LocationHistoryRepository
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant

class LocationHistoryServiceSpec extends Specification {

    LocationHistoryRepository repository = Mock()

    @Subject
    LocationHistoryService service = new LocationHistoryService(repository)

    def 'save should persist a location history record'() {
        given:
        def userId = UUID.randomUUID()
        def latitude = 37.7749d
        def longitude = -122.4194d

        when:
        service.save(userId, latitude, longitude)

        then:
        1 * repository.save({ LocationHistory lh ->
            lh.userId == userId &&
            lh.latitude == latitude &&
            lh.longitude == longitude &&
            lh.timestamp != null
        })
    }

    def 'queryByTimeRange should return records within the time window'() {
        given:
        def userId = UUID.randomUUID()
        def now = Instant.now()
        def start = now.minusSeconds(3600)
        def end = now

        def record1 = new LocationHistory(userId, now.minusSeconds(1800), 37.77, -122.42)
        def record2 = new LocationHistory(userId, now.minusSeconds(900), 37.78, -122.41)
        repository.findByUserIdAndTimestampBetween(userId, start, end) >> [record1, record2]

        when:
        def results = service.queryByTimeRange(userId, start, end)

        then:
        results.size() == 2
        results[0].latitude == 37.77d
        results[1].latitude == 37.78d
    }

    def 'queryByTimeRange should return empty list when no records exist'() {
        given:
        def userId = UUID.randomUUID()
        def start = Instant.now().minusSeconds(3600)
        def end = Instant.now()
        repository.findByUserIdAndTimestampBetween(userId, start, end) >> []

        when:
        def results = service.queryByTimeRange(userId, start, end)

        then:
        results.isEmpty()
    }
}
