package com.aratiri.infrastructure.persistence.jpa.repository;

import com.aratiri.infrastructure.persistence.jpa.entity.PasswordResetData;
import com.aratiri.infrastructure.persistence.jpa.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetDataRepository extends JpaRepository<PasswordResetData, String> {
    Optional<PasswordResetData> findByUser(UserEntity user);

}