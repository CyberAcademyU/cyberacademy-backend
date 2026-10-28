-- ============================================================================
-- V5: contenido de las teorías y cuestionarios de evaluación
-- ============================================================================
-- Flujo del estudiante:
--   1. Lee la teoría 1 y responde su pregunta de comprensión.
--   2. Al acertar se desbloquea la teoría 2 (y después la 3).
--   3. Al completar las 3 teorías se desbloquea la práctica.
--   4. La práctica es un cuestionario de un solo intento: el backend lo
--      corrige y guarda la nota. Después, todas las sesiones quedan bloqueadas.
--
-- La nota del módulo sale únicamente de la práctica: al ser la única sección
-- con nota, la fórmula ponderada existente (V2) devuelve exactamente su nota.
-- ============================================================================

-- 1. Texto de lectura de cada sesión (teorías e instrucciones de la práctica) --
ALTER TABLE public.module_sections
    ADD COLUMN IF NOT EXISTS content TEXT;

COMMENT ON COLUMN public.module_sections.content IS
    'Contenido de lectura de la teoría o instrucciones de la práctica.';

-- 2. Preguntas de cada sección ------------------------------------------------
CREATE TABLE IF NOT EXISTS public.section_questions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    section_id  UUID         NOT NULL REFERENCES public.module_sections(id) ON DELETE CASCADE,
    order_index INT          NOT NULL,
    prompt      TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_section_questions UNIQUE (section_id, order_index),
    CONSTRAINT chk_section_questions_order CHECK (order_index > 0)
);

CREATE INDEX IF NOT EXISTS idx_section_questions_section
    ON public.section_questions(section_id);

-- 3. Opciones de cada pregunta ------------------------------------------------
CREATE TABLE IF NOT EXISTS public.question_options (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID         NOT NULL REFERENCES public.section_questions(id) ON DELETE CASCADE,
    order_index INT          NOT NULL,
    text        TEXT         NOT NULL,
    is_correct  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_question_options UNIQUE (question_id, order_index),
    CONSTRAINT chk_question_options_order CHECK (order_index > 0)
);

CREATE INDEX IF NOT EXISTS idx_question_options_question
    ON public.question_options(question_id);

-- Como máximo una opción correcta por pregunta
CREATE UNIQUE INDEX IF NOT EXISTS uq_question_one_correct
    ON public.question_options(question_id)
    WHERE is_correct;

-- 4. updated_at automático ----------------------------------------------------
DROP TRIGGER IF EXISTS trg_section_questions_updated_at ON public.section_questions;
CREATE TRIGGER trg_section_questions_updated_at
    BEFORE UPDATE ON public.section_questions
    FOR EACH ROW EXECUTE FUNCTION public.fn_set_updated_at();

DROP TRIGGER IF EXISTS trg_question_options_updated_at ON public.question_options;
CREATE TRIGGER trg_question_options_updated_at
    BEFORE UPDATE ON public.question_options
    FOR EACH ROW EXECUTE FUNCTION public.fn_set_updated_at();

-- 5. Cerrar el acceso público (Data API de Supabase), igual que en V3 ---------
REVOKE ALL ON public.section_questions FROM anon, authenticated;
REVOKE ALL ON public.question_options  FROM anon, authenticated;

ALTER TABLE public.section_questions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.question_options  ENABLE ROW LEVEL SECURITY;

COMMENT ON TABLE public.section_questions IS
    'Preguntas de una sección: 1 de comprensión en cada teoría y N en la práctica.';
COMMENT ON TABLE public.question_options IS
    'Opciones de cada pregunta. Exactamente una marcada como correcta.';
