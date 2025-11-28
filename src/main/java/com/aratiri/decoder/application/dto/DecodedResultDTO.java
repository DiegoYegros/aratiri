package com.aratiri.decoder.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DecodedResultDTO {
    private String type;
    private Object data;
    private String error;
}