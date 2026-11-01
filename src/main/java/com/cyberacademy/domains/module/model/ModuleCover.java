package com.cyberacademy.domains.module.model;

/**
 * Catálogo cerrado de portadas de módulo. Cada clave es una ilustración incluida en el
 * frontend (shared/ui/module-cover.ts): no hay subida de imágenes.
 */
public final class ModuleCover {

    /** Valores admitidos; vacío = sin portada elegida (el frontend usa una abstracta). */
    public static final String PATTERN =
            "^$|^(intro|inbox|password|profile|photo|wifi|challenge|exam|prism|tunnel)$";

    private ModuleCover() {}

    public static String normalize(String cover) {
        return cover == null || cover.isBlank() ? null : cover.trim();
    }
}
