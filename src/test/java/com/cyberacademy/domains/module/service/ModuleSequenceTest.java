package com.cyberacademy.domains.module.service;

import com.cyberacademy.domains.module.model.CourseModule;
import com.cyberacademy.domains.module.model.ProgressStatus;
import com.cyberacademy.domains.module.model.StudentModuleProgress;
import com.cyberacademy.domains.module.model.StudentSectionProgress;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ModuleSequenceTest {
    private CourseModule module(int order) {
        CourseModule module = new CourseModule();
        module.setId(UUID.randomUUID());
        module.setOrderIndex(order);
        return module;
    }

    private StudentModuleProgress progress(CourseModule module, ProgressStatus status) {
        StudentModuleProgress progress = new StudentModuleProgress();
        progress.setModule(module);
        progress.setStatus(status);
        return progress;
    }

    @Test void onlyFirstIsAvailableUntilPreviousModulesAreFinished() {
        var first = module(1); var second = module(2); var third = module(3);
        var modules = List.of(first, second, third);
        assertEquals(java.util.Set.of(second.getId(), third.getId()), ModuleService.lockedModules(modules, Map.of()));
        assertEquals(java.util.Set.of(second.getId(), third.getId()), ModuleService.lockedModules(modules,
                Map.of(first.getId(), progress(first, ProgressStatus.IN_PROGRESS))));
        assertEquals(java.util.Set.of(third.getId()), ModuleService.lockedModules(modules,
                Map.of(first.getId(), progress(first, ProgressStatus.FAILED))));
        assertTrue(ModuleService.lockedModules(modules, Map.of(
                first.getId(), progress(first, ProgressStatus.COMPLETED),
                second.getId(), progress(second, ProgressStatus.FAILED))).isEmpty());
    }

    @Test void completedPracticeClosesAfterOneAttemptRegardlessOfScore() {
        var practice = new StudentSectionProgress();
        practice.setStatus(ProgressStatus.COMPLETED);
        practice.setAttempts(1);
        practice.setScore(BigDecimal.ZERO);
        assertTrue(ModuleService.isClosed(practice, BigDecimal.valueOf(70)));
        assertFalse(ModuleService.isPassed(practice, BigDecimal.valueOf(70)));
    }

    @Test void inactiveModulesDoNotBlockTheSequence() {
        var inactive = module(1); inactive.setActive(false);
        var active = module(2);
        assertTrue(ModuleService.lockedModules(List.of(inactive, active), Map.of()).isEmpty());
    }
}
