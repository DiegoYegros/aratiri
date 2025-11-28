package com.aratiri.payments.application.port.out;

import com.aratiri.payments.domain.OutboxMessage;

public interface OutboxEventPort {

    void save(OutboxMessage message);
}
