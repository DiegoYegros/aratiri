package com.aratiri.auth.application.port.out;

import java.util.Optional;

public interface AuthenticatedUserPort {

    Optional<String> getCurrentUserEmail();
}
