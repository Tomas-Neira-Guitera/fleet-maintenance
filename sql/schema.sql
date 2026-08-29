-- Schema de la base "TIP". Sin herramienta de migraciones todavía (decisión
-- abierta en docs/STATE.md): por ahora se corre a mano con psql cada vez que
-- se agrega una tabla.
--
--   psql -U postgres -d TIP -f sql/schema.sql

CREATE TABLE IF NOT EXISTS defectos (
    id          SERIAL PRIMARY KEY,
    gravedad    VARCHAR(10) NOT NULL CHECK (gravedad IN ('bajo', 'medio', 'alto')),
    fecha       TIMESTAMPTZ NOT NULL DEFAULT now(),
    descripcion TEXT NOT NULL,
    patente     VARCHAR(20) NOT NULL
);
