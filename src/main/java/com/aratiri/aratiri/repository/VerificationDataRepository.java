package com.aratiri.aratiri.repository;

import com.aratiri.aratiri.entity.VerificationData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VerificationDataRepository extends JpaRepository<VerificationData, String> {
}