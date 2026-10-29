package com.ollisante.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MediaServiceTest {

    @Test
    void recognisesImagesByContent() {
        assertEquals("jpg", MediaService.imageExtension(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}));
        assertEquals("png", MediaService.imageExtension(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}));
        assertEquals("webp", MediaService.imageExtension("RIFF\0\0\0\0WEBP".getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    void rejectsOtherContent() {
        assertNull(MediaService.imageExtension("<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8)));
        assertNull(MediaService.imageExtension(new byte[0]));
    }

    @Test
    void onlyGeneratedNamesCanBeRead() {
        MediaService service = new MediaService("target/test-media");
        assertFalse(service.find("../../etc/passwd").isPresent());
        assertFalse(service.find("photo.jpg").isPresent());
    }
}
