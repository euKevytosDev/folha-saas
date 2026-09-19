package com.sacolao.media;

import com.sacolao.support.AuthApi;
import com.sacolao.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MediaIT extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void mediaConfigReportsDisabledWithoutCredentials() throws Exception {
        var registered = AuthApi.register(mockMvc, "Loja Media", "Media", AuthApi.uniqueEmail("media"), "senha12345");
        String token = AuthApi.accessToken(registered);

        mockMvc.perform(get("/api/v1/media/config")
                        .header("Authorization", AuthApi.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void uploadFailsGracefullyWhenDisabled() throws Exception {
        var registered = AuthApi.register(mockMvc, "Loja Upload", "Up", AuthApi.uniqueEmail("upload"), "senha12345");
        String token = AuthApi.accessToken(registered);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "tomato.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
        );

        mockMvc.perform(multipart("/api/v1/media/upload")
                        .file(file)
                        .header("Authorization", AuthApi.bearer(token)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MEDIA_DISABLED"));
    }
}
