-- Usuarios de prueba para CAM-43 (Login) / Postman. Correr con:
--   psql -U postgres -d TIP -f docs/db/seed-users.sql
--
-- Se puede volver a correr sobre una base que ya tiene algunos de estos usuarios:
-- los que ya existen (mismo username) se saltean.
--
-- Contraseñas en texto plano (solo para probar localmente):
--   admin    / admin123
--   chofer   / chofer123
--   tecnico  / tecnico123
--   tecnico2 / tecnico123  (mismo hash que tecnico -- segundo técnico para probar la asignación de OTs, CAM-60)

insert into users (id, username, password_hash, role) values
  (gen_random_uuid(), 'admin', '$2a$10$f.6fLuq50cD/q6x3p9Gcx.LKvuE/XCL11PDvrq9IFQTjdK15vfHKi', 'ADMIN'),
  (gen_random_uuid(), 'chofer', '$2a$10$edcMcLxUUrmfxqfR3hWYvOC66svPwpGRgoEhM/i.l8WwX9ZOxo1Hi', 'CHOFER'),
  (gen_random_uuid(), 'tecnico', '$2a$10$uGDj/epb3fSJk1vgvVGqr.UhYwJtvrB3Tmkom8dAdTNMQnrr90jhG', 'TECNICO'),
  (gen_random_uuid(), 'tecnico2', '$2a$10$uGDj/epb3fSJk1vgvVGqr.UhYwJtvrB3Tmkom8dAdTNMQnrr90jhG', 'TECNICO')
on conflict (username) do nothing;

select id, username, role from users;
