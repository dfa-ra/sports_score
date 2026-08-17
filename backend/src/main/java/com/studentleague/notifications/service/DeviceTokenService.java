package com.studentleague.notifications.service;

import com.studentleague.notifications.dto.RegisterDeviceTokenRequest;
import com.studentleague.notifications.entity.DeviceToken;
import com.studentleague.notifications.repository.DeviceTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    public DeviceTokenService(DeviceTokenRepository deviceTokenRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
    }

    @Transactional
    public void register(UUID userId, RegisterDeviceTokenRequest request) {
        DeviceToken existing = deviceTokenRepository.findByToken(request.token()).orElse(null);
        if (existing != null) {
            existing.setUserId(userId);
            existing.setPlatform(request.platform());
            existing.touch();
            deviceTokenRepository.save(existing);
            return;
        }
        DeviceToken token = new DeviceToken();
        token.setUserId(userId);
        token.setPlatform(request.platform());
        token.setToken(request.token());
        deviceTokenRepository.save(token);
    }

    @Transactional
    public void unregister(UUID userId, String token) {
        deviceTokenRepository.deleteByUserIdAndToken(userId, token);
    }
}
