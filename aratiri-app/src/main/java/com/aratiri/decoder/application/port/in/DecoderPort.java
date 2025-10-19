package com.aratiri.decoder.application.port.in;

import com.aratiri.decoder.api.dto.DecodedResultDTO;

public interface DecoderPort {

    DecodedResultDTO decode(String input);
}
