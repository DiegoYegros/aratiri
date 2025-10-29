package com.aratiri.auth.application.port.out;

import com.aratiri.auth.domain.AuthProvider;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.auth.domain.Role;

public interface UserCommandPort {

    AuthUser registerLocalUser(String name, String email, String encodedPassword);

    AuthUser registerSocialUser(String name, String email, AuthProvider provider, Role role);

    void updatePassword(String userId, String encodedPassword);
}
