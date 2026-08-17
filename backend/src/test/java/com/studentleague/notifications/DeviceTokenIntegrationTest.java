package com.studentleague.notifications;

import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeviceTokenIntegrationTest extends AbstractIntegrationTest {

    @Test
    void registerAndUnregisterDeviceToken() throws Exception {
        String token = registerAndLogin("device-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        String deviceToken = "device-token-" + System.nanoTime();

        mockMvc.perform(post("/api/v1/notifications/device-tokens")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"platform":"ANDROID","token":"%s"}
                                """.formatted(deviceToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/notifications/device-tokens")
                        .header("Authorization", auth(token))
                        .param("token", deviceToken))
                .andExpect(status().isNoContent());
    }
}
