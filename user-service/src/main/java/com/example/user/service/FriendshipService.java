package com.example.user.service;

import com.example.user.model.Friendship;
import com.example.user.model.FriendshipId;
import com.example.user.repository.FriendshipRepository;
import com.example.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class FriendshipService {
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    public FriendshipService(FriendshipRepository friendshipRepository, UserRepository userRepository) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void addFriendship(UUID userId, UUID friendId) {
        userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        userRepository.findById(friendId).orElseThrow(() -> new IllegalArgumentException("Friend not found: " + friendId));
        friendshipRepository.save(new Friendship(new FriendshipId(userId, friendId)));
        friendshipRepository.save(new Friendship(new FriendshipId(friendId, userId)));
    }

    @Transactional
    public void removeFriendship(UUID userId, UUID friendId) {
        friendshipRepository.deleteBidirectional(userId, friendId);
    }

    public List<UUID> listFriends(UUID userId) {
        return friendshipRepository.findAllByUserId(userId).stream()
                .map(f -> f.getId().getFriendId())
                .toList();
    }
}
