package com.aratiri.aratiri.context;


import com.aratiri.aratiri.dto.users.UserDTO;

public class AratiriContext {
    private final UserDTO user;

    public AratiriContext(UserDTO user) {
        this.user = user;
    }

    public UserDTO getUser() {
        return user;
    }

}