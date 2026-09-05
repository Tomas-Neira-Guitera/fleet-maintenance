# Ritual de sesión — `/retomar` y `/cierre`

El problema que esto resuelve: entre dos sesiones pueden pasar semanas. Cuando
volvés, no te acordás de en qué quedaste, y la IA arranca de cero cada vez.
`STATE.md` es la memoria del proyecto, y estos dos comandos la leen y la escriben.

**La regla de fondo: la IA propone el estado, Guido lo confirma.** Nunca se
escribe `STATE.md` sin confirmación explícita. Un estado escrito solo es un
estado en el que no se puede confiar, y un estado en el que no se puede confiar
no sirve para nada.

Los comandos viven en `.claude/commands/` de este repo, así que funcionan con
Claude Code abierto **parado en `TIP - Backend`**.

---

## `/retomar` — al abrir la sesión

El comando ya te trae inyectados los commits recientes y el trabajo sin
commitear de los dos repos. Con eso:

1. **Leé `docs/STATE.md` completo.** Es el punto de partida.
2. **Contrastá con el git que te llegó inyectado.** ¿Hay commits posteriores a la
   última anotación del Historial? ¿Hay trabajo sin commitear que el estado no
   menciona? Si la última anotación es de hace mucho, decilo con la fecha.
3. **Situá en no más de 10 líneas:**
   - dónde quedó el trabajo, en criollo
   - qué es lo siguiente según el estado
   - qué encontraste en git que el estado **no** dice (esto es lo más valioso:
     es exactamente lo que se pierde entre sesiones)
   - si hay decisiones abiertas que bloquean lo que sigue, nombralas
4. **Si algo no cierra, preguntá antes de arrancar.** Un estado desactualizado
   se arregla en 30 segundos preguntando y cuesta una sesión entera si no se
   pregunta.
5. **No escribas nada.** `/retomar` solo lee.

Antes de correrlo: `git pull` en los dos repos, si trabaja alguien más.

---

## `/cierre` — al terminar

El comando ya te trae el `status`, el `diff --stat` y los commits del día.

1. **Redactá el bloque de estado** con las secciones de abajo, en base a lo que
   pasó en la sesión, no a lo que decía el estado anterior.
2. **Mostrámelo entero y detenete.** No edites `STATE.md` todavía.
3. **Esperá mi confirmación.** Puedo corregirte: el resumen es mío, vos lo
   redactás. Si corrijo algo, rehacé el bloque completo y mostralo de nuevo.
4. **Recién con el OK, escribí `docs/STATE.md`:** reemplazá las secciones "Dónde
   estamos", "En qué quedé", "Qué sigue" y "Decisiones abiertas"; **agregá** —no
   reemplaces— una línea al Historial y, si corresponde, a Callejones sin salida.
5. **Sugerime el commit, no lo hagas vos.** Con este formato:
   ```
   git add docs/STATE.md
   git commit -m "docs: estado <AAAA-MM-DD>"
   ```
   El commit de estado va **solo**, sin mezclar con código, directo a `main`.
   Así el historial del backend queda legible aunque tenga estos commits de más.

---

## Secciones de `STATE.md`

| Sección | Qué va | Se reemplaza o se agrega |
|---|---|---|
| **Dónde estamos** | 2-3 líneas: el estado del proyecto para alguien que vuelve después de un mes | reemplaza |
| **En qué quedé** | lo último que se tocó, con nombre de archivo. Incluye lo que quedó a medias | reemplaza |
| **Qué sigue** | el próximo paso concreto, no un objetivo vago. "Agregar `GET /api/vehiculos`", no "avanzar con flota" | reemplaza |
| **Decisiones abiertas** | lo que hay que decidir antes de seguir, y qué bloquea cada una | reemplaza |
| **Callejones sin salida** | lo que se probó y no funcionó, **con el motivo** | agrega |
| **Historial** | una línea por sesión: fecha + qué se hizo | agrega |

**Callejones sin salida es la sección que salva las pausas largas.** Sin ella,
volvés en tres semanas y perdés medio día reintentando algo que ya habías
descartado. Una línea alcanza: qué probaste, por qué no anduvo.

**Historial es de una línea por sesión, y punto.** Si crece a párrafos deja de
leerse. Cuando pase el año, se archivan las líneas viejas al final del archivo.

---

## Si trabaja más gente en el repo

- El commit de estado va solo y directo a `main`.
- Si hay conflicto en el Historial, se resuelve **quedándose con las dos
  líneas**: las dos sesiones pasaron.
- `git pull` antes de `/retomar`, siempre.

---

## Si no usás los comandos

El ritual no depende de la herramienta. Pegarle a cualquier asistente el
contenido de `STATE.md` y pedirle que redacte el bloque de cierre funciona igual:
los archivos son markdown plano justamente para eso.

---

# Referencia rápida — qué es cada cosa y cuándo usarla

## Se cargan solos en cada sesión

**`CLAUDE.md`** (raíz de este repo) — el archivo que hace que todo lo demás
funcione. Importa `AGENTS.md`, `docs/PROJECT.md`, `docs/STATE.md` y
`docs/API.md`, y le dice a la IA dónde está el frontend.
→ *No le agregues imports.* Cada uno se paga en todas las sesiones, incluso en
las que no lo necesitás. Lo que no hace falta siempre va como lectura a pedido.

**`AGENTS.md`** (uno por repo) — las reglas de código: stack, estructura, qué no
hacer. Es lo que evita que aparezca un framework sin que nadie lo decidiera.
El contenido vive acá y no en `CLAUDE.md` para que lo lea cualquier herramienta.
→ *Si le repetís lo mismo a la IA dos veces, falta una línea acá.*

**`docs/PROJECT.md`** — qué es FleetGuard y **por qué** está construido así. La
parte del "por qué" es la que importa: sin motivos, las decisiones se erosionan.
→ *Cambialo poco, y siempre con fecha y motivo.*

**`docs/STATE.md`** — dónde estás. El único que cambia cada sesión.
→ *No lo edites a mano.* Si algo está mal, corregilo en el borrador de `/cierre`,
así el archivo y la IA quedan alineados.

**`docs/API.md`** — el contrato entre backend y frontend.
→ *Se actualiza antes de escribir el endpoint, no después.* Es la regla que más
tiempo ahorra en este proyecto.

## Se leen a pedido

**`docs/ROADMAP.md`** — qué falta, por hitos.
→ "Leé `docs/ROADMAP.md` y decime qué sigue". Revisalo cada dos o tres sesiones.

**`docs/SETUP.md`** — de cero a los dos repos corriendo.
→ Máquina nueva o alguien que se suma. *Si algo falla la primera vez que se usa,
actualizalo en el momento:* un setup que falla una vez falla siempre.

**`docs/guias/sesiones.md`** (este archivo) — el ritual. Lo lee el comando, no vos.

**`docs/guias/nuevo-endpoint.md`** — el paso a paso de punta a punta.
→ "Seguí `docs/guias/nuevo-endpoint.md` para agregar `GET /api/vehiculos`".
Usalo religiosamente los primeros tres o cuatro endpoints; después el molde ya
está fijado.

## Comandos

**`/retomar`** — lo primero de cada sesión. Llega con el `git log` y el `status`
de los dos repos ya inyectados, los contrasta contra `STATE.md` y sitúa.
→ *Hacé `git pull` antes.* Y prestá atención a lo que encontró en git y el estado
**no** menciona: eso es exactamente lo que se pierde entre sesiones.

**`/cierre`** — lo último. Redacta el bloque de estado, lo muestra y frena.
→ *Leé el borrador de verdad.* Treinta segundos ahí sostienen todo el sistema; si
empezás a confirmar sin leer, `STATE.md` se vuelve ficción en cuatro sesiones.
Corrilo aunque no hayas terminado nada.

## Modos de trabajo

| Modo | Qué hace | Cuándo |
|---|---|---|
| normal | edita y ejecuta | implementar algo que ya está claro |
| **plan mode** (Shift+Tab) | propone sin tocar archivos | tareas con más de un camino, o que cruzan los dos repos |
| subagente **`revisor`** | lee y critica, no puede editar | antes de commitear, o cuando algo "anda pero no me convence" |
| subagente **Explore** | búsqueda rápida en el código | "¿dónde está X?", cuando el proyecto crezca |

Para documentación externa (por ejemplo "¿cómo se hace X con el `HttpServer` del
JDK?") no hace falta nada especial: Claude Code busca en la web solo.

→ *Invocá al `revisor` en una sesión aparte de la que escribió el código.* Un
agente que acaba de escribir algo lo defiende; uno que lo lee de cero lo juzga.
Se invoca pidiéndolo: "revisá esto con el subagente `revisor`".

## Cómo trabajar

**Ciclo de una sesión:** `git pull` → abrir Claude Code **parado en
`TIP - Backend`** → `/retomar` → trabajar → `revisor` → commitear código →
`/cierre` → revisar el borrador → confirmar → commit de estado.

- **Empezá por el contrato, no por el código.** Todo lo que cruce backend y
  frontend arranca en `API.md`.
- **Plan mode antes de implementar cuando la tarea no es obvia.** Corregir un
  plan cuesta un párrafo; corregir una implementación cuesta una sesión.
- **Una tarea por sesión, terminada y commiteada.** Tres cosas a medias hacen un
  `STATE.md` imposible de escribir y peor de leer.
- **Si la IA propone algo que contradice `PROJECT.md`, gana `PROJECT.md`.** Si de
  verdad querés cambiar la decisión, cambiala ahí. Así es como un proyecto "sin
  frameworks" termina con tres: nadie lo decide, se van colando de a uno.
- **Volviendo después de semanas, no arranques a codear apenas termina
  `/retomar`.** Levantá el backend, abrí el frontend, verificá que prende.
- **Pedile que lea antes de proponer.** "Leé `API.md` y `Main.java` antes de
  contestar" es una línea y cambia bastante lo que sale.
- **Nombrá las rutas completas.** `../TIP - Frontend/src/App.tsx`, no `App.tsx`.
  Con dos repos en la misma sesión, la ambigüedad se paga.

**La regla que ordena todo:** si algo tiene que ejecutarse, Claude Code; si solo
hay que decidirlo o escribirlo, Claude en Cowork. El traspaso entre los dos es
"Qué sigue" de `STATE.md`, no copiar y pegar.
