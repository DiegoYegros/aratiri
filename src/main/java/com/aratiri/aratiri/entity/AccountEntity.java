package com.aratiri.aratiri.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Entity
@Table(name = "ACCOUNTS")
@Data
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String bitcoinAddress;

    @Column(nullable = false)
    private long balance = 0L;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    public AccountEntity() {
        this.id = UUID.randomUUID().toString();
    }
}