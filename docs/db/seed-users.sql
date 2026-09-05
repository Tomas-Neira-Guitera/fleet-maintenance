-- Usuarios de prueba para CAM-43 (Login) / Postman. Correr con:
--   psql -U postgres -d TIP -f docs/db/seed-users.sql
--
-- Contraseñas en texto plano (solo para probar localmente):
--   admin  / admin123
--   chofer / chofer123

insert into users (id, username, password_hash, role) values
  (gen_random_uuid(), 'admin', '$2a$10$f.6fLuq50cD/q6x3p9Gcx.LKvuE/XCL11PDvrq9IFQTjdK15vfHKi', 'ADMIN'),
  (gen_random_uuid(), 'chofer', '$2a$10$edcMcLxUUrmfxqfR3hWYvOC66svPwpGRgoEhM/i.l8WwX9ZOxo1Hi', 'CHOFER');

select id, username, role from users;
