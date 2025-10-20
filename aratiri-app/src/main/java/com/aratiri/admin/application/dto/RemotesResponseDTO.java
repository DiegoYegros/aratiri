package com.aratiri.admin.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RemotesResponseDTO {
    private List<NodeInfoDTO> nodes;
}
