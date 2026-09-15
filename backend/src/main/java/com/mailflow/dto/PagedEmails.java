package com.mailflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedEmails {
    private List<EmailDto> emails;
    private long total;
    private int page;
    private int pages;
    private Long unreadCount; // only populated for /inbox
}
