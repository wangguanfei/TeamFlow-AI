package com.teamflow.ai.modules.file.dto;

import java.util.List;

public record IdListRequest(
        List<Long> ids
) {
}
