package com.aratiri.auth.application.dto;

import com.aratiri.auth.domain.Role;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserDTO {
    private String id;
    private String name;
    private String email;
    private Role role;
}
