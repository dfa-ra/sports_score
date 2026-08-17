package com.studentleague.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stub FCM provider. Enable with app.push.fcm.enabled=true and wire real credentials later.
 */
@Component
@ConditionalOnProperty(name = "app.push.fcm.enabled", havingValue = "true")
public class FcmPushNotificationProvider implements PushNotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(FcmPushNotificationProvider.class);

    @Override
    public String name() {
        return "fcm";
    }

    @Override
    public void send(NotificationPayload payload) {
        log.info("FCM stub send: user={} title={}", payload.userId(), payload.title());
    }
}
