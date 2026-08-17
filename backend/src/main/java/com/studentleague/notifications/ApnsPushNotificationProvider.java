package com.studentleague.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stub APNs provider. Enable with app.push.apns.enabled=true and wire real credentials later.
 */
@Component
@ConditionalOnProperty(name = "app.push.apns.enabled", havingValue = "true")
public class ApnsPushNotificationProvider implements PushNotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(ApnsPushNotificationProvider.class);

    @Override
    public String name() {
        return "apns";
    }

    @Override
    public void send(NotificationPayload payload) {
        log.info("APNs stub send: user={} title={}", payload.userId(), payload.title());
    }
}
