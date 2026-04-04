package com.example.user.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "friendships")
public class Friendship {
    @EmbeddedId
    private FriendshipId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Friendship() {}
    public Friendship(FriendshipId id) { this.id = id; }

    public FriendshipId getId() { return id; }
    public void setId(FriendshipId id) { this.id = id; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
