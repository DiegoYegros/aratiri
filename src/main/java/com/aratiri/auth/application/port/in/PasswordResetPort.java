package com.aratiri.auth.application.port.in;

public interface PasswordResetPort {

    void initiatePasswordReset(PasswordResetRequestCommand command);

    void completePasswordReset(PasswordResetCompletionCommand command);
}
