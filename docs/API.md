# Contrato de API — FleetGuard

Fuente de verdad de qué endpoints existen y qué devuelven. **El backend no
expone nada que no esté acá, y el frontend no consume nada que no esté acá.**

Base URL en desarrollo: `http://localhost:8080`
El frontend la toma de `VITE_API_BASE_URL`.

Convenciones: todas las rutas cuelgan de `/api`. Respuestas siempre JSON con
`Content-Type: application/json`. Los errores devuelven un objeto con al menos
la clave `error`.

---

## ⚠️ Desalineación abierta

`src/App.tsx` del frontend llama a **`GET /api/ping`** y espera
`{ status, service, timestamp }`. Ese endpoint **no existe**: el backend expone
`/api/health` y devuelve `{ status, database, timestamp }`. Efecto: el indicador
de conexión de la home dice "offline" aunque el backend esté perfectamente
levantado.

Resolución acordada: **manda el backend**. Hay que corregir `App.tsx` para que
llame a `/api/health` y tipar la respuesta como está documentada abajo.
Está anotado como primera tarea en `ROADMAP.md`.

---

## Endpoints

### `GET /api/health`

Chequea que el servidor esté arriba y que la conexión a Postgres funcione.

**Request** — sin parámetros, sin body.

**200 OK**
```json
{
  "status": "UP",
  "database": "TIP",
  "timestamp": "2026-08-28T14:32:07.481Z"
}
```

| Campo | Tipo | Qué es |
|---|---|---|
| `status` | string | `"UP"` cuando la conexión a la base funciona |
| `database` | string | nombre del catálogo devuelto por la conexión JDBC |
| `timestamp` | string | `Instant.now()` en ISO-8601 UTC |

**500 Internal Server Error** — no se pudo conectar a Postgres
```json
{
  "status": "DOWN",
  "error": "mensaje de la SQLException"
}
```

Implementado en `Main.handleHealth`.

---

## Por definir

Cada uno de estos se documenta acá **en el mismo cambio** en que se implementa,
nunca después:

- Login y sesión — `POST /api/auth/login`, forma del token, cómo viaja
- Flota — alta, baja, listado y detalle de vehículos
- Inspecciones — crear, listar, ver resultado
- Defectos — los que salen de una inspección
- Órdenes de trabajo — abrir, asignar, cerrar
- Mantenimiento preventivo — planes y vencimientos

Antes de agregar el primero hay que cerrar tres decisiones transversales, porque
después son caras de cambiar: **forma del error** (qué claves tiene siempre),
**paginación** (si los listados la tienen y cómo), y **autenticación** (qué
header, qué pasa cuando falta). Están anotadas en `STATE.md` como decisiones
abiertas.

---

## Cuándo migrar a OpenAPI

Cuando pase cualquiera de estas: más de ~10 endpoints estables, el primer bug
causado por un campo desalineado, o la necesidad de generar tipos TypeScript.
Hasta entonces este markdown alcanza y cuesta menos mantenerlo.
