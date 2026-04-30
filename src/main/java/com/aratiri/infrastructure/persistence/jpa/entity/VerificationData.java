package com.aratiri.infrastructure.persistence.jpa.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "VERIFICATION_DATA")
@Getter
@Setter
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
