package com.aratiri.repository;

import com.aratiri.entity.VerificationData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VerificationDataRepository extends JpaRepository<VerificationData, String> {
}