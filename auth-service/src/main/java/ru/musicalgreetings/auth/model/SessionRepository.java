package ru.musicalgreetings.auth.model;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionRepository extends CrudRepository<Session, UUID> {
    Optional<Session> findByRefreshTokenHash(
            byte[] refreshTokenHash
    );
}
