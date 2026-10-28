-- ============================================================================
-- V3: cerrar el acceso publico (Data API de Supabase) a modulos y progreso
-- ============================================================================
-- Supabase expone el esquema "public" por PostgREST, y por defecto concede
-- privilegios a los roles anon/authenticated. En las tablas el RLS los bloquea,
-- pero una vista SIN security_invoker se ejecuta con los permisos de su dueno,
-- saltandose el RLS -> el dashboard la marca como "UNRESTRICTED".
--
-- Esta migracion:
--   1. Hace la vista security_invoker (respeta permisos y RLS de quien consulta).
--   2. Revoca los privilegios de anon/authenticated sobre la vista y las tablas.
--
-- El backend sigue funcionando igual porque se conecta con el rol "postgres",
-- que es dueno de los objetos y por tanto no queda afectado por el RLS.
-- ============================================================================

-- 1. La vista debe ejecutarse con los permisos de quien consulta --------------
ALTER VIEW public.v_student_module_summary SET (security_invoker = true);

-- 2. Sin acceso desde la Data API publica ------------------------------------
REVOKE ALL ON public.v_student_module_summary FROM anon, authenticated;

REVOKE ALL ON public.modules                  FROM anon, authenticated;
REVOKE ALL ON public.module_sections          FROM anon, authenticated;
REVOKE ALL ON public.student_module_progress  FROM anon, authenticated;
REVOKE ALL ON public.student_section_progress FROM anon, authenticated;

-- 3. Dejar el RLS explicito (Supabase ya lo activa, esto lo garantiza) -------
ALTER TABLE public.modules                  ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.module_sections          ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.student_module_progress  ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.student_section_progress ENABLE ROW LEVEL SECURITY;

COMMENT ON VIEW public.v_student_module_summary IS
    'Resumen calculado por estudiante y modulo. security_invoker: respeta RLS; sin acceso para anon/authenticated.';
