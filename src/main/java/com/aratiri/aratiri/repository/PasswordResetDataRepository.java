package com.aratiri.aratiri.repository;

import com.aratiri.aratiri.entity.PasswordResetData;
import com.aratiri.aratiri.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetDataRepository extends JpaRepository<PasswordResetData, String> {
    Optional<PasswordResetData> findByUser(UserEntity user);

}