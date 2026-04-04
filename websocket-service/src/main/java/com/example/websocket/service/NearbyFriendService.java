package com.example.websocket.service;

import com.example.websocket.dto.LocationUpdate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class NearbyFriendService {
    public record NearbyFriend(String friendId, double distance, long timestamp) {}

    private final LocationCacheService cacheService;
    private final PubSubService pubSubService;
    private final double radiusMiles;

    public NearbyFriendService(LocationCacheService cacheService, PubSubService pubSubService,
                                @Value("${nearby.radius-miles}") double radiusMiles) {
        this.cacheService = cacheService;
        this.pubSubService = pubSubService;
        this.radiusMiles = radiusMiles;
    }

    public List<NearbyFriend> computeNearbyFriends(List<String> friendIds, LocationUpdate userLocation) {
        return friendIds.stream()
                .map(fid -> cacheService.getLocation(fid)
                        .map(loc -> {
                            double dist = DistanceCalculator.calculateMiles(
                                    userLocation.latitude(), userLocation.longitude(),
                                    loc.latitude(), loc.longitude());
                            return new NearbyFriend(fid, dist, loc.timestamp());
                        }).orElse(null))
                .filter(Objects::nonNull)
                .filter(nf -> nf.distance() <= radiusMiles)
                .toList();
    }

    public void processLocationUpdate(String userId, LocationUpdate update) {
        cacheService.updateLocation(userId, update);
        pubSubService.publish(userId, update);
    }

    public Optional<NearbyFriend> evaluateFriendUpdate(LocationUpdate userLocation, String friendId, LocationUpdate friendLocation) {
        double distance = DistanceCalculator.calculateMiles(
                userLocation.latitude(), userLocation.longitude(),
                friendLocation.latitude(), friendLocation.longitude());
        if (distance <= radiusMiles) return Optional.of(new NearbyFriend(friendId, distance, friendLocation.timestamp()));
        return Optional.empty();
    }
}
