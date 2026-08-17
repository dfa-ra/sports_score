package com.studentleague.notifications;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private final List<PushNotificationProvider> providers;

    public NotificationService(List<PushNotificationProvider> providers) {
        this.providers = providers;
    }

    public void publish(NotificationPayload payload) {
        for (PushNotificationProvider provider : providers) {
            provider.send(payload);
        }
    }
}
