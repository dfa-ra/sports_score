package com.studentleague.notifications;

import java.util.Map;
import java.util.UUID;

public record NotificationPayload(
        NotificationEventType type,
        UUID userId,
        String title,
        String body,
        Map<String, String> data
) {
}
