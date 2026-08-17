package com.studentleague.notifications;

/**
 * Abstraction over push providers (FCM / APNs). Implementations can be swapped without
 * changing domain services that emit notifications.
 */
public interface PushNotificationProvider {
    String name();

    void send(NotificationPayload payload);
}
