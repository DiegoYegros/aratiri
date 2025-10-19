package com.aratiri.dto.users;

import com.aratiri.enums.Role;
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
