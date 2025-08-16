package com.aratiri.aratiri.service;

import com.aratiri.aratiri.dto.decoder.DecodedResultDTO;

public interface DecoderService {
    DecodedResultDTO decode(String input);
}
