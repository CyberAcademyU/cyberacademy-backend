package com.cyberacademy.domains.module.service;

import com.cyberacademy.domains.module.dto.PublicModuleResponse;
import com.cyberacademy.domains.module.model.CourseModule;
import com.cyberacademy.domains.module.model.ModuleSection;
import com.cyberacademy.domains.module.model.SectionType;
import com.cyberacademy.domains.module.repository.CourseModuleRepository;
import com.cyberacademy.domains.module.repository.ModuleSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Catálogo público: lo que cualquiera puede ver antes de iniciar sesión. */
@Service
public class CatalogService {

    private final CourseModuleRepository moduleRepository;
    private final ModuleSectionRepository sectionRepository;

    public CatalogService(CourseModuleRepository moduleRepository, ModuleSectionRepository sectionRepository) {
        this.moduleRepository = moduleRepository;
        this.sectionRepository = sectionRepository;
    }

    @Transactional(readOnly = true)
    public List<PublicModuleResponse> listPublicModules() {
        List<CourseModule> modules = moduleRepository.findByActiveTrueOrderByOrderIndexAsc();
        if (modules.isEmpty()) return List.of();

        // Una sola consulta para las secciones de todos los módulos
        Map<UUID, List<String>> topicsByModule = sectionRepository
                .findByModuleIdIn(modules.stream().map(CourseModule::getId).toList()).stream()
                .filter(section -> section.getSectionType() == SectionType.THEORY)
                .sorted(Comparator.comparingInt(ModuleSection::getSectionOrder))
                .collect(Collectors.groupingBy(section -> section.getModule().getId(),
                        Collectors.mapping(ModuleSection::getTitle, Collectors.toList())));

        return modules.stream()
                .map(module -> new PublicModuleResponse(
                        module.getCode(),
                        module.getTitle(),
                        module.getDescription(),
                        module.getCover(),
                        module.getOrderIndex(),
                        module.getPassingScore(),
                        topicsByModule.getOrDefault(module.getId(), List.of())))
                .toList();
    }
}
