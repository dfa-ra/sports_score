package com.studentleague.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoOpPushNotificationProvider implements PushNotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(NoOpPushNotificationProvider.class);

    @Override
    public String name() {
        return "noop";
    }

    @Override
    public void send(NotificationPayload payload) {
        log.debug("Push skipped (noop): type={} user={} title={}",
                payload.type(), payload.userId(), payload.title());
    }
}
