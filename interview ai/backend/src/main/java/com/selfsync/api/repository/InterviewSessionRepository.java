package com.selfsync.api.repository;

import com.selfsync.api.model.InterviewSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {
    @EntityGraph(attributePaths = {"questions", "questions.answer", "scoreReport"})
    @Query("select s from InterviewSession s where s.id = :id and s.user.id = :userId")
    Optional<InterviewSession> findDetailedByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"scoreReport"})
    @Query("select s from InterviewSession s where s.user.id = :userId order by s.createdAt desc")
    List<InterviewSession> findAllByUserIdWithScores(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"scoreReport"})
    @Query("""
        select s from InterviewSession s
        where s.user.id = :userId
          and (:domain is null or s.domain = :domain)
          and (:status is null or s.status = :status)
        order by s.createdAt desc
        """)
    List<InterviewSession> findFiltered(
            @Param("userId") UUID userId,
            @Param("domain") String domain,
            @Param("status") String status);
}
