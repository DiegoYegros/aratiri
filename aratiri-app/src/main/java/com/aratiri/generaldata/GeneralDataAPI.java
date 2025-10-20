package com.aratiri.generaldata;

import com.aratiri.infrastructure.web.context.AratiriContext;
import com.aratiri.infrastructure.web.context.AratiriCtx;
import com.aratiri.generaldata.application.port.in.GeneralDataPort;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
    public ResponseEntity<List<String>> getFiatCurrencies(@AratiriCtx AratiriContext ctx) {
        return ResponseEntity.ok(generalDataPort.getFiatCurrencies());
    }
}
