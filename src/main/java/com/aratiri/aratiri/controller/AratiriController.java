package com.aratiri.aratiri.controller;

import com.aratiri.aratiri.dto.response.WalletBalanceResponse;
import com.aratiri.aratiri.service.AratiriService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AratiriController {

    AratiriService aratiriService;

    public AratiriController(AratiriService aratiriService) {
        this.aratiriService = aratiriService;
    }

    @GetMapping("/balance")
    public WalletBalanceResponse getWalletBalance(){
        return aratiriService.getWalletBalance();
    }
}
