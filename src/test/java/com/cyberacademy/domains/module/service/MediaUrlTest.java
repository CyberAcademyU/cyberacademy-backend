package com.cyberacademy.domains.module.service;

import com.cyberacademy.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MediaUrlTest {
    @Test void allowsHttpsAndEmptyValues() {
        assertEquals("https://example.com/image.jpg", QuizService.validateMediaUrl(" https://example.com/image.jpg "));
        assertNull(QuizService.validateMediaUrl(" "));
    }

    @Test void rejectsUnsafeOrMalformedUrls() {
        assertThrows(BadRequestException.class, () -> QuizService.validateMediaUrl("javascript:alert(1)"));
        assertThrows(BadRequestException.class, () -> QuizService.validateMediaUrl("http://example.com/video.mp4"));
        assertThrows(BadRequestException.class, () -> QuizService.validateMediaUrl("https://user:password@example.com/file"));
    }
}
