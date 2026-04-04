package com.example.user.repository;

import com.example.user.model.Friendship;
import com.example.user.model.FriendshipId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, FriendshipId> {

    @Query("SELECT f FROM Friendship f WHERE f.id.userId = :userId")
    List<Friendship> findAllByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM Friendship f WHERE " +
           "(f.id.userId = :userId AND f.id.friendId = :friendId) OR " +
           "(f.id.userId = :friendId AND f.id.friendId = :userId)")
    void deleteBidirectional(@Param("userId") UUID userId, @Param("friendId") UUID friendId);
}
