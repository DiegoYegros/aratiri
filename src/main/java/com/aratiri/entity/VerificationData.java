package com.aratiri.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "VERIFICATION_DATA")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationData {

    @Id
    private String email;
    private String name;
    private String password;
    private String alias;
    private String code;
    private LocalDateTime expiresAt;
}
