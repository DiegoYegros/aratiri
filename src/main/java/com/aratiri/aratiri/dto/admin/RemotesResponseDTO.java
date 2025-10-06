package com.aratiri.aratiri.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RemotesResponseDTO {
    private List<NodeInfoDTO> nodes;
}
