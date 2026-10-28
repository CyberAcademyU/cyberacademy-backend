-- ============================================================================
-- V4: recalcular el progreso de los estudiantes cuando cambian los pesos
-- ============================================================================
-- Hasta ahora fn_refresh_module_progress solo se disparaba al registrar una
-- nota. Si un administrador cambia el peso de una sección, las notas ya
-- calculadas quedaban desactualizadas. Este trigger lo corrige.
-- ============================================================================

CREATE OR REPLACE FUNCTION fn_refresh_module_progress_for_module(p_module_id UUID)
RETURNS VOID AS $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT id FROM student_module_progress WHERE module_id = p_module_id LOOP
        PERFORM fn_refresh_module_progress(r.id);
    END LOOP;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION fn_trg_section_weight_changed()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.weight_percent IS DISTINCT FROM OLD.weight_percent THEN
        PERFORM fn_refresh_module_progress_for_module(NEW.module_id);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_section_weight_refresh ON module_sections;
CREATE TRIGGER trg_section_weight_refresh
    AFTER UPDATE ON module_sections
    FOR EACH ROW EXECUTE FUNCTION fn_trg_section_weight_changed();

COMMENT ON FUNCTION fn_refresh_module_progress_for_module(UUID) IS
    'Recalcula el estado y la nota de todos los estudiantes de un módulo';
