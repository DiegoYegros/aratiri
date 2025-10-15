package com.aratiri.service;

import com.aratiri.dto.decoder.DecodedResultDTO;

public interface DecoderService {
    DecodedResultDTO decode(String input);
}
