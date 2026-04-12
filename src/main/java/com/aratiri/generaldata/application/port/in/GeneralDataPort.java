package com.aratiri.generaldata.application.port.in;

import com.aratiri.generaldata.application.dto.BtcPriceHistoryResponseDTO;
import com.aratiri.generaldata.application.dto.CurrentBtcPriceResponseDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface GeneralDataPort {

    Map<String, BigDecimal> getCurrentBtcPrice();

    CurrentBtcPriceResponseDTO getCurrentBtcPrice(String currency);

    BtcPriceHistoryResponseDTO getBtcPriceHistory(String currency, String range);

    List<String> getFiatCurrencies();
}
