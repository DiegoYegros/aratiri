package com.aratiri.auth.application.port.in;

import com.aratiri.auth.domain.AuthTokens;

public interface GoogleAuthPort {

    AuthTokens loginWithGoogle(String googleToken);
}
