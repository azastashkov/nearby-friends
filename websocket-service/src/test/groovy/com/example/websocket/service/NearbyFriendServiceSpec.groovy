package com.example.websocket.service

import com.example.websocket.dto.LocationUpdate
import spock.lang.Specification
import spock.lang.Subject

class NearbyFriendServiceSpec extends Specification {

    LocationCacheService cacheService = Mock()
    PubSubService pubSubService = Mock()

    @Subject
    NearbyFriendService service = new NearbyFriendService(cacheService, pubSubService, 5.0)

    def 'should return only friends within 5 mile radius'() {
        given:
        def userLocation = new LocationUpdate(37.7749, -122.4194, System.currentTimeMillis())
        def nearbyFriend = new LocationUpdate(37.7849, -122.4094, System.currentTimeMillis())
        def farFriend = new LocationUpdate(34.0522, -118.2437, System.currentTimeMillis())
        def friendIds = ['friend-1', 'friend-2']
        cacheService.getLocation('friend-1') >> Optional.of(nearbyFriend)
        cacheService.getLocation('friend-2') >> Optional.of(farFriend)
        when:
        def result = service.computeNearbyFriends(friendIds, userLocation)
        then:
        result.size() == 1
        result[0].friendId() == 'friend-1'
        result[0].distance() <= 5.0
    }

    def 'should handle empty friend list'() {
        given:
        def userLocation = new LocationUpdate(37.7749, -122.4194, System.currentTimeMillis())
        when:
        def result = service.computeNearbyFriends([], userLocation)
        then:
        result.isEmpty()
        0 * cacheService.getLocation(_)
    }

    def 'should skip offline friends with no cached location'() {
        given:
        def userLocation = new LocationUpdate(37.7749, -122.4194, System.currentTimeMillis())
        def friendIds = ['online-friend', 'offline-friend']
        cacheService.getLocation('online-friend') >> Optional.of(
            new LocationUpdate(37.7849, -122.4094, System.currentTimeMillis()))
        cacheService.getLocation('offline-friend') >> Optional.empty()
        when:
        def result = service.computeNearbyFriends(friendIds, userLocation)
        then:
        result.size() == 1
        result[0].friendId() == 'online-friend'
    }

    def 'processLocationUpdate should update cache and publish'() {
        given:
        def userId = 'user-1'
        def update = new LocationUpdate(37.7749, -122.4194, System.currentTimeMillis())
        when:
        service.processLocationUpdate(userId, update)
        then:
        1 * cacheService.updateLocation(userId, update)
        1 * pubSubService.publish(userId, update)
    }

    def 'evaluateFriendUpdate should return nearby friend when within radius'() {
        given:
        def userLocation = new LocationUpdate(37.7749, -122.4194, System.currentTimeMillis())
        def friendLocation = new LocationUpdate(37.7849, -122.4094, System.currentTimeMillis())
        when:
        def result = service.evaluateFriendUpdate(userLocation, 'friend-1', friendLocation)
        then:
        result.isPresent()
        result.get().distance() <= 5.0
    }

    def 'evaluateFriendUpdate should return empty when beyond radius'() {
        given:
        def userLocation = new LocationUpdate(37.7749, -122.4194, System.currentTimeMillis())
        def friendLocation = new LocationUpdate(34.0522, -118.2437, System.currentTimeMillis())
        when:
        def result = service.evaluateFriendUpdate(userLocation, 'friend-1', friendLocation)
        then:
        result.isEmpty()
    }
}
