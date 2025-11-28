package com.aratiri.auth.application.port.out;

import com.aratiri.auth.domain.GoogleUserProfile;

public interface GoogleTokenVerifierPort {

    GoogleUserProfile verify(String token);
}
