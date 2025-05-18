package com.aratiri.aratiri.service;

import com.aratiri.aratiri.dto.response.WalletBalanceResponse;

public interface AratiriService {
    WalletBalanceResponse getWalletBalance();
    String createInvoice(long sats, String memo);
}