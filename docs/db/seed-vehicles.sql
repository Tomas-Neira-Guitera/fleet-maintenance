-- Datos de prueba para CAM-11 / Postman. Correr con:
--   psql -U postgres -d TIP -f docs/db/seed-vehicles.sql

insert into vehicles (id, plate, brand, model) values
  (gen_random_uuid(), 'AB123CD', 'Mercedes-Benz', 'Sprinter'),
  (gen_random_uuid(), 'AC456EF', 'Iveco', 'Daily'),
  (gen_random_uuid(), 'AD789GH', 'Ford', 'Cargo 1723'),
  (gen_random_uuid(), 'AE234JK', 'Toyota', 'Hilux'),
  (gen_random_uuid(), 'AF567LM', 'Scania', 'R450');

select id, plate, brand, model from vehicles;
