package com.aratiri.aratiri.dto.lnurl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LnurlCallbackResponseDTO {

    @JsonProperty("pr")
    private String paymentRequest;

    private List<Object> routes;
}