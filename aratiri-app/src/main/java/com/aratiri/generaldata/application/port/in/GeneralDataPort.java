package com.aratiri.generaldata.application.port.in;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface GeneralDataPort {

    Map<String, BigDecimal> getCurrentBtcPrice();

    List<String> getFiatCurrencies();
}