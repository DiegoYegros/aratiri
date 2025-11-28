package com.aratiri.infrastructure.web.context;

import com.aratiri.auth.application.dto.UserDTO;

public record AratiriContext(UserDTO user) {
}