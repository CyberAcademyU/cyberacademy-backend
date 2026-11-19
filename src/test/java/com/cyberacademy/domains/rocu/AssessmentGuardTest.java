package com.cyberacademy.domains.rocu;

import com.cyberacademy.domains.module.repository.SectionQuestionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssessmentGuardTest {
    private final SectionQuestionRepository questions = mock(SectionQuestionRepository.class);
    private final AssessmentGuard guard = new AssessmentGuard(questions);

    @Test void recognizesCopiedPromptWithoutAnswerLabelsOrPunctuation() {
        when(questions.findAssessmentPrompts()).thenReturn(List.of("¿Qué señal indica suplantación?"));
        assertTrue(guard.matches("Ayuda: QUE SEÑAL\n INDICA SUPLANTACION\nRemitente distinto\nArchivo adjunto"));
        assertFalse(guard.matches("¿Dónde abro la práctica del módulo?"));
    }

    @Test void recognizesMultipleChoiceWithoutConsultingTheBank() {
        assertTrue(guard.matches("¿Cuál marcarías? A) Responder B) Reportar"));
        verifyNoInteractions(questions);
    }
}
