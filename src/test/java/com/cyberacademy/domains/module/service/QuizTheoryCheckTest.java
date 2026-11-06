package com.cyberacademy.domains.module.service;

import com.cyberacademy.domains.auth.model.User;
import com.cyberacademy.domains.module.dto.CheckAnswerRequest;
import com.cyberacademy.domains.module.dto.CheckAnswerResponse;
import com.cyberacademy.domains.module.model.CourseModule;
import com.cyberacademy.domains.module.model.ModuleSection;
import com.cyberacademy.domains.module.model.QuestionOption;
import com.cyberacademy.domains.module.model.SectionQuestion;
import com.cyberacademy.domains.module.model.SectionType;
import com.cyberacademy.domains.module.model.StudentModuleProgress;
import com.cyberacademy.domains.module.model.StudentSectionProgress;
import com.cyberacademy.domains.module.repository.CourseModuleRepository;
import com.cyberacademy.domains.module.repository.ModuleSectionRepository;
import com.cyberacademy.domains.module.repository.PracticeAnswerRepository;
import com.cyberacademy.domains.module.repository.SectionQuestionRepository;
import com.cyberacademy.domains.module.repository.StudentModuleProgressRepository;
import com.cyberacademy.domains.module.repository.StudentSectionProgressRepository;
import com.cyberacademy.security.CurrentUserProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La teoría se completa contestar bien o mal: fallar muestra la correcta y desbloquea la siguiente. */
class QuizTheoryCheckTest {

    private final CourseModuleRepository modules = mock(CourseModuleRepository.class);
    private final ModuleSectionRepository sectionRepository = mock(ModuleSectionRepository.class);
    private final StudentModuleProgressRepository moduleProgressRepository =
            mock(StudentModuleProgressRepository.class);
    private final StudentSectionProgressRepository sectionProgressRepository =
            mock(StudentSectionProgressRepository.class);
    private final SectionQuestionRepository questionRepository = mock(SectionQuestionRepository.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final ModuleService moduleService = mock(ModuleService.class);
    private final EntityManager entityManager = mock(EntityManager.class);

    private final UUID moduleId = UUID.randomUUID();
    private final UUID sectionId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();
    private final UUID correctOptionId = UUID.randomUUID();
    private final UUID wrongOptionId = UUID.randomUUID();
    private final UUID progressId = UUID.randomUUID();

    private CourseModule module;
    private ModuleSection section;
    private SectionQuestion question;
    private QuizService quizService;

    @BeforeEach
    void setUp() {
        User student = mock(User.class);
        lenient().when(student.getId()).thenReturn(UUID.randomUUID());
        lenient().when(currentUserProvider.requireCurrentUser()).thenReturn(student);

        module = new CourseModule();
        module.setId(moduleId);
        section = new ModuleSection();
        section.setId(sectionId);
        section.setSectionType(SectionType.THEORY);
        question = new SectionQuestion();
        question.setId(questionId);
        question.setSection(section);
        question.setExplanation("Lección completa de la pregunta");
        question.setOptions(List.of(correct(), wrong()));

        StudentModuleProgress progress = new StudentModuleProgress();
        progress.setId(progressId);

        when(modules.findById(moduleId)).thenReturn(Optional.of(module));
        when(sectionRepository.findByModuleId(moduleId)).thenReturn(List.of(section));
        when(moduleProgressRepository.findByUserIdAndModuleId(student.getId(), moduleId))
                .thenReturn(Optional.of(progress));
        when(sectionProgressRepository.findByModuleProgressId(progressId)).thenReturn(List.of());
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));

        quizService = new QuizService(modules, sectionRepository, moduleProgressRepository,
                sectionProgressRepository, questionRepository, mock(PracticeAnswerRepository.class),
                currentUserProvider, moduleService, entityManager, mock(ApplicationEventPublisher.class));
    }

    private QuestionOption correct() {
        QuestionOption option = new QuestionOption();
        option.setId(correctOptionId);
        option.setOrderIndex(0);
        option.setText("Opción correcta");
        option.setCorrect(true);
        return option;
    }

    private QuestionOption wrong() {
        QuestionOption option = new QuestionOption();
        option.setId(wrongOptionId);
        option.setOrderIndex(1);
        option.setText("Opción incorrecta");
        option.setCorrect(false);
        option.setFeedback("Esta opción falla porque…");
        return option;
    }

    @Test
    void aWrongAnswerCompletesTheTheoryAndRevealsTheCorrectOption() {
        CheckAnswerResponse response = quizService.checkTheoryAnswer(
                moduleId, sectionId, request(wrongOptionId));

        assertFalse(response.correct());
        assertTrue(response.sectionCompleted());
        assertEquals(correctOptionId, response.correctOptionId());
        assertEquals("Lección completa de la pregunta", response.explanation());
        assertEquals("Esta opción falla porque…", response.feedback());
    }

    @Test
    void aWrongAnswerStillSavesProgressAsCompleted() {
        quizService.checkTheoryAnswer(moduleId, sectionId, request(wrongOptionId));

        org.mockito.ArgumentCaptor<StudentSectionProgress> captor =
                org.mockito.ArgumentCaptor.forClass(StudentSectionProgress.class);
        verify(sectionProgressRepository).saveAndFlush(captor.capture());
        assertEquals(com.cyberacademy.domains.module.model.ProgressStatus.COMPLETED,
                captor.getValue().getStatus());
        assertNotNull(captor.getValue().getCompletedAt());
        verify(entityManager).refresh(org.mockito.ArgumentMatchers.any(StudentModuleProgress.class));
    }

    @Test
    void aCorrectAnswerReportsCompletionAndTheCorrectOption() {
        CheckAnswerResponse response = quizService.checkTheoryAnswer(
                moduleId, sectionId, request(correctOptionId));

        assertTrue(response.correct());
        assertTrue(response.sectionCompleted());
        assertEquals(correctOptionId, response.correctOptionId());
        assertEquals("Lección completa de la pregunta", response.explanation());
    }

    private CheckAnswerRequest request(UUID optionId) {
        CheckAnswerRequest request = new CheckAnswerRequest();
        request.setQuestionId(questionId);
        request.setOptionId(optionId);
        return request;
    }
}
