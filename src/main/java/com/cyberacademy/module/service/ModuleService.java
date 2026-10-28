package com.cyberacademy.module.service;

import com.cyberacademy.auth.entity.User;
import com.cyberacademy.auth.repository.UserRepository;
import com.cyberacademy.common.exception.BadRequestException;
import com.cyberacademy.common.exception.ConflictException;
import com.cyberacademy.common.exception.ResourceNotFoundException;
import com.cyberacademy.module.dto.*;
import com.cyberacademy.module.entity.*;
import com.cyberacademy.module.repository.CourseModuleRepository;
import com.cyberacademy.module.repository.ModuleSectionRepository;
import com.cyberacademy.module.repository.SectionQuestionRepository;
import com.cyberacademy.module.repository.StudentModuleProgressRepository;
import com.cyberacademy.module.repository.StudentSectionProgressRepository;
import com.cyberacademy.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ModuleService {

    private static final BigDecimal TOTAL_WEIGHT = new BigDecimal("100.00");

    /** Orden de presentación: teoría 1..3 y después la práctica. */
    private static final Comparator<ModuleSection> SECTION_ORDER = Comparator
            .comparingInt((ModuleSection s) -> s.getSectionType() == SectionType.THEORY ? 0 : 1)
            .thenComparingInt(ModuleSection::getSectionOrder);

    private final CourseModuleRepository moduleRepository;
    private final ModuleSectionRepository sectionRepository;
    private final StudentModuleProgressRepository moduleProgressRepository;
    private final StudentSectionProgressRepository sectionProgressRepository;
    private final SectionQuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public ModuleService(CourseModuleRepository moduleRepository,
                         ModuleSectionRepository sectionRepository,
                         StudentModuleProgressRepository moduleProgressRepository,
                         StudentSectionProgressRepository sectionProgressRepository,
                         SectionQuestionRepository questionRepository,
                         UserRepository userRepository,
                         CurrentUserProvider currentUserProvider) {
        this.moduleRepository = moduleRepository;
        this.sectionRepository = sectionRepository;
        this.moduleProgressRepository = moduleProgressRepository;
        this.sectionProgressRepository = sectionProgressRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    // =========================================================================
    // Catálogo de módulos
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ModuleResponse> listModules(boolean includeInactive) {
        List<CourseModule> modules = includeInactive
                ? moduleRepository.findAllByOrderByOrderIndexAsc()
                : moduleRepository.findByActiveTrueOrderByOrderIndexAsc();

        if (modules.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<ModuleSection>> sectionsByModule =
                loadSections(modules.stream().map(CourseModule::getId).toList());

        return modules.stream()
                .map(module -> toModuleResponse(module, sectionsByModule.getOrDefault(module.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ModuleResponse getModule(UUID moduleId) {
        CourseModule module = findModule(moduleId);
        return toModuleResponse(module, sortedSections(moduleId));
    }

    @Transactional
    public ModuleResponse createModule(CreateModuleRequest request) {
        String code = request.getCode().trim().toUpperCase(Locale.ROOT);

        if (moduleRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Ya existe un módulo con el código " + code);
        }

        CourseModule module = new CourseModule();
        module.setCode(code);
        module.setTitle(request.getTitle().trim());
        module.setDescription(request.getDescription());
        if (request.getOrderIndex() != null) {
            module.setOrderIndex(request.getOrderIndex());
        }
        if (request.getPassingScore() != null) {
            module.setPassingScore(request.getPassingScore());
        }

        // El trigger trg_modules_default_sections crea las 3 teorías + 1 práctica
        moduleRepository.saveAndFlush(module);

        return toModuleResponse(module, sortedSections(module.getId()));
    }

    @Transactional
    public ModuleResponse updateModule(UUID moduleId, UpdateModuleRequest request) {
        CourseModule module = findModule(moduleId);

        if (request.getTitle() != null) {
            module.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            module.setDescription(request.getDescription());
        }
        if (request.getOrderIndex() != null) {
            module.setOrderIndex(request.getOrderIndex());
        }
        if (request.getPassingScore() != null) {
            module.setPassingScore(request.getPassingScore());
        }
        if (request.getActive() != null) {
            module.setActive(request.getActive());
        }
        module.setUpdatedAt(OffsetDateTime.now());

        moduleRepository.save(module);
        return toModuleResponse(module, sortedSections(moduleId));
    }

    @Transactional
    public SectionResponse updateSection(UUID moduleId, UUID sectionId, UpdateSectionRequest request) {
        findModule(moduleId);

        if (request.getTitle() == null && request.getWeightPercent() == null) {
            throw new BadRequestException("Debes enviar al menos el título o el peso de la sección");
        }

        ModuleSection section = sectionRepository.findByIdAndModuleId(sectionId, moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("La sección no pertenece a este módulo"));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            section.setTitle(request.getTitle().trim());
        }

        if (request.getWeightPercent() != null) {
            section.setWeightPercent(request.getWeightPercent());

            BigDecimal total = sectionRepository.findByModuleId(moduleId).stream()
                    .map(ModuleSection::getWeightPercent)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (total.compareTo(TOTAL_WEIGHT) != 0) {
                throw new BadRequestException(
                        "Los pesos de las 4 secciones deben sumar 100. Suma resultante: "
                                + total.stripTrailingZeros().toPlainString());
            }
        }

        section.setUpdatedAt(OffsetDateTime.now());
        sectionRepository.save(section);

        return toSectionResponse(section);
    }

    /**
     * Actualiza los pesos de todas las secciones de un módulo en una sola operación.
     * La base de datos recalcula automáticamente las notas ya registradas (V4).
     */
    @Transactional
    public List<SectionResponse> updateSectionWeights(UUID moduleId, UpdateWeightsRequest request) {
        findModule(moduleId);

        List<ModuleSection> sections = sectionRepository.findByModuleId(moduleId);
        Map<UUID, ModuleSection> byId = sections.stream()
                .collect(Collectors.toMap(ModuleSection::getId, section -> section));

        if (request.getWeights().size() != sections.size()) {
            throw new BadRequestException(
                    "Debes enviar el peso de las " + sections.size() + " secciones del módulo");
        }

        Set<UUID> received = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;

        for (UpdateWeightsRequest.SectionWeight item : request.getWeights()) {
            ModuleSection section = byId.get(item.getSectionId());
            if (section == null) {
                throw new BadRequestException(
                        "La sección " + item.getSectionId() + " no pertenece a este módulo");
            }
            if (!received.add(item.getSectionId())) {
                throw new BadRequestException("La sección " + item.getSectionId() + " está repetida");
            }

            section.setWeightPercent(item.getWeightPercent());
            section.setUpdatedAt(OffsetDateTime.now());
            total = total.add(item.getWeightPercent());
        }

        if (total.compareTo(TOTAL_WEIGHT) != 0) {
            throw new BadRequestException("Los pesos deben sumar 100. Suma enviada: "
                    + total.stripTrailingZeros().toPlainString());
        }

        sectionRepository.saveAll(sections);

        return sections.stream().sorted(SECTION_ORDER).map(this::toSectionResponse).toList();
    }

    // =========================================================================
    // Progreso
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ModuleSummaryResponse> listMyProgress() {
        return listProgressFor(currentUserProvider.requireCurrentUser());
    }

    @Transactional(readOnly = true)
    public List<ModuleSummaryResponse> listStudentProgress(UUID userId) {
        User student = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Estudiante no encontrado"));
        return listProgressFor(student);
    }

    @Transactional(readOnly = true)
    public ModuleProgressResponse getMyProgress(UUID moduleId) {
        return buildProgress(moduleId, currentUserProvider.requireCurrentUser());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private List<ModuleSummaryResponse> listProgressFor(User student) {
        List<StudentModuleProgress> progresses = moduleProgressRepository.findByUserId(student.getId());
        Map<UUID, StudentModuleProgress> progressByModule = progresses.stream()
                .collect(Collectors.toMap(p -> p.getModule().getId(), p -> p));

        // Se muestran los módulos activos y también los inactivos donde el estudiante
        // ya tiene historial, para no ocultarle notas pasadas.
        List<CourseModule> modules = moduleRepository.findAllByOrderByOrderIndexAsc().stream()
                .filter(module -> module.isActive() || progressByModule.containsKey(module.getId()))
                .toList();

        if (modules.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<ModuleSection>> sectionsByModule =
                loadSections(modules.stream().map(CourseModule::getId).toList());

        Map<UUID, List<StudentSectionProgress>> itemsByProgress = progresses.isEmpty()
                ? Map.of()
                : sectionProgressRepository
                    .findByModuleProgressIdIn(progresses.stream().map(StudentModuleProgress::getId).toList())
                    .stream()
                    .collect(Collectors.groupingBy(sp -> sp.getModuleProgress().getId()));

        return modules.stream()
                .map(module -> {
                    StudentModuleProgress progress = progressByModule.get(module.getId());
                    List<StudentSectionProgress> items = progress == null
                            ? List.of()
                            : itemsByProgress.getOrDefault(progress.getId(), List.of());
                    return toSummary(module, sectionsByModule.getOrDefault(module.getId(), List.of()), progress, items);
                })
                .toList();
    }

    private ModuleProgressResponse buildProgress(UUID moduleId, User student) {
        CourseModule module = findModule(moduleId);
        List<ModuleSection> sections = sortedSections(moduleId);

        StudentModuleProgress progress = moduleProgressRepository
                .findByUserIdAndModuleId(student.getId(), moduleId)
                .orElse(null);

        List<StudentSectionProgress> items = progress == null
                ? List.of()
                : sectionProgressRepository.findByModuleProgressId(progress.getId());

        return new ModuleProgressResponse(toSummary(module, sections, progress, items), toSectionProgress(sections, items));
    }

    private ModuleSummaryResponse toSummary(CourseModule module,
                                            List<ModuleSection> sections,
                                            StudentModuleProgress progress,
                                            List<StudentSectionProgress> items) {
        Map<UUID, StudentSectionProgress> bySection = items.stream()
                .collect(Collectors.toMap(sp -> sp.getSection().getId(), sp -> sp, (a, b) -> a));

        long completedSections = 0;

        for (ModuleSection section : sections) {
            StudentSectionProgress item = bySection.get(section.getId());
            if (item != null && item.getStatus() == ProgressStatus.COMPLETED) {
                completedSections++;
            }
        }

        // Cada sesión cuenta igual: 3 teorías = 75% y la práctica = 100%.
        BigDecimal progressPercent = sections.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(completedSections)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(sections.size()), 2, RoundingMode.HALF_UP);

        ProgressStatus status = progress == null ? ProgressStatus.NOT_STARTED : progress.getStatus();
        BigDecimal score = progress == null ? null : progress.getScore();

        return new ModuleSummaryResponse(
                module.getId(),
                module.getCode(),
                module.getTitle(),
                module.getDescription(),
                module.getOrderIndex(),
                status,
                score,
                module.getPassingScore(),
                sections.size(),
                completedSections,
                progressPercent,
                status == ProgressStatus.COMPLETED,
                progress == null ? null : progress.getStartedAt(),
                progress == null ? null : progress.getCompletedAt());
    }

    private List<SectionProgressResponse> toSectionProgress(List<ModuleSection> sections,
                                                            List<StudentSectionProgress> items) {
        Map<UUID, StudentSectionProgress> bySection = items.stream()
                .collect(Collectors.toMap(sp -> sp.getSection().getId(), sp -> sp, (a, b) -> a));

        // Si la práctica ya está completada, el módulo terminó y todo queda bloqueado.
        boolean moduleFinished = sections.stream()
                .filter(section -> section.getSectionType() == SectionType.PRACTICE)
                .map(section -> bySection.get(section.getId()))
                .anyMatch(item -> item != null && item.getStatus() == ProgressStatus.COMPLETED);

        List<SectionProgressResponse> result = new ArrayList<>();
        boolean allPreviousCompleted = true;

        for (ModuleSection section : sections) {
            StudentSectionProgress item = bySection.get(section.getId());
            ProgressStatus status = item == null ? ProgressStatus.NOT_STARTED : item.getStatus();
            boolean completed = status == ProgressStatus.COMPLETED;

            // Solo la primera sesión pendiente está abierta (orden estricto).
            boolean accessible = !moduleFinished && !completed && allPreviousCompleted;
            boolean locked = !accessible && !completed;

            result.add(new SectionProgressResponse(
                    section.getId(),
                    section.getSectionType(),
                    section.getSectionOrder(),
                    section.getTitle(),
                    section.getWeightPercent(),
                    status,
                    item == null ? null : item.getScore(),
                    item == null ? 0 : item.getAttempts(),
                    item == null ? null : item.getCompletedAt(),
                    accessible,
                    locked,
                    (int) questionRepository.countBySectionId(section.getId())));

            allPreviousCompleted = allPreviousCompleted && completed;
        }

        return result;
    }

    private ModuleResponse toModuleResponse(CourseModule module, List<ModuleSection> sections) {
        return new ModuleResponse(
                module.getId(),
                module.getCode(),
                module.getTitle(),
                module.getDescription(),
                module.getOrderIndex(),
                module.getPassingScore(),
                module.isActive(),
                sections.stream().map(this::toSectionResponse).toList());
    }

    private SectionResponse toSectionResponse(ModuleSection section) {
        return new SectionResponse(
                section.getId(),
                section.getSectionType(),
                section.getSectionOrder(),
                section.getTitle(),
                section.getWeightPercent());
    }

    private CourseModule findModule(UUID moduleId) {
        return moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Módulo no encontrado"));
    }

    private List<ModuleSection> sortedSections(UUID moduleId) {
        return sectionRepository.findByModuleId(moduleId).stream()
                .sorted(SECTION_ORDER)
                .toList();
    }

    private Map<UUID, List<ModuleSection>> loadSections(List<UUID> moduleIds) {
        Map<UUID, List<ModuleSection>> grouped = sectionRepository.findByModuleIdIn(moduleIds).stream()
                .collect(Collectors.groupingBy(section -> section.getModule().getId()));

        Map<UUID, List<ModuleSection>> sorted = new HashMap<>();
        grouped.forEach((moduleId, sections) ->
                sorted.put(moduleId, sections.stream().sorted(SECTION_ORDER).toList()));
        return sorted;
    }
}
