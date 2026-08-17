package com.studentleague.notifications;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private final List<PushNotificationProvider> providers;

    public NotificationService(List<PushNotificationProvider> providers) {
        this.providers = providers;
    }

    @Async
    public void publish(NotificationPayload payload) {
        for (PushNotificationProvider provider : providers) {
            provider.send(payload);
        }
    }

    @Async
    public void publishToUser(
            UUID userId,
            NotificationEventType type,
            String title,
            String body,
            Map<String, String> data
    ) {
        publish(new NotificationPayload(type, userId, title, body, data));
    }
}
