package com.cyberacademy.domains.profile.controller;

import com.cyberacademy.common.ApiResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Order(0)
@RestControllerAdvice
public class ProfilePhotoErrors {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> tooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("La foto supera el límite de 2 MB."));
    }
}
