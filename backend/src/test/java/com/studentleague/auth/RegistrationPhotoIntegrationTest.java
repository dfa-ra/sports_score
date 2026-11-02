package com.studentleague.auth;

import com.studentleague.storage.ImageUploadsTest;
import com.studentleague.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistrationPhotoIntegrationTest extends AbstractIntegrationTest {

    @Test
    void anonymousValidPngIsUnauthorized() throws Exception {
        mockMvc.perform(multipart("/api/v1/auth/photo").file(png("e2e-avatar.png")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void oneTimeTicketAllowsASingleRegistrationUpload() throws Exception {
        String token = ticket();

        mockMvc.perform(multipart("/api/v1/auth/photo")
                        .file(png("player.png"))
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(startsWith("/media/registration/")));

        mockMvc.perform(multipart("/api/v1/auth/photo")
                        .file(png("player.png"))
                        .param("token", token))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(multipart("/api/v1/auth/photo")
                        .file(png("player.png"))
                        .header("X-Registration-Upload", ticket()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(startsWith("/media/registration/")));
    }

    @Test
    void signedInTextDisguisedAsJpegIsRejectedWithoutATicket() throws Exception {
        String access = registerAndLogin("photo-" + System.nanoTime() + "@example.com", "Str0ngPass!");
        mockMvc.perform(multipart("/api/v1/auth/photo")
                        .file(new MockMultipartFile(
                                "file", "avatar.jpg", "image/jpeg", "this is not a jpeg".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", auth(access)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("JPEG")));
    }

    private String ticket() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/photo-ticket"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private static MockMultipartFile png(String name) {
        return new MockMultipartFile("file", name, "image/png", ImageUploadsTest.PNG);
    }
}
