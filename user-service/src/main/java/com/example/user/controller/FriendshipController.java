package com.example.user.controller;

import com.example.user.service.FriendshipService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/{userId}/friends")
public class FriendshipController {
    private final FriendshipService friendshipService;

    public FriendshipController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    @PostMapping("/{friendId}")
    public ResponseEntity<Void> addFriend(@PathVariable UUID userId, @PathVariable UUID friendId) {
        friendshipService.addFriendship(userId, friendId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{friendId}")
    public ResponseEntity<Void> removeFriend(@PathVariable UUID userId, @PathVariable UUID friendId) {
        friendshipService.removeFriendship(userId, friendId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listFriends(@PathVariable UUID userId) {
        List<UUID> friends = friendshipService.listFriends(userId);
        return ResponseEntity.ok(Map.of("friends", friends));
    }
}
