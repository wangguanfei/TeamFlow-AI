package com.teamflow.ai.modules.user.dto;

import java.util.List;

public record UserRoleRequest(
        List<Long> roleIds
) {
}
