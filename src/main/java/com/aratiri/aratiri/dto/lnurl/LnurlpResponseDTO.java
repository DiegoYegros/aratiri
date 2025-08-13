package com.aratiri.aratiri.dto.lnurl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LnurlpResponseDTO {
    private String tag;
    private String status;
    private Boolean allowsNostr;
    private String nostrPubkey;
    private String callback;
    private Long minSendable;
    private Long maxSendable;
    private String metadata;
    private Integer commentAllowed;
}