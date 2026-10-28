package com.cyberacademy.module.service;

import com.cyberacademy.auth.entity.User;
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
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Flujo de aprendizaje guiado:
 * teorías con pregunta de comprensión (se desbloquean en orden) y una práctica
 * tipo cuestionario de un solo intento cuya nota es la del módulo.
 */
@Service
public class QuizService {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final int MAX_PRACTICE_QUESTIONS = 50;

    /** Orden de presentación: teoría 1..3 y después la práctica. */
    private static final Comparator<ModuleSection> SECTION_ORDER = Comparator
            .comparingInt((ModuleSection s) -> s.getSectionType() == SectionType.THEORY ? 0 : 1)
            .thenComparingInt(ModuleSection::getSectionOrder);

    private final CourseModuleRepository moduleRepository;
    private final ModuleSectionRepository sectionRepository;
    private final StudentModuleProgressRepository moduleProgressRepository;
    private final StudentSectionProgressRepository sectionProgressRepository;
    private final SectionQuestionRepository questionRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ModuleService moduleService;
    private final EntityManager entityManager;

    public QuizService(CourseModuleRepository moduleRepository,
                       ModuleSectionRepository sectionRepository,
                       StudentModuleProgressRepository moduleProgressRepository,
                       StudentSectionProgressRepository sectionProgressRepository,
                       SectionQuestionRepository questionRepository,
                       CurrentUserProvider currentUserProvider,
                       ModuleService moduleService,
                       EntityManager entityManager) {
        this.moduleRepository = moduleRepository;
        this.sectionRepository = sectionRepository;
        this.moduleProgressRepository = moduleProgressRepository;
        this.sectionProgressRepository = sectionProgressRepository;
        this.questionRepository = questionRepository;
        this.currentUserProvider = currentUserProvider;
        this.moduleService = moduleService;
        this.entityManager = entityManager;
    }

    // =========================================================================
    // Estudiante
    // =========================================================================

    /** Devuelve la sesión desbloqueada con su contenido y sus preguntas (sin respuestas). */
    @Transactional(readOnly = true)
    public SectionContentResponse getStudentSection(UUID moduleId, UUID sectionId) {
        User student = currentUserProvider.requireCurrentUser();
        findModule(moduleId);

        List<ModuleSection> sections = sortedSections(moduleId);
        ModuleSection section = findSection(sections, sectionId);

        StudentModuleProgress progress = moduleProgressRepository
                .findByUserIdAndModuleId(student.getId(), moduleId)
                .orElse(null);
        List<StudentSectionProgress> items = progress == null
                ? List.of()
                : sectionProgressRepository.findByModuleProgressId(progress.getId());

        Access access = evaluate(sections, items, section);
        if (!access.accessible()) {
            throw new ConflictException(access.reason());
        }

        List<SectionQuestion> questions =
                questionRepository.findBySectionIdOrderByOrderIndexAsc(sectionId);

        return new SectionContentResponse(
                section.getId(),
                section.getSectionType(),
                section.getSectionOrder(),
                section.getTitle(),
                section.getContent(),
                access.status(),
                true,
                toStudentQuestions(questions));
    }

    /** Comprueba la pregunta de comprensión de una teoría y la completa si es correcta. */
    @Transactional
    public CheckAnswerResponse checkTheoryAnswer(UUID moduleId, UUID sectionId, CheckAnswerRequest request) {
        User student = currentUserProvider.requireCurrentUser();
        CourseModule module = findModule(moduleId);

        List<ModuleSection> sections = sortedSections(moduleId);
        ModuleSection section = findSection(sections, sectionId);

        if (section.getSectionType() != SectionType.THEORY) {
            throw new BadRequestException("Solo las sesiones de teoría tienen pregunta de comprensión");
        }

        StudentModuleProgress progress = getOrCreateProgress(student, module);
        List<StudentSectionProgress> items =
                sectionProgressRepository.findByModuleProgressId(progress.getId());

        Access access = evaluate(sections, items, section);
        if (!access.accessible()) {
            throw new ConflictException(access.reason());
        }

        SectionQuestion question = questionRepository.findById(request.getQuestionId())
                .filter(q -> q.getSection().getId().equals(sectionId))
                .orElseThrow(() -> new BadRequestException("La pregunta no pertenece a esta sesión"));

        QuestionOption option = question.getOptions().stream()
                .filter(o -> o.getId().equals(request.getOptionId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("La opción no pertenece a esta pregunta"));

        StudentSectionProgress item = items.stream()
                .filter(sp -> sp.getSection().getId().equals(sectionId))
                .findFirst()
                .orElseGet(() -> {
                    StudentSectionProgress created = new StudentSectionProgress();
                    created.setModuleProgress(progress);
                    created.setSection(section);
                    return created;
                });

        item.setAttempts(item.getAttempts() + 1);

        if (!option.isCorrect()) {
            if (item.getStatus() != ProgressStatus.COMPLETED) {
                item.setStatus(ProgressStatus.IN_PROGRESS);
            }
            sectionProgressRepository.saveAndFlush(item);
            return new CheckAnswerResponse(
                    false,
                    false,
                    "Respuesta incorrecta. Revisa el contenido e inténtalo de nuevo.");
        }

        item.setStatus(ProgressStatus.COMPLETED);
        item.setCompletedAt(OffsetDateTime.now());
        item.setScore(null); // las teorías no llevan nota
        sectionProgressRepository.saveAndFlush(item);

        // El trigger de la BD ya recalculó el módulo; refrescamos para leer el estado real.
        entityManager.refresh(progress);

        return new CheckAnswerResponse(
                true,
                true,
                "¡Respuesta correcta! Se desbloqueó la siguiente sesión.");
    }

    /** Corrige la práctica. Solo se permite un intento. */
    @Transactional
    public ModuleProgressResponse submitPractice(UUID moduleId, SubmitPracticeRequest request) {
        User student = currentUserProvider.requireCurrentUser();
        CourseModule module = findModule(moduleId);

        List<ModuleSection> sections = sortedSections(moduleId);
        ModuleSection practice = sections.stream()
                .filter(s -> s.getSectionType() == SectionType.PRACTICE)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("El módulo no tiene sesión de práctica"));

        StudentModuleProgress progress = getOrCreateProgress(student, module);
        List<StudentSectionProgress> items =
                sectionProgressRepository.findByModuleProgressId(progress.getId());
        Map<UUID, StudentSectionProgress> bySection = items.stream()
                .collect(Collectors.toMap(sp -> sp.getSection().getId(), sp -> sp, (a, b) -> a));

        boolean theoriesCompleted = sections.stream()
                .filter(s -> s.getSectionType() == SectionType.THEORY)
                .allMatch(s -> {
                    StudentSectionProgress item = bySection.get(s.getId());
                    return item != null && item.getStatus() == ProgressStatus.COMPLETED;
                });
        if (!theoriesCompleted) {
            throw new ConflictException(
                    "Debes completar las 3 sesiones de teoría antes de enviar la práctica");
        }

        StudentSectionProgress practiceItem = bySection.get(practice.getId());
        if (practiceItem != null && practiceItem.getStatus() == ProgressStatus.COMPLETED) {
            throw new ConflictException("La práctica ya fue enviada. Solo se permite un intento");
        }

        List<SectionQuestion> questions =
                questionRepository.findBySectionIdOrderByOrderIndexAsc(practice.getId());
        if (questions.isEmpty()) {
            throw new BadRequestException("La práctica todavía no tiene preguntas configuradas");
        }

        Map<UUID, UUID> answers = request.getAnswers().stream()
                .collect(Collectors.toMap(
                        PracticeAnswerRequest::getQuestionId,
                        PracticeAnswerRequest::getOptionId,
                        (a, b) -> a));

        if (answers.size() != questions.size()) {
            throw new BadRequestException("Debes responder todas las preguntas de la práctica");
        }

        int correctCount = 0;
        for (SectionQuestion question : questions) {
            UUID optionId = answers.get(question.getId());
            if (optionId == null) {
                throw new BadRequestException("Debes responder todas las preguntas de la práctica");
            }
            QuestionOption option = question.getOptions().stream()
                    .filter(o -> o.getId().equals(optionId))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException(
                            "Una de las opciones enviadas no pertenece a su pregunta"));
            if (option.isCorrect()) {
                correctCount++;
            }
        }

        BigDecimal score = BigDecimal.valueOf(correctCount)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(questions.size()), 2, RoundingMode.HALF_UP);

        StudentSectionProgress item = practiceItem != null ? practiceItem : new StudentSectionProgress();
        item.setModuleProgress(progress);
        item.setSection(practice);
        item.setStatus(ProgressStatus.COMPLETED);
        item.setScore(score);
        item.setAttempts(item.getAttempts() + 1);
        item.setCompletedAt(OffsetDateTime.now());
        sectionProgressRepository.saveAndFlush(item);

        // La nota del módulo la calcula la BD: al ser la práctica la única sección
        // con nota, la fórmula ponderada devuelve exactamente esta puntuación.
        entityManager.refresh(progress);

        return moduleService.getMyProgress(moduleId);
    }

    // =========================================================================
    // Administrador
    // =========================================================================

    /** Contenido y preguntas de las 4 secciones, con las respuestas correctas. */
    @Transactional(readOnly = true)
    public List<AdminSectionContentResponse> getModuleContent(UUID moduleId) {
        findModule(moduleId);

        return sortedSections(moduleId).stream()
                .map(section -> toAdminResponse(
                        section,
                        questionRepository.findBySectionIdOrderByOrderIndexAsc(section.getId())))
                .toList();
    }

    /** Reemplaza el contenido y las preguntas de una sesión. */
    @Transactional
    public AdminSectionContentResponse saveSectionContent(UUID moduleId,
                                                          UUID sectionId,
                                                          SaveSectionContentRequest request) {
        findModule(moduleId);

        ModuleSection section = sectionRepository.findByIdAndModuleId(sectionId, moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("La sección no pertenece a este módulo"));

        String title = request.getTitle() == null ? "" : request.getTitle().trim();
        if (title.isEmpty()) {
            throw new BadRequestException("El título de la sesión es obligatorio");
        }

        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            throw new BadRequestException("El contenido de la sesión es obligatorio");
        }

        List<SaveSectionContentRequest.QuestionInput> questions = request.getQuestions();
        if (questions == null) {
            throw new BadRequestException("Debes enviar las preguntas de la sesión");
        }

        if (section.getSectionType() == SectionType.THEORY && questions.size() != 1) {
            throw new BadRequestException(
                    "Cada teoría debe tener exactamente una pregunta de comprensión");
        }
        if (section.getSectionType() == SectionType.PRACTICE
                && (questions.isEmpty() || questions.size() > MAX_PRACTICE_QUESTIONS)) {
            throw new BadRequestException(
                    "La práctica debe tener entre 1 y " + MAX_PRACTICE_QUESTIONS + " preguntas");
        }

        for (SaveSectionContentRequest.QuestionInput question : questions) {
            if (question.getOptions() == null) {
                throw new BadRequestException("Cada pregunta debe tener opciones");
            }
            long correctOptions = question.getOptions().stream()
                    .filter(SaveSectionContentRequest.OptionInput::isCorrect)
                    .count();
            if (correctOptions != 1) {
                throw new BadRequestException(
                        "Cada pregunta debe tener exactamente una opción correcta");
            }
        }

        section.setTitle(title);
        section.setContent(content);
        section.setUpdatedAt(OffsetDateTime.now());
        sectionRepository.save(section);

        // Reemplazo completo: primero se borran las preguntas anteriores y sus opciones.
        questionRepository.deleteBySectionId(sectionId);

        int questionOrder = 1;
        for (SaveSectionContentRequest.QuestionInput input : questions) {
            SectionQuestion question = new SectionQuestion();
            question.setSection(section);
            question.setOrderIndex(questionOrder++);
            question.setPrompt(input.getPrompt().trim());

            int optionOrder = 1;
            for (SaveSectionContentRequest.OptionInput optionInput : input.getOptions()) {
                QuestionOption option = new QuestionOption();
                option.setQuestion(question);
                option.setOrderIndex(optionOrder++);
                option.setText(optionInput.getText().trim());
                option.setCorrect(optionInput.isCorrect());
                question.getOptions().add(option);
            }

            questionRepository.save(question);
        }

        questionRepository.flush();

        return toAdminResponse(
                section,
                questionRepository.findBySectionIdOrderByOrderIndexAsc(sectionId));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private record Access(boolean accessible, ProgressStatus status, String reason) {}

    private Access evaluate(List<ModuleSection> ordered,
                            List<StudentSectionProgress> items,
                            ModuleSection target) {
        Map<UUID, StudentSectionProgress> bySection = items.stream()
                .collect(Collectors.toMap(sp -> sp.getSection().getId(), sp -> sp, (a, b) -> a));

        boolean moduleFinished = ordered.stream()
                .filter(s -> s.getSectionType() == SectionType.PRACTICE)
                .map(s -> bySection.get(s.getId()))
                .anyMatch(sp -> sp != null && sp.getStatus() == ProgressStatus.COMPLETED);

        boolean allPreviousCompleted = true;
        for (ModuleSection section : ordered) {
            StudentSectionProgress item = bySection.get(section.getId());
            ProgressStatus status = item == null ? ProgressStatus.NOT_STARTED : item.getStatus();

            if (section.getId().equals(target.getId())) {
                if (moduleFinished) {
                    return new Access(false, status,
                            "El módulo ya está finalizado y sus sesiones están bloqueadas");
                }
                if (status == ProgressStatus.COMPLETED) {
                    return new Access(false, status,
                            "Esta sesión ya está completada y bloqueada");
                }
                if (!allPreviousCompleted) {
                    return new Access(false, status,
                            "Debes completar las sesiones anteriores antes de abrir esta");
                }
                return new Access(true, status, null);
            }

            allPreviousCompleted = allPreviousCompleted && status == ProgressStatus.COMPLETED;
        }

        return new Access(false, ProgressStatus.NOT_STARTED, "La sesión no pertenece a este módulo");
    }

    private StudentModuleProgress getOrCreateProgress(User student, CourseModule module) {
        return moduleProgressRepository.findByUserIdAndModuleId(student.getId(), module.getId())
                .orElseGet(() -> {
                    StudentModuleProgress created = new StudentModuleProgress();
                    created.setUser(student);
                    created.setModule(module);
                    return moduleProgressRepository.saveAndFlush(created);
                });
    }

    private List<QuizQuestionResponse> toStudentQuestions(List<SectionQuestion> questions) {
        return questions.stream()
                .map(question -> new QuizQuestionResponse(
                        question.getId(),
                        question.getOrderIndex(),
                        question.getPrompt(),
                        question.getOptions().stream()
                                .sorted(Comparator.comparingInt(QuestionOption::getOrderIndex))
                                .map(option -> new QuizOptionResponse(
                                        option.getId(),
                                        option.getOrderIndex(),
                                        option.getText()))
                                .toList()))
                .toList();
    }

    private AdminSectionContentResponse toAdminResponse(ModuleSection section,
                                                        List<SectionQuestion> questions) {
        List<AdminQuestionResponse> mapped = questions.stream()
                .map(question -> new AdminQuestionResponse(
                        question.getId(),
                        question.getOrderIndex(),
                        question.getPrompt(),
                        question.getOptions().stream()
                                .sorted(Comparator.comparingInt(QuestionOption::getOrderIndex))
                                .map(option -> new AdminOptionResponse(
                                        option.getId(),
                                        option.getOrderIndex(),
                                        option.getText(),
                                        option.isCorrect()))
                                .toList()))
                .toList();

        return new AdminSectionContentResponse(
                section.getId(),
                section.getSectionType(),
                section.getSectionOrder(),
                section.getTitle(),
                section.getContent(),
                mapped);
    }

    private CourseModule findModule(UUID moduleId) {
        return moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Módulo no encontrado"));
    }

    private ModuleSection findSection(List<ModuleSection> sections, UUID sectionId) {
        return sections.stream()
                .filter(section -> section.getId().equals(sectionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("La sección no pertenece a este módulo"));
    }

    private List<ModuleSection> sortedSections(UUID moduleId) {
        return sectionRepository.findByModuleId(moduleId).stream()
                .sorted(SECTION_ORDER)
                .toList();
    }
}
