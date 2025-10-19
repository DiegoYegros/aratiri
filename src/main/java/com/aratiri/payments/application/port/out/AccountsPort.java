package com.aratiri.payments.application.port.out;

import com.aratiri.payments.domain.PaymentAccount;

public interface AccountsPort {

    PaymentAccount getAccount(String userId);
}
