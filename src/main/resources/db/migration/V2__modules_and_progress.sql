-- ============================================================================
-- V2: Módulos de aprendizaje, secciones (3 teoría + 1 práctica) y progreso
-- ============================================================================
-- Estructura:
--   modules                     -> catálogo de módulos
--   module_sections             -> 3 sesiones de teoría + 1 de práctica por módulo
--   student_module_progress     -> estado y nota final de un estudiante en un módulo
--   student_section_progress    -> nota de cada sección del estudiante
--   v_student_module_summary    -> vista con el resumen calculado por módulo
--
-- El puntaje del módulo se calcula automáticamente al completar las secciones:
--   nota_modulo = SUM(nota_seccion * peso) / SUM(peso de secciones calificadas)
-- ============================================================================

-- 1. Tipos enumerados ---------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'section_type') THEN
        CREATE TYPE section_type AS ENUM ('THEORY', 'PRACTICE');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'progress_status') THEN
        CREATE TYPE progress_status AS ENUM ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'FAILED');
    END IF;
END $$;

-- 2. Catálogo de módulos ------------------------------------------------------
CREATE TABLE IF NOT EXISTS modules (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(50)  NOT NULL UNIQUE,          -- ej: MOD-01
    title         VARCHAR(150) NOT NULL,
    description   TEXT,
    order_index   INT          NOT NULL DEFAULT 1,       -- orden en el curso
    passing_score NUMERIC(5,2) NOT NULL DEFAULT 70.00,   -- nota mínima para aprobar
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_modules_passing_score CHECK (passing_score BETWEEN 0 AND 100)
);

CREATE INDEX IF NOT EXISTS idx_modules_order ON modules(order_index);

-- 3. Secciones del módulo: 3 de teoría + 1 de práctica ------------------------
CREATE TABLE IF NOT EXISTS module_sections (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    module_id      UUID          NOT NULL REFERENCES modules(id) ON DELETE CASCADE,
    section_type   section_type  NOT NULL,
    section_order  INT           NOT NULL,               -- teoría 1..3, práctica 1
    title          VARCHAR(150)  NOT NULL,
    weight_percent NUMERIC(5,2)  NOT NULL DEFAULT 25.00, -- peso dentro del módulo
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_module_sections UNIQUE (module_id, section_type, section_order),
    CONSTRAINT chk_section_order CHECK (
        (section_type::text = 'THEORY'   AND section_order BETWEEN 1 AND 3) OR
        (section_type::text = 'PRACTICE' AND section_order = 1)
    ),
    CONSTRAINT chk_section_weight CHECK (weight_percent > 0 AND weight_percent <= 100)
);

CREATE INDEX IF NOT EXISTS idx_module_sections_module ON module_sections(module_id);

-- 4. Progreso del estudiante por módulo (estado + nota final) -----------------
CREATE TABLE IF NOT EXISTS student_module_progress (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID             NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    module_id    UUID             NOT NULL REFERENCES modules(id) ON DELETE CASCADE,
    status       progress_status  NOT NULL DEFAULT 'NOT_STARTED',
    score        NUMERIC(5,2),                            -- nota final ponderada
    started_at   TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_student_module UNIQUE (user_id, module_id),
    CONSTRAINT chk_module_progress_score CHECK (score IS NULL OR score BETWEEN 0 AND 100)
);

CREATE INDEX IF NOT EXISTS idx_student_module_user   ON student_module_progress(user_id);
CREATE INDEX IF NOT EXISTS idx_student_module_module ON student_module_progress(module_id);

-- 5. Progreso del estudiante por sección --------------------------------------
CREATE TABLE IF NOT EXISTS student_section_progress (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    module_progress_id UUID             NOT NULL REFERENCES student_module_progress(id) ON DELETE CASCADE,
    section_id         UUID             NOT NULL REFERENCES module_sections(id)         ON DELETE CASCADE,
    status             progress_status  NOT NULL DEFAULT 'NOT_STARTED',
    score              NUMERIC(5,2),                      -- nota de la sección (0-100)
    attempts           INT              NOT NULL DEFAULT 0,
    completed_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_student_section UNIQUE (module_progress_id, section_id),
    CONSTRAINT chk_section_progress_score CHECK (score IS NULL OR score BETWEEN 0 AND 100)
);

CREATE INDEX IF NOT EXISTS idx_section_progress_module ON student_section_progress(module_progress_id);

-- 6. updated_at automático ----------------------------------------------------
CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_modules_updated_at ON modules;
CREATE TRIGGER trg_modules_updated_at
    BEFORE UPDATE ON modules
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

DROP TRIGGER IF EXISTS trg_module_sections_updated_at ON module_sections;
CREATE TRIGGER trg_module_sections_updated_at
    BEFORE UPDATE ON module_sections
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

DROP TRIGGER IF EXISTS trg_student_module_updated_at ON student_module_progress;
CREATE TRIGGER trg_student_module_updated_at
    BEFORE UPDATE ON student_module_progress
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

DROP TRIGGER IF EXISTS trg_student_section_updated_at ON student_section_progress;
CREATE TRIGGER trg_student_section_updated_at
    BEFORE UPDATE ON student_section_progress
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- 7. Al crear un módulo se generan sus 4 secciones ---------------------------
--    Pesos por defecto: teoría 20% cada una + práctica 40% = 100%
CREATE OR REPLACE FUNCTION fn_create_default_module_sections()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO module_sections (module_id, section_type, section_order, title, weight_percent)
    VALUES
        (NEW.id, 'THEORY',   1, 'Teoría 1', 20.00),
        (NEW.id, 'THEORY',   2, 'Teoría 2', 20.00),
        (NEW.id, 'THEORY',   3, 'Teoría 3', 20.00),
        (NEW.id, 'PRACTICE', 1, 'Práctica', 40.00);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_modules_default_sections ON modules;
CREATE TRIGGER trg_modules_default_sections
    AFTER INSERT ON modules
    FOR EACH ROW EXECUTE FUNCTION fn_create_default_module_sections();

-- 8. Recalcular estado y nota del módulo --------------------------------------
CREATE OR REPLACE FUNCTION fn_refresh_module_progress(p_progress_id UUID)
RETURNS VOID AS $$
DECLARE
    v_total_sections INT;
    v_completed      INT;
    v_total_weight   NUMERIC;
    v_done_weight    NUMERIC;
    v_weighted_sum   NUMERIC;
    v_score          NUMERIC;
    v_passing        NUMERIC;
BEGIN
    SELECT COUNT(*),
           COUNT(*) FILTER (WHERE sp.status = 'COMPLETED'),
           COALESCE(SUM(s.weight_percent), 0),
           COALESCE(SUM(s.weight_percent) FILTER (WHERE sp.status = 'COMPLETED' AND sp.score IS NOT NULL), 0),
           COALESCE(SUM(sp.score * s.weight_percent) FILTER (WHERE sp.status = 'COMPLETED' AND sp.score IS NOT NULL), 0)
      INTO v_total_sections, v_completed, v_total_weight, v_done_weight, v_weighted_sum
      FROM module_sections s
      JOIN student_module_progress p ON p.id = p_progress_id
      LEFT JOIN student_section_progress sp
             ON sp.section_id = s.id
            AND sp.module_progress_id = p_progress_id
     WHERE s.module_id = p.module_id;

    -- Promedio ponderado sobre las secciones ya calificadas
    v_score := CASE WHEN v_done_weight > 0 THEN ROUND(v_weighted_sum / v_done_weight, 2) END;

    SELECT m.passing_score
      INTO v_passing
      FROM modules m
      JOIN student_module_progress p ON p.module_id = m.id
     WHERE p.id = p_progress_id;

    UPDATE student_module_progress
       SET score = v_score,
           status = CASE
                        WHEN v_completed = 0 THEN 'NOT_STARTED'::progress_status
                        WHEN v_completed < v_total_sections THEN 'IN_PROGRESS'::progress_status
                        WHEN v_score IS NOT NULL AND v_score >= v_passing THEN 'COMPLETED'::progress_status
                        ELSE 'FAILED'::progress_status
                    END,
           started_at = CASE
                            WHEN v_completed > 0 THEN COALESCE(started_at, CURRENT_TIMESTAMP)
                            ELSE NULL
                        END,
           completed_at = CASE
                              WHEN v_completed = v_total_sections THEN COALESCE(completed_at, CURRENT_TIMESTAMP)
                              ELSE NULL
                          END
     WHERE id = p_progress_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION fn_trg_section_progress()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM fn_refresh_module_progress(OLD.module_progress_id);
        RETURN OLD;
    END IF;

    PERFORM fn_refresh_module_progress(NEW.module_progress_id);

    IF TG_OP = 'UPDATE' AND NEW.module_progress_id <> OLD.module_progress_id THEN
        PERFORM fn_refresh_module_progress(OLD.module_progress_id);
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_section_progress_refresh ON student_section_progress;
CREATE TRIGGER trg_section_progress_refresh
    AFTER INSERT OR UPDATE OR DELETE ON student_section_progress
    FOR EACH ROW EXECUTE FUNCTION fn_trg_section_progress();

-- 9. Vista de resumen por estudiante y módulo ---------------------------------
CREATE OR REPLACE VIEW v_student_module_summary AS
SELECT
    p.user_id,
    p.module_id,
    m.code                                        AS module_code,
    m.title                                       AS module_title,
    m.order_index,
    p.status,
    p.score                                       AS module_score,
    m.passing_score,
    COUNT(s.id)                                   AS total_sections,
    COUNT(s.id) FILTER (WHERE sp.status = 'COMPLETED') AS completed_sections,
    ROUND(
        COALESCE(SUM(s.weight_percent) FILTER (WHERE sp.status = 'COMPLETED'), 0)
        * 100.0 / NULLIF(SUM(s.weight_percent), 0), 2
    )                                             AS progress_percent,
    ROUND(
        SUM(sp.score * s.weight_percent)
          FILTER (WHERE sp.status = 'COMPLETED' AND sp.score IS NOT NULL)
        / NULLIF(SUM(s.weight_percent)
          FILTER (WHERE sp.status = 'COMPLETED' AND sp.score IS NOT NULL), 0)
    , 2)                                          AS weighted_score,
    CASE
        WHEN p.status = 'COMPLETED' THEN TRUE
        ELSE FALSE
    END                                           AS is_passed,
    p.started_at,
    p.completed_at
FROM student_module_progress p
JOIN modules m          ON m.id = p.module_id
JOIN module_sections s  ON s.module_id = m.id
LEFT JOIN student_section_progress sp
       ON sp.section_id = s.id
      AND sp.module_progress_id = p.id
GROUP BY p.user_id, p.module_id, m.code, m.title, m.order_index,
         p.status, p.score, m.passing_score, p.started_at, p.completed_at;

COMMENT ON TABLE modules                  IS 'Catálogo de módulos del curso';
COMMENT ON TABLE module_sections          IS 'Secciones de cada módulo: 3 teoría + 1 práctica';
COMMENT ON TABLE student_module_progress  IS 'Estado y nota final de un estudiante en un módulo';
COMMENT ON TABLE student_section_progress IS 'Nota y estado de cada sección por estudiante';
COMMENT ON VIEW  v_student_module_summary IS 'Resumen calculado: progreso, nota ponderada y aprobación por módulo';
COMMENT ON COLUMN module_sections.weight_percent IS 'Peso de la sección dentro del módulo (deben sumar 100)';
