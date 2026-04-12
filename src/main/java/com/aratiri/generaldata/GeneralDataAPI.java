package com.aratiri.generaldata;

import com.aratiri.generaldata.application.dto.BtcPriceHistoryResponseDTO;
import com.aratiri.generaldata.application.dto.CurrentBtcPriceResponseDTO;
import com.aratiri.generaldata.application.port.in.GeneralDataPort;
import com.aratiri.infrastructure.web.context.AratiriContext;
import com.aratiri.infrastructure.web.context.AratiriCtx;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/general-data")
@Tag(name = "General Data", description = "General Information.")
public class GeneralDataAPI {

    private final GeneralDataPort generalDataPort;

    public GeneralDataAPI(GeneralDataPort generalDataPort) {
        this.generalDataPort = generalDataPort;
    }

    @GetMapping("/currencies")
    @Operation(summary = "List supported fiat currencies")
    public ResponseEntity<List<String>> getFiatCurrencies(@AratiriCtx AratiriContext ctx) {
        return ResponseEntity.ok(generalDataPort.getFiatCurrencies());
    }

    @GetMapping("/btc-price/current")
    @Operation(summary = "Get the current BTC spot price for a fiat currency")
    public ResponseEntity<CurrentBtcPriceResponseDTO> getCurrentBtcPrice(
            @RequestParam String currency,
            @AratiriCtx AratiriContext ctx
    ) {
        return ResponseEntity.ok(generalDataPort.getCurrentBtcPrice(currency));
    }

    @GetMapping("/btc-price/history")
    @Operation(summary = "Get historical BTC price points for a fiat currency")
    public ResponseEntity<BtcPriceHistoryResponseDTO> getBtcPriceHistory(
            @RequestParam String currency,
            @RequestParam String range,
            @AratiriCtx AratiriContext ctx
    ) {
        return ResponseEntity.ok(generalDataPort.getBtcPriceHistory(currency, range));
    }
}
