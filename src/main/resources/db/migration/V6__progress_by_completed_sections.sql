-- ============================================================================
-- V6: porcentaje de avance por sesiones completadas
-- ============================================================================
-- El avance del módulo ya no se pondera con los pesos de las secciones:
-- cada una de las 4 sesiones (3 teorías + 1 práctica) cuenta igual.
--   - 3 teorías completadas -> 75%
--   - práctica completada    -> 100%
--
-- La nota final del módulo sigue saliendo únicamente de la práctica, porque
-- es la única sección que registra puntaje. La fórmula ponderada de V2 se
-- conserva para no alterar los datos históricos.
-- ============================================================================

CREATE OR REPLACE VIEW public.v_student_module_summary AS
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
        COUNT(s.id) FILTER (WHERE sp.status = 'COMPLETED') * 100.0
        / NULLIF(COUNT(s.id), 0), 2
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

-- La vista se ejecuta con los permisos de quien consulta y sigue cerrada a la
-- Data API pública (se reaplica porque CREATE OR REPLACE puede no conservarlo).
ALTER VIEW public.v_student_module_summary SET (security_invoker = true);
REVOKE ALL ON public.v_student_module_summary FROM anon, authenticated;

COMMENT ON VIEW public.v_student_module_summary IS
    'Resumen por estudiante y módulo. Avance = sesiones completadas / 4. security_invoker: respeta RLS; sin acceso para anon/authenticated.';
