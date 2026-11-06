package com.cyberacademy.domains.module.service;

import com.cyberacademy.domains.module.dto.PublicModuleResponse;
import com.cyberacademy.domains.module.dto.PublicCatalogChangedEvent;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.cyberacademy.domains.module.model.CourseModule;
import com.cyberacademy.domains.module.model.ModuleSection;
import com.cyberacademy.domains.module.model.SectionType;
import com.cyberacademy.domains.module.repository.CourseModuleRepository;
import com.cyberacademy.domains.module.repository.ModuleSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/** Catálogo público: lo que cualquiera puede ver antes de iniciar sesión. */
@Service
public class CatalogService {

    private final CourseModuleRepository moduleRepository;
    private final ModuleSectionRepository sectionRepository;
    private final Cache<Long, List<PublicModuleResponse>> publicCatalog;
    private final AtomicLong revision = new AtomicLong();

    @Autowired
    public CatalogService(CourseModuleRepository moduleRepository, ModuleSectionRepository sectionRepository) {
        this(moduleRepository, sectionRepository, Ticker.systemTicker());
    }

    CatalogService(CourseModuleRepository moduleRepository, ModuleSectionRepository sectionRepository, Ticker ticker) {
        this.moduleRepository = moduleRepository;
        this.sectionRepository = sectionRepository;
        this.publicCatalog = Caffeine.newBuilder().maximumSize(1)
                .expireAfterWrite(Duration.ofSeconds(60)).ticker(ticker).build();
    }

    @Transactional(readOnly = true)
    public List<PublicModuleResponse> listPublicModules() {
        return publicCatalog.get(revision.get(), ignored -> loadPublicModules());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPublicCatalogChanged(PublicCatalogChangedEvent event) {
        revision.incrementAndGet();
        publicCatalog.invalidateAll();
    }

    private List<PublicModuleResponse> loadPublicModules() {
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
