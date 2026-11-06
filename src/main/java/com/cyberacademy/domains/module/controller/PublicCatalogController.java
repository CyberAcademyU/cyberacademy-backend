package com.cyberacademy.domains.module.controller;

import com.cyberacademy.common.ApiResponse;
import com.cyberacademy.domains.module.dto.PublicModuleResponse;
import com.cyberacademy.domains.module.service.CatalogService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Rutas sin sesión (/public/**): solo lectura y solo datos de presentación. */
@RestController
@RequestMapping("/public")
public class PublicCatalogController {

    private final CatalogService catalogService;

    public PublicCatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** Módulos activos para la página "Explorar". */
    @GetMapping("/modules")
    public ResponseEntity<ApiResponse<List<PublicModuleResponse>>> modules() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().cachePublic())
                .body(ApiResponse.ok("Catálogo obtenido correctamente", catalogService.listPublicModules()));
    }
}
