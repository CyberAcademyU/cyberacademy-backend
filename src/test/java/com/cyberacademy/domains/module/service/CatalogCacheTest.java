package com.cyberacademy.domains.module.service;

import com.cyberacademy.domains.module.dto.PublicCatalogChangedEvent;
import com.cyberacademy.domains.module.dto.PublicModuleResponse;
import com.cyberacademy.domains.module.model.CourseModule;
import com.cyberacademy.domains.module.repository.CourseModuleRepository;
import com.cyberacademy.domains.module.repository.ModuleSectionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionalEventListenerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class CatalogCacheTest {
    private final CourseModuleRepository modules = mock(CourseModuleRepository.class);
    private final ModuleSectionRepository sections = mock(ModuleSectionRepository.class);

    private CourseModule module(String title) {
        CourseModule module = new CourseModule();
        module.setId(UUID.randomUUID());
        module.setCode("CA-01");
        module.setTitle(title);
        return module;
    }

    @Test
    void cachesPublicResultsAndExpiresAfterOneMinute() {
        AtomicLong nanos = new AtomicLong();
        TestTransactionManager transactions = new TestTransactionManager();
        CatalogService service = new CatalogService(modules, sections, transactions, nanos::get);
        when(modules.findByActiveTrueOrderByOrderIndexAsc()).thenReturn(List.of(module("Inicial")), List.of(module("Actualizado")));
        when(sections.findByModuleIdIn(anyList())).thenReturn(List.of());

        List<PublicModuleResponse> result = service.listPublicModules();
        assertEquals("Inicial", result.get(0).title());
        assertEquals(result, service.listPublicModules());
        verify(modules, times(1)).findByActiveTrueOrderByOrderIndexAsc();
        assertEquals(1, transactions.begins, "Un acierto de caché no debe abrir una transacción");
        assertThrows(UnsupportedOperationException.class, result::clear);
        nanos.addAndGet(TimeUnit.SECONDS.toNanos(60));
        assertEquals("Actualizado", service.listPublicModules().get(0).title());
        verify(modules, times(2)).findByActiveTrueOrderByOrderIndexAsc();
        assertEquals(2, transactions.begins);
    }

    @Test
    void publicModuleTopicsAreDefensivelyCopied() {
        List<String> input = new ArrayList<>(List.of("Tema original"));
        PublicModuleResponse response = new PublicModuleResponse("CA-01", "Módulo", null, null, 1, null, input);
        input.set(0, "Tema modificado");
        assertEquals(List.of("Tema original"), response.topics());
        assertThrows(UnsupportedOperationException.class, () -> response.topics().add("Otro"));
    }

    @Test
    void invalidatesOnlyAfterTransactionCommits() {
        AtomicReference<List<CourseModule>> data = new AtomicReference<>(List.of(module("Inicial")));
        when(modules.findByActiveTrueOrderByOrderIndexAsc()).thenAnswer(inv -> data.get());
        when(sections.findByModuleIdIn(anyList())).thenReturn(List.of());
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(CourseModuleRepository.class, () -> modules);
            context.registerBean(ModuleSectionRepository.class, () -> sections);
            context.registerBean(PlatformTransactionManager.class, TestTransactionManager::new);
            context.registerBean(CatalogService.class);
            context.registerBean(TransactionalEventListenerFactory.class);
            context.refresh();
            CatalogService service = context.getBean(CatalogService.class);
            TransactionTemplate tx = new TransactionTemplate(new TestTransactionManager());
            assertEquals("Inicial", service.listPublicModules().get(0).title());
            data.set(List.of(module("Nuevo")));
            assertEquals("Inicial", service.listPublicModules().get(0).title());

            tx.executeWithoutResult(status -> {
                assertEquals("Inicial", service.listPublicModules().get(0).title());
                context.publishEvent(new PublicCatalogChangedEvent());
                assertEquals("Inicial", service.listPublicModules().get(0).title());
                status.setRollbackOnly();
            });
            assertEquals("Inicial", service.listPublicModules().get(0).title());
            tx.executeWithoutResult(status -> context.publishEvent(new PublicCatalogChangedEvent()));
            assertEquals("Nuevo", service.listPublicModules().get(0).title());
        }
    }

    @Test
    void invalidationCannotLeaveAnOldInFlightLoadCached() throws Exception {
        CatalogService service = new CatalogService(modules, sections, new TestTransactionManager());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(modules.findByActiveTrueOrderByOrderIndexAsc()).thenAnswer(inv -> {
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return List.of(module("Anterior"));
        }).thenReturn(List.of(module("Nuevo")));
        when(sections.findByModuleIdIn(anyList())).thenReturn(List.of());
        var pool = Executors.newFixedThreadPool(2);
        try {
            var loading = pool.submit(service::listPublicModules);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var invalidating = pool.submit(() -> service.onPublicCatalogChanged(new PublicCatalogChangedEvent()));
            invalidating.get(5, TimeUnit.SECONDS);
            release.countDown();
            loading.get(5, TimeUnit.SECONDS);
            assertEquals("Nuevo", service.listPublicModules().get(0).title());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private static class TestTransactionManager extends AbstractPlatformTransactionManager implements PlatformTransactionManager {
        int begins;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { begins++; }
        @Override protected void doCommit(DefaultTransactionStatus status) {}
        @Override protected void doRollback(DefaultTransactionStatus status) {}
    }
}
