package com.cyberacademy.domains.rocu;

import com.cyberacademy.domains.module.repository.SectionQuestionRepository;
import org.springframework.stereotype.Component;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
class AssessmentGuard {
    static final String RESPONSE = "Esa pregunta es para que practiques. No puedo elegir, descartar ni confirmar opciones del módulo. Revisa la lección y decide por tu cuenta; si quieres, podemos repasar el tema con un ejemplo diferente.";
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern OPTION = Pattern.compile("(?iu)(?:^|\\s)([a-d1-4])[.)]\\s+\\S");
    private final SectionQuestionRepository questions;

    AssessmentGuard(SectionQuestionRepository questions) { this.questions = questions; }

    boolean matches(String message) {
        var options = OPTION.matcher(message);
        if (message.contains("?") && options.find()) {
            String first = options.group(1);
            while (options.find()) if (!first.equalsIgnoreCase(options.group(1))) return true;
        }
        String text = " " + normalize(message) + " ";
        return questions.findAssessmentPrompts().stream().map(AssessmentGuard::normalize)
                .anyMatch(prompt -> !prompt.isBlank() && text.contains(" " + prompt + " "));
    }

    private static String normalize(String value) {
        String plain = MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("");
        return SEPARATORS.matcher(plain.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }
}
