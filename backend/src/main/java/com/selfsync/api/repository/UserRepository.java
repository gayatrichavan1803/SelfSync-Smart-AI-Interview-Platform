package com.selfsync.api.repository;

import com.selfsync.api.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByFirebaseUid(String firebaseUid);
    Optional<User> findByResetToken(String resetToken);
    boolean existsByEmail(String email);
}
