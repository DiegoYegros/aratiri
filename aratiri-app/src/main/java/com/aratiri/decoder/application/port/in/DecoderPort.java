package com.aratiri.decoder.application.port.in;

import com.aratiri.decoder.application.dto.DecodedResultDTO;

public interface DecoderPort {

    DecodedResultDTO decode(String input);
}
