package com.aratiri.admin.application.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class NodeInfoDTO {
    private String pubKey;
    private String alias;
    private String color;
    private List<String> addresses;
    private long capacity;
    private int numChannels;
    private double betweennessCentrality;
}