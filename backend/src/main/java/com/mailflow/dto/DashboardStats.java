package com.mailflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStats {
    private long totalUsers;
    private long totalEmails;
    private long activeUsers;
    private long sentToday;
    private long trashCount;
    private long deletedCount;
}
