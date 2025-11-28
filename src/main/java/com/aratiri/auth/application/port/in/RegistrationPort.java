package com.aratiri.auth.application.port.in;

import com.aratiri.auth.domain.AuthTokens;

public interface RegistrationPort {

    void initiateRegistration(RegistrationCommand command);

    AuthTokens completeRegistration(VerificationCommand command);
}
