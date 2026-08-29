# Agregar un endpoint de punta a punta

El orden importa: **primero el contrato, después el backend, después el
frontend.** Al revés es como se llega a que la home diga "offline" durante días
sin que nadie sepa por qué.

## 1. Contrato (`docs/API.md`)

Antes de escribir código, anotá en `API.md`: ruta, método, parámetros, forma
exacta del JSON de éxito con un ejemplo real, y los códigos de error con su
forma. Si algo no está claro acá, va a estar peor en el código.

Si es el primer endpoint que devuelve un error de negocio, o el primero que
devuelve un listado, fijate en `API.md` → "Por definir": hay decisiones
transversales (forma del error, paginación, auth) que conviene cerrar ahora.

## 2. Backend (`TIP - Backend`)

Sigue la arquitectura en capas de `AGENTS.md` → "Estructura hoy":

1. **Modelo** (`model/`): una clase con los campos del `API.md` y un
   `toJson()` propio, usando `Json.escape()` de `util/` para lo que venga de
   afuera.
2. **DAO** (`dao/`), si hay acceso a datos: consultá con `PreparedStatement`
   dentro de un try-with-resources dentro de `DatabaseConnection.getConnection()`,
   nunca concatenando valores. Devolvé instancias del modelo, no `ResultSet`
   crudo.
3. **Controller** (`controller/`): arma la respuesta (llama al DAO, arma el
   status code) y la manda con `HttpResponses.sendJson()`.
4. Registralo en `Main.main()`:
   `server.createContext("/api/loquesea", LoQueSeaController::handle);`
5. Probalo con curl o Postman antes de tocar el frontend:
   ```bash
   curl -i http://localhost:8080/api/loquesea
   ```
   Verificá que la respuesta sea **idéntica** a lo que dice `API.md`. Si no lo
   es, corregí el código o corregí el contrato, pero que queden iguales.

## 3. Frontend (`TIP - Frontend`)

1. Escribí el `type` de la respuesta copiando los campos de `API.md`.
2. Hacé el `fetch` contra `${API_BASE_URL}/api/...`, con `API_BASE_URL` saliendo
   de `import.meta.env.VITE_API_BASE_URL`.
3. Manejá los tres estados: cargando, ok, error. El error también es una pantalla.
4. `npm run lint && npm run build`.

## 4. Cierre

- Antes de commitear, pedí una revisión con el subagente `revisor`.
- Commiteá el backend y el frontend por separado, cada uno en su repo.
- Corré `/cierre` para dejar anotado en `STATE.md` qué quedó y qué sigue.
