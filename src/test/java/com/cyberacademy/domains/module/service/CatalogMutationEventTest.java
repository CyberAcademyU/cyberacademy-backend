package com.cyberacademy.domains.module.service;

import com.cyberacademy.domains.auth.repository.UserRepository;
import com.cyberacademy.domains.module.controller.PublicCatalogController;
import com.cyberacademy.domains.module.dto.*;
import com.cyberacademy.domains.module.model.*;
import com.cyberacademy.domains.module.repository.*;
import com.cyberacademy.security.CurrentUserProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CatalogMutationEventTest {
    private final CourseModuleRepository modules = mock(CourseModuleRepository.class);
    private final ModuleSectionRepository sections = mock(ModuleSectionRepository.class);
    private final SectionQuestionRepository questions = mock(SectionQuestionRepository.class);
    private final StudentModuleProgressRepository progress = mock(StudentModuleProgressRepository.class);
    private final StudentSectionProgressRepository sectionProgress = mock(StudentSectionProgressRepository.class);
    private final CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ModuleService service = new ModuleService(modules, sections, progress, sectionProgress, questions,
            mock(UserRepository.class), mock(SavedModuleRepository.class), currentUser, events);

    @Test
    void moduleCreationAndEditsPublishCatalogInvalidation() {
        CreateModuleRequest create = new CreateModuleRequest();
        create.setCode("CA-01");
        create.setTitle("Inicio");
        service.createModule(create);
        verify(events).publishEvent(any(PublicCatalogChangedEvent.class));

        clearInvocations(events);
        UUID moduleId = UUID.randomUUID();
        CourseModule module = new CourseModule();
        module.setId(moduleId);
        when(modules.findById(moduleId)).thenReturn(Optional.of(module));
        UpdateModuleRequest edit = new UpdateModuleRequest();
        edit.setTitle("Nuevo título");
        service.updateModule(moduleId, edit);
        verify(events).publishEvent(any(PublicCatalogChangedEvent.class));

        clearInvocations(events);
        UUID sectionId = UUID.randomUUID();
        ModuleSection section = new ModuleSection();
        section.setId(sectionId);
        when(sections.findByIdAndModuleId(sectionId, moduleId)).thenReturn(Optional.of(section));
        UpdateSectionRequest sectionEdit = new UpdateSectionRequest();
        sectionEdit.setTitle("Nuevo tema");
        service.updateSection(moduleId, sectionId, sectionEdit);
        verify(events).publishEvent(any(PublicCatalogChangedEvent.class));
    }

    @Test
    void savingTheoryContentPublishesCatalogInvalidation() {
        UUID moduleId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        CourseModule module = new CourseModule();
        module.setId(moduleId);
        ModuleSection section = new ModuleSection();
        section.setId(sectionId);
        section.setSectionType(SectionType.THEORY);
        when(modules.findById(moduleId)).thenReturn(Optional.of(module));
        when(sections.findByIdAndModuleId(sectionId, moduleId)).thenReturn(Optional.of(section));
        QuizService quiz = new QuizService(modules, sections, progress, sectionProgress, questions,
                mock(PracticeAnswerRepository.class), currentUser, service, mock(EntityManager.class), events);
        SaveSectionContentRequest.OptionInput correct = new SaveSectionContentRequest.OptionInput();
        correct.setText("Correcta");
        correct.setCorrect(true);
        SaveSectionContentRequest.OptionInput incorrect = new SaveSectionContentRequest.OptionInput();
        incorrect.setText("Incorrecta");
        SaveSectionContentRequest.QuestionInput question = new SaveSectionContentRequest.QuestionInput();
        question.setPrompt("Pregunta");
        question.setOptions(List.of(correct, incorrect));
        SaveSectionContentRequest request = new SaveSectionContentRequest();
        request.setTitle("Tema actualizado");
        request.setContent("Contenido");
        request.setQuestions(List.of(question));
        quiz.saveSectionContent(moduleId, sectionId, request);
        verify(events).publishEvent(any(PublicCatalogChangedEvent.class));
    }

    @Test
    void publicResponseRequiresRevalidationRatherThanKeepingAnOldBrowserCopy() {
        CatalogService catalog = mock(CatalogService.class);
        when(catalog.listPublicModules()).thenReturn(List.of());
        String header = new PublicCatalogController(catalog).modules().getHeaders().getCacheControl();
        assertNotNull(header);
        assertTrue(header.contains("no-cache"));
    }
}
