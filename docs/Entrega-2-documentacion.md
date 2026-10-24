# Entrega 2 - Fleet Maintenance

## Resumen Ejecutivo

### Qué se agregó/modificó en esta iteración

La Entrega 2 del proyecto **Fleet Maintenance** cubre los sprints 5, 7 y 9 del tablero de la
Entrega 2 (del 3 al 24 de octubre). La Entrega 1 había cerrado el ciclo defecto → orden de trabajo
→ mantenimiento. En esta entrega el foco estuvo en tres frentes: saber **cuánto cuesta** mantener
la flota, **avisarle a cada persona** lo que le importa sin que tenga que estar mirando la pantalla
correcta, y que los datos que ya carga el equipo (quién maneja, cuántos km tiene el vehículo) sean
**reales y estén al día** sin carga manual.

Este documento lista todas las historias terminadas.

En esta etapa se implementaron y validaron:

- **Sección Gastos**, con todos los gastos de mantenimiento de la flota: totales, reparto por
  categoría, preventivo vs. correctivo, taller propio vs. externo, evolución mensual, gasto por
  vehículo, las órdenes más costosas y el listado completo con buscador. Cada gráfico funciona
  también como filtro del resto de la pantalla.
- **Costo de mantenimiento por vehículo**, en su detalle: total histórico, últimos 12 meses y costo
  por kilómetro, con un acceso a sus gastos.
- **KPIs de gastos en el Resumen**: el gasto del mes con su variación contra el mes anterior y los
  vehículos más caros de los últimos 30 días.
- **Comprobantes y exportación**: cada gasto puede llevar el número y la foto de la factura o el
  ticket, y el listado se exporta a CSV listo para abrir en Excel en español.
- **Notificaciones**: una campanita en el header de los tres roles, push al navegador (llega
  aunque la aplicación esté cerrada) y email. Avisos al admin (defecto bloqueante, orden de
  trabajo finalizada, mantenimientos por vencer y vencidos), al técnico (le asignaron, le sacaron
  o le cancelaron una orden) y al chofer (recordatorio de cerrar el viaje).
- **Configuración de notificaciones**: una sección donde el admin decide, aviso por aviso, si está
  activo, por qué canales sale y a quién le llega, y la anticipación de los avisos de vencimiento,
  que cada plan de mantenimiento puede personalizar.
- **Identidad real del chofer**: las inspecciones y los viajes quedan a nombre del chofer que
  inició sesión (antes salían a nombre de un chofer fijo), y el detalle del vehículo muestra quién
  lo tiene en viaje y desde cuándo.
- **Bloqueo validado en el servidor**: un vehículo con un defecto bloqueante o dado de baja no
  puede iniciar un viaje aunque el pedido no venga de la aplicación.
- **Kilometraje automático**: el km del vehículo se actualiza con las inspecciones, las órdenes de
  trabajo finalizadas y los mantenimientos registrados, con un tope contra errores de tipeo.
- **Usuarios**: tabla con búsqueda y filtros, edición del nombre (el rol queda fijo) y un apartado
  de **Actividad** por período para choferes y técnicos.
- **Mejoras de UI/UX**: calendario rediseñado con la paleta y estados por color, selectores de
  fecha y hora propios, filtro por fecha en Órdenes de trabajo, vista del chofer con un interruptor
  OK / defecto por control (toda la tarjeta es tocable), botones de dictado y foto más visibles, y
  ajustes en Planes de mantenimiento y en el detalle de gastos.
- **Calidad**: el backend pasó de más de 260 a 473 tests, con 94% de cobertura de líneas.

### Decisiones tomadas

#### El chofer sale del token, y el token dura un turno

Hasta la Entrega 1 el frontend mandaba un chofer fijo en todas las inspecciones. Ahora
`POST /api/inspections/{vehicleId}` exige el JWT de un usuario con rol Chofer y toma de ahí quién
hizo la inspección. Inspecciones y viajes guardan el id del usuario, y el nombre se busca al
momento: si un usuario se renombra, su historial se muestra con el nombre nuevo. El token pasó de
60 minutos a 12 horas, para que el chofer no tenga que volver a entrar entre el pre-viaje y el
post-viaje.

#### El servidor decide si un vehículo puede salir

El estado "No disponible" se calculaba en la pantalla del chofer. Ahora lo decide el backend: un
pre-viaje sobre un vehículo con un defecto bloqueante abierto o dado de baja responde 409. El
post-viaje nunca se rechaza por bloqueo, porque un viaje abierto se tiene que poder cerrar. Un
pre-viaje que reporta un bloqueante guarda la inspección y el defecto, y el viaje nace cerrado: el
vehículo no sale.

#### Kilometraje automático con tope de salto

El km que cargan el chofer y el técnico ahora actualiza el del vehículo, así los vencimientos por
km están al día sin usar "Cargar km" todas las semanas. Como el kilometraje no puede bajar, un
error de tipeo (1.500.000 en vez de 150.000) quedaría cargado para siempre: por eso una lectura que
supera la actual en más de 5.000 km no la actualiza. Se descartó permitir corregir hacia abajo.

#### El rol de un usuario no se edita

Cambiar el rol de alguien con historial (un técnico con órdenes, un chofer con viajes) generaba
casos difíciles de explicar. Se decidió que el rol quede fijo desde el alta; si una persona cambia
de función, se le crea otro usuario. El nombre sí se edita: como todo apunta al usuario por su id,
renombrar no rompe nada.

#### Gastos: un solo endpoint y cada gráfico como filtro

`GET /api/expenses` devuelve los gastos del período y el resto (totales, agrupaciones, gráficos) se
calcula en el frontend, porque el volumen de una flota de hasta 200 vehículos lo permite y evita un
endpoint por gráfico. Cada gráfico es además un filtro: al elegir un segmento o un vehículo, todo
lo demás se recalcula, y el gráfico elegido sigue mostrando el reparto completo con la selección
resaltada. "Preventivo" y "correctivo" salen del origen real de la orden de trabajo (un plan o un
defecto); las órdenes creadas a mano se muestran aparte para no distorsionar la comparación.

#### Gráficos propios, con una paleta para series

Los gráficos (dona, barras y columnas) se hicieron en SVG propio en lugar de sumar una librería.
Para las series se agregó un trío de colores (azul, turquesa y ciruela), validado para daltonismo y
contraste. El verde, el ámbar y el rojo de la paleta quedan reservados para estados (al día, por
vencer, vencido), para que un color no signifique dos cosas.

#### Exportar a CSV desde el navegador

El CSV se arma en el frontend con los mismos datos y filtros que se ven en pantalla, así el archivo
tiene exactamente lo que el admin está mirando. El formato está pensado para Excel en español:
punto y coma como separador, coma decimal y UTF-8 con BOM para que se vean los acentos.

#### Notificaciones que nacen de eventos

La lógica de negocio no sabe nada de avisos. El servicio que hace un cambio (finalizar una orden,
guardar una inspección, subir el km) publica un evento, y un listener que corre recién cuando el
cambio se confirma decide a quién avisar. Si la operación se deshace, no queda un aviso de algo que
no pasó; si el aviso falla, la operación igual se completa. Lo que nace del paso del tiempo
(recordatorio de viaje abierto, vencimientos por fecha) lo revisa una tarea programada.

#### Un catálogo fijo de avisos, configurable

Se evaluó dejar que el admin cree reglas libres ("cuando pase X, avisar a Y"). Se eligió un
catálogo fijo de ocho avisos, que son los que el sistema sabe detectar, y que el admin configure
cada uno: si está activo, por qué canales sale (campanita, push, email) y, en los avisos al admin, a
qué roles o usuarios. Los avisos al técnico y al chofer le llegan siempre a la persona involucrada.
Sin configuración guardada rige un valor por defecto (campanita y push, a los admins).

#### Push con el estándar de los navegadores

Para que los avisos lleguen con la aplicación cerrada se usó Web Push, el estándar de los
navegadores, sin contratar un servicio externo. El servidor firma cada envío con un par de claves
propio (VAPID) y el mensaje viaja cifrado por el servicio de push de cada navegador (Google, Mozilla,
Microsoft o Apple). Se activa por dispositivo desde la campanita y se da de baja al cerrar sesión.

#### Email con un servidor de pruebas en desarrollo

El email se manda por SMTP con Spring Mail. En desarrollo se usa Mailpit, un servidor local que
atrapa los emails y los muestra en una página, sin mandar nada a casillas reales. Para producción
alcanza con configurar un servidor real. El email de los usuarios es opcional: quien no lo tiene
recibe los avisos por los otros canales, y la pantalla de configuración avisa quiénes son.

#### Avisos de vencimiento: dos por ciclo y anticipación propia

Un plan avisa una vez cuando entra en la anticipación y otra cuando vence; después no insiste hasta
el próximo ciclo, que empieza cuando se registra el mantenimiento. La anticipación es global (7 días
o 1.000 km por defecto) y cada plan puede tener la suya. Es independiente del estado "por vencer"
del dashboard, que sigue con su umbral fijo, para que configurar un aviso no cambie el health score
de la flota.

#### Descartar un aviso no lo borra

La X de cada aviso lo saca de la campanita y del contador, pero queda guardado. Tocar un aviso lo
marca como leído y lleva a la pantalla de lo que reporta (el vehículo, la orden de trabajo).

#### Selectores de fecha y hora propios

Los selectores nativos del navegador no toman la paleta y se ven distinto en cada sistema. Se
reemplazaron por componentes propios (grilla de mes, horarios cada 15 minutos, teclado), que se usan
en el calendario y en los filtros de período de Gastos, Usuarios y Órdenes de trabajo.

#### El estado del turno se lee por el color

En el calendario, cada turno tenía un color y además un chip con su estado. Se sacó el chip para
ganar espacio en las tarjetas, y la leyenda de colores pasó arriba, junto a la navegación, siempre
visible. El estado sigue disponible en el tooltip y para lectores de pantalla.

### Desafíos técnicos encontrados

- Fechas en UTC vs. hora local: los turnos programados desde las 21:00 aparecían en el día
  siguiente, porque el calendario agrupaba por la fecha UTC del turno. Se pasó a agrupar y a pedir
  los rangos por día local, con un único helper de fechas.
- Un viaje que no se podía cerrar: un pre-viaje con defecto bloqueante abría el viaje y a la vez
  dejaba el vehículo inhabilitado, así que nadie podía hacer el post-viaje. Como cada inspección
  exige un viaje y no hay herramienta de migraciones para relajar esa restricción, la solución fue
  abrir y cerrar el viaje en la misma operación.
- Guardar un aviso después del commit: los avisos corren cuando la operación ya se confirmó, y un
  guardado sumado a esa transacción terminada no se escribía nunca, sin ningún error. Se resolvió
  dándole a cada aviso su propia transacción, y atrapando cualquier error afuera de ella para que un
  aviso fallido no convierta en error una operación que salió bien.
- Push segura: el backend le hace un pedido a la dirección que manda el navegador al suscribirse, así
  que una dirección cualquiera serviría para atacar hosts internos. Solo se aceptan los servicios de
  push conocidos, se validan las claves al suscribir y las suscripciones vencidas se borran solas.
- No repetir avisos de vencimiento: el km cambia por varios caminos (inspecciones, órdenes,
  mantenimientos, carga manual), así que se centralizó en un evento único, y lo ya avisado se guarda
  por asignación, etapa y ciclo para no avisar dos veces.
- CSV que Excel abra bien: separador, decimales, acentos y descripciones con punto y coma, comillas o
  saltos de línea que no rompan las columnas.
- Interacciones táctiles: que toda la tarjeta de un control del checklist y todo el renglón de un
  gasto respondan al toque, sin romper la selección de texto, los controles internos ni el uso con
  teclado y lector de pantalla.
- Trabajo en paralelo entre dos personas: la base de notificaciones se publicó primero como un
  contrato y una interfaz con una implementación provisoria, para que los avisos concretos y la
  campanita avanzaran sin esperar al modelo de datos, y después se completó sin cambiar lo que ya
  la usaba. Cada sprint se integró en una rama de release antes de llegar a `develop`.

---

# User Stories

## Historias incluidas

| Clave | Historia | Sprint |
|---|---|---|
| CAM-85 | Identificar al chofer real en las inspecciones | 5 |
| CAM-87 | Registrar qué chofer abrió y cerró cada viaje | 5 |
| CAM-89 | Validar en el servidor que un vehículo bloqueado no salga | 5 |
| CAM-94 / CAM-124 | Vista del chofer más rápida | 5, 9 |
| CAM-84 | Botones de dictado y foto más visibles | 5 |
| CAM-90 | Kilometraje actualizado desde inspecciones y órdenes | 5 |
| CAM-91 / CAM-114 | Usuarios: edición y actividad | 5, 7 |
| CAM-92 | Filtro por fecha en Órdenes de trabajo | 5 |
| CAM-93 | Desactivar un plan desde su edición | 5 |
| CAM-52 / CAM-110 / CAM-111 / CAM-112 / CAM-113 | Sección Gastos | 5, 7 |
| CAM-104 | Gastos preventivos vs. correctivos | 7 |
| CAM-105 | Costo de mantenimiento en el detalle del vehículo | 7 |
| CAM-106 | KPIs de gastos en el Resumen del admin | 7 |
| CAM-107 | Comprobante adjunto a un gasto | 7 |
| CAM-108 | Exportar gastos a CSV | 7 |
| CAM-122 / CAM-123 | Ajustes del detalle de gastos | 9 |
| CAM-65 / CAM-120 / CAM-121 | Calendario de planificación | 7, 9 |
| CAM-96 | Avisos en la aplicación (campanita) | 9 |
| CAM-97 | Aviso al admin: defecto bloqueante | 9 |
| CAM-98 | Aviso al admin: orden de trabajo finalizada | 9 |
| CAM-99 / CAM-100 | Avisos al técnico sobre sus órdenes | 9 |
| CAM-101 | Recordatorio al chofer de cerrar el viaje | 9 |
| CAM-119 | Notificaciones push al navegador | 9 |
| CAM-117 | Configuración de notificaciones | 9 |
| CAM-118 | Avisos de vencimiento de planes de mantenimiento | 9 |
| CAM-86 / CAM-88 | Bugs corregidos | 5 |

---

## CAM-85 - Identificar al chofer real en las inspecciones

**Sprint:** 5.

### Actor/es
- Chofer
- Admin

### Funcionalidad
Como admin, quiero que cada inspección quede a nombre del chofer que inició sesión, para saber quién
reportó cada defecto.

### Valor aportado
Hasta ahora todas las inspecciones salían a nombre de un chofer fijo. Con esta historia cada defecto
y cada viaje tienen un responsable real, que es la base de la actividad por chofer y de los avisos al
chofer.

### Criterios de aceptación
- Quiero que la inspección, el defecto y el historial del vehículo muestren el nombre del chofer que
  la hizo.
- Solo un chofer con sesión válida puede enviar una inspección; sin sesión responde 401 y con otro
  rol, 403.
- Si mi sesión venció mientras cargaba la inspección, quiero ver un aviso claro con la opción de
  volver a entrar.
- Quiero poder hacer el pre-viaje y el post-viaje de una jornada sin tener que volver a iniciar
  sesión.
- Las inspecciones anteriores conservan el nombre con el que se guardaron.

---

## CAM-87 - Registrar qué chofer abrió y cerró cada viaje

**Sprint:** 5.

### Actor/es
- Admin
- Chofer

### Funcionalidad
Como admin, quiero ver qué chofer tiene cada vehículo en viaje y desde cuándo, para saber a quién
preguntarle.

### Valor aportado
El dato de quién tiene cada vehículo estaba disperso en las inspecciones. Ahora el viaje lo guarda,
y es la base del recordatorio de cerrar el viaje (CAM-101).

### Criterios de aceptación
- Quiero ver en el detalle del vehículo "En viaje · chofer · desde 07/10 08:15".
- Si el post-viaje lo hace otro chofer, el viaje queda abierto por uno y cerrado por el otro.
- Como chofer, quiero ver en el listado de flota si el viaje abierto es mío o de quién es.
- Los viajes anteriores, sin chofer registrado, se siguen viendo sin errores.

---

## CAM-89 - Validar en el servidor que un vehículo bloqueado no salga

**Sprint:** 5.

### Actor/es
- Admin
- Chofer

### Funcionalidad
Como admin, quiero que el servidor rechace un pre-viaje sobre un vehículo que no puede salir, para que
el bloqueo no dependa solo de la pantalla del chofer.

### Valor aportado
Un defecto bloqueante pasa a ser una regla del sistema y no solo una indicación visual: el vehículo
no sale aunque el pedido llegue por otro camino.

### Criterios de aceptación
- Un pre-viaje sobre un vehículo con un defecto bloqueante abierto se rechaza y no abre viaje.
- Un pre-viaje sobre un vehículo dado de baja se rechaza.
- Un post-viaje se acepta siempre, aunque el vehículo tenga un bloqueante: un viaje abierto se tiene
  que poder cerrar.
- Si otro chofer reportó un bloqueante mientras yo cargaba el pre-viaje, quiero ver el motivo y
  volver al listado.
- Al resolverse el defecto, el vehículo vuelve a estar disponible.

---

## CAM-94 / CAM-124 - Vista del chofer más rápida

**Sprint:** 5 y 9.

### Actor/es
- Chofer

### Funcionalidad
Como chofer, quiero completar la inspección con la menor cantidad de toques posible desde el celular,
marcando solo lo que tiene un problema.

### Valor aportado
La mayoría de los controles están bien en cada inspección. Arrancar todo en OK y que toda la tarjeta
responda al toque reduce el tiempo de la inspección, que es lo que hace que el chofer la complete.

### Criterios de aceptación
- Quiero que cada control tenga un solo interruptor y arranque en OK.
- Quiero pasar un control a "Reportar defecto" tocando cualquier parte de su tarjeta, y que se
  despliegue la carga del defecto.
- Con el defecto desplegado, escribir el título o elegir la gravedad no lo cierra; tocar el renglón
  del título lo vuelve a OK.
- Quiero ver un resumen ("6 controles en OK" / "1 de 6 con defecto") en lugar de un contador de
  respondidos.
- El interruptor sigue funcionando con el teclado y el lector de pantalla.

---

## CAM-84 - Botones de dictado y foto más visibles

**Sprint:** 5.

### Actor/es
- Chofer
- Técnico

### Funcionalidad
Como chofer, quiero ver claramente el botón para dictar la descripción de un defecto, para usarlo sin
tener que escribir en el celular.

### Valor aportado
El dictado (Entrega 1) pasaba desapercibido. Con el color principal se usa más, y la foto, que es
obligatoria en un defecto bloqueante, no queda con menos peso que el dictado, que es opcional.

### Criterios de aceptación
- En "Reportar defecto", "Dictar" y "Adjuntar foto" tienen el mismo color (el principal de la app).
- Mientras se dicta, el botón se pone rojo y dice "Detener".
- Lo mismo en el cierre de la orden de trabajo del técnico.
- Si el navegador no soporta el dictado, el botón no aparece.

---

## CAM-90 - Kilometraje actualizado desde inspecciones y órdenes

**Sprint:** 5.

### Actor/es
- Admin
- Chofer
- Técnico

### Funcionalidad
Como admin, quiero que el kilometraje del vehículo se actualice con lo que ya cargan el chofer y el
técnico, para que los vencimientos por km estén al día sin cargarlo a mano.

### Valor aportado
Los planes por km dependen de un kilometraje al día. El dato ya se cargaba en cada inspección y al
cerrar cada orden, pero no se usaba.

### Criterios de aceptación
- Un post-viaje con más km que el vehículo lo actualiza, y un plan que vencía en ese km pasa a
  vencido.
- Una inspección con menos km que el vehículo se guarda igual y el kilometraje no cambia.
- Finalizar una orden de trabajo o registrar un mantenimiento con km también lo actualiza.
- Una lectura que supera la actual en más de 5.000 km no la actualiza, por posible error de tipeo.
- Un vehículo dado de baja no cambia su kilometraje.
- "Cargar km" se mantiene para correcciones manuales.

---

## CAM-91 / CAM-114 - Usuarios: edición y actividad

**Sprint:** 5 y 7.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero gestionar los usuarios desde una tabla clara y ver la actividad de cada uno en un
período, para auditar el trabajo del equipo.

### Valor aportado
Ordena la gestión de accesos y suma una vista de desempeño por persona, sin tener que cruzar
inspecciones y órdenes a mano.

### Criterios de aceptación
- Quiero ver los usuarios en una tabla, con búsqueda por nombre, filtro por rol y la opción de ver
  los desactivados.
- Quiero editar el nombre de un usuario y resetear su contraseña; el rol queda el del alta.
- Quiero desactivar o reactivar a un usuario desde su edición, con confirmación.
- Quiero cargarle un email (opcional), para que reciba los avisos por ese canal.
- Quiero ver, debajo del listado, la actividad del período: viajes y defectos reportados por chofer;
  órdenes finalizadas y abiertas, tiempo promedio de resolución y gasto cargado por técnico.
- El período es configurable y por defecto es el año en curso.

---

## CAM-92 - Filtro por fecha en Órdenes de trabajo

**Sprint:** 5.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero filtrar las órdenes de trabajo por fecha de creación, para encontrar las de un
período sin recorrer todo el listado.

### Valor aportado
Con el uso, el listado de órdenes crece rápido. El filtro por período usa la misma lógica y los mismos
atajos que Gastos y Usuarios, así se aprende una sola vez.

### Criterios de aceptación
- Quiero atajos para "Este mes", "Últimos 3 meses", "Este año" y "Año anterior".
- Quiero poder elegir un rango a mano.
- El filtro se combina con los de estado, vehículo y técnico.

---

## CAM-93 - Desactivar un plan desde su edición

**Sprint:** 5.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero desactivar un plan de mantenimiento desde su edición, para que la lista quede más
limpia y no lo desactive por error.

### Valor aportado
Evita desactivar un plan por un click accidental en la lista, con el mismo criterio que la baja de un
vehículo o la desactivación de un usuario.

### Criterios de aceptación
- Un plan activo muestra solo "Editar" en la lista; "Desactivar plan" está dentro de la edición.
- Si tengo cambios sin guardar, se me avisa que se van a perder antes de desactivar.
- Un plan desactivado se ve en "Ver desactivados" y se puede volver a activar desde ahí.

---

## CAM-52 / CAM-110 / CAM-111 / CAM-112 / CAM-113 - Sección Gastos

**Sprint:** 5 y 7.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero ver todos los gastos de mantenimiento de la flota en un solo lugar, para saber
cuánto cuesta mantener cada vehículo y en qué se va el dinero.

### Valor aportado
Los gastos se cargaban dentro de cada orden de trabajo y solo se veían abriéndola una por una. La
sección los junta y los convierte en información para decidir.

### Criterios de aceptación
- Quiero una pestaña "Gastos" con el total del período y su reparto por categoría (repuestos, mano
  de obra, otros).
- Quiero ver cuánto se gastó en cada vehículo, ordenado de mayor a menor, con su peso sobre el total
  (CAM-110).
- Quiero filtrar por vehículo, y que se recalculen los totales, los gráficos y el listado (CAM-111).
- Quiero ver qué parte del gasto se resolvió en el taller propio y qué parte afuera (CAM-112).
- Quiero ver la evolución por mes (por día si el período es corto) y las órdenes más costosas.
- Quiero un listado con fecha, vehículo, categoría, descripción y monto, con un buscador por
  descripción o número de comprobante, y que desde cada gasto se abra su orden de trabajo (CAM-113).
- Al elegir un segmento de un gráfico, quiero que se filtre el resto de la pantalla.
- Por defecto quiero ver el año en curso, con atajos para otros períodos.
- Sin gastos en el período, quiero ver un estado vacío y no un error.

---

## CAM-104 - Gastos preventivos vs. correctivos

**Sprint:** 7.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero ver cuánto gasto en mantenimiento preventivo y cuánto en reparaciones correctivas,
para saber si el plan preventivo está funcionando.

### Valor aportado
Es la tesis del producto convertida en un número: si el preventivo funciona, el correctivo debería
bajar con el tiempo.

### Criterios de aceptación
- Un gasto de una orden nacida de un plan suma en "preventivo"; uno de una orden nacida de un defecto,
  en "correctivo".
- Las órdenes creadas a mano se muestran aparte y no se mezclan con las otras dos.
- Quiero ver el reparto con su porcentaje, y la evolución mensual separada en los tres tipos.
- Los totales respetan los filtros de período y vehículo.

---

## CAM-105 - Costo de mantenimiento en el detalle del vehículo

**Sprint:** 7.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero ver en el detalle de un vehículo cuánto me costó mantenerlo, para decidir si
conviene seguir reparándolo o reemplazarlo.

### Valor aportado
Pone el costo junto al historial del vehículo, que es donde se toma la decisión sobre esa unidad.

### Criterios de aceptación
- Quiero ver en el detalle del vehículo el total gastado, el de los últimos 12 meses y la cantidad de
  gastos.
- Quiero ver el costo por kilómetro, calculado desde el primer kilometraje registrado.
- Si no hay km suficientes para calcularlo, se muestra "sin datos" y no un número engañoso.
- Un vehículo sin gastos muestra $0 y un mensaje, no un error.
- "Ver gastos" abre la sección Gastos ya filtrada por ese vehículo.

---

## CAM-106 - KPIs de gastos en el Resumen del admin

**Sprint:** 7.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero ver en el Resumen cuánto se gastó este mes y qué vehículos son los más caros, para
detectar desvíos sin tener que entrar a la sección Gastos.

### Valor aportado
Lleva el costo a la pantalla que el admin mira todos los días, junto al estado de la flota y los
vencimientos.

### Criterios de aceptación
- Quiero ver el gasto del mes actual con su variación contra el mes anterior (flecha y porcentaje).
- Quiero ver los 3 vehículos con más gasto en los últimos 30 días, cada uno con un acceso a su
  detalle.
- Al tocar el gasto del mes, quiero ir a Gastos filtrada por el mes actual.
- Si el mes anterior no tuvo gastos, no se muestra un porcentaje roto.
- Sin gastos, el indicador muestra $0 y la lista de vehículos queda vacía con un mensaje.

---

## CAM-107 - Comprobante adjunto a un gasto

**Sprint:** 7.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero adjuntar el número y la foto de la factura o el ticket a cada gasto, para tener
respaldo de lo que se pagó.

### Valor aportado
Cada gasto queda con su respaldo dentro del sistema, sin tener que buscar el papel.

### Criterios de aceptación
- Quiero poder cargar un gasto con o sin comprobante; el número y la foto son opcionales.
- Quiero adjuntar la foto del comprobante al cargar el gasto en la orden de trabajo.
- Un gasto con comprobante muestra un ícono, y la foto se abre en el visor.
- El número de comprobante se puede buscar en Gastos y sale en el CSV.
- No se puede agregar un gasto a una orden finalizada o cancelada.

---

## CAM-108 - Exportar gastos a CSV

**Sprint:** 7.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero descargar los gastos filtrados en un archivo CSV, para pasárselos a quien lleva la
contabilidad o analizarlos en una planilla.

### Valor aportado
Saca los datos del sistema en un formato que cualquiera puede abrir, sin copiar a mano.

### Criterios de aceptación
- El archivo tiene exactamente los gastos que veo con los filtros y la búsqueda aplicados.
- Abierto en Excel en español, las columnas se separan bien y los acentos y la "ñ" se ven bien.
- Una descripción con punto y coma, comillas o salto de línea no rompe las columnas.

---

## CAM-122 / CAM-123 - Ajustes del detalle de gastos

**Sprint:** 9.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero usar el listado de gastos más cómodo: leer completo el texto del buscador y abrir
la orden de trabajo de un gasto tocando cualquier parte de su renglón.

### Valor aportado
Corrige dos fricciones detectadas al usar la sección: no se llegaba a leer que el buscador también
encuentra por número de comprobante, y había que apuntar a una sola columna para abrir la orden.

### Criterios de aceptación
- Con el cursor encima o el foco en el buscador vacío, el texto de ayuda se desplaza hasta leerse
  completo (CAM-122). Si el sistema pide reducir el movimiento, no se anima.
- Un click en cualquier parte del renglón abre la orden de trabajo del gasto (CAM-123).
- El ícono del comprobante sigue abriendo solo la foto, y seleccionar texto no abre la orden.

---

## CAM-65 / CAM-120 / CAM-121 - Calendario de planificación

**Sprint:** 7 y 9.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero que el calendario de planificación se vea y se use igual que el resto del panel, y
que el estado de cada turno se entienda de un vistazo.

### Valor aportado
El calendario es la pantalla donde se organiza el trabajo del taller. Con estados claros y sin
elementos que sobran, se ve rápido qué está pendiente, qué se atrasó y qué ya se hizo.

### Criterios de aceptación
- El calendario semanal y el mensual usan solo los colores de la paleta.
- Cada turno muestra su estado con un color: programado, atrasado, hecho o cancelado (CAM-65). Los
  turnos hechos y cancelados siguen en su día.
- La leyenda de estados está arriba, junto al botón del mes completo, y se ve siempre (CAM-120).
- Las tarjetas de los turnos no repiten el estado en texto: alcanza con el color (CAM-121).
- Una semana sin turnos muestra un estado vacío con la opción de agendar.
- Las fechas y horas se eligen con selectores propios, con la paleta de la aplicación.
- En el celular la semana se ve en una columna, sin scroll horizontal.

---

## CAM-96 - Avisos en la aplicación (campanita)

**Sprint:** 9.

### Actor/es
- Admin
- Técnico
- Chofer

### Funcionalidad
Como usuario, quiero una campanita en el header que me avise lo que me importa, para enterarme sin
estar mirando la pantalla correcta.

### Valor aportado
Es la base de todos los avisos: dónde se guardan, cómo los lee cada usuario y cómo se configuran los
canales por los que salen.

### Criterios de aceptación
- Quiero ver una campanita con la cantidad de avisos sin leer, que se actualiza sola.
- Al tocarla, quiero ver mis avisos del más nuevo al más viejo.
- Al tocar un aviso, quiero que se marque como leído y me lleve a lo que reporta.
- Quiero poder descartar un aviso con una X, y marcar todos como leídos.
- Solo veo mis avisos: el de otro usuario no se puede leer ni descartar.
- Si un aviso falla al enviarse, la acción que lo originó se completa igual.

---

## CAM-97 - Aviso al admin: defecto bloqueante

**Sprint:** 9.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero enterarme apenas un chofer reporta un defecto bloqueante, para organizar la
reparación antes de que ese vehículo haga falta.

### Valor aportado
Es el aviso más urgente del sistema: un vehículo que quedó sin poder salir.

### Criterios de aceptación
- Cuando un chofer reporta un defecto bloqueante, recibo un aviso con la patente, el defecto, el tipo
  de inspección y quién lo reportó.
- Si la inspección trae varios bloqueantes, recibo un solo aviso que dice cuántos son.
- Un defecto no bloqueante no genera aviso.
- Al tocar el aviso, se abre el detalle del vehículo.

---

## CAM-98 - Aviso al admin: orden de trabajo finalizada

**Sprint:** 9.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero enterarme cuando se finaliza una orden de trabajo, para saber que el vehículo está
listo sin tener que preguntarle al taller.

### Valor aportado
Cierra el circuito de comunicación entre taller y administración.

### Criterios de aceptación
- Cuando se finaliza una orden, recibo un aviso con el título, la patente y quién la hizo (el técnico,
  o el taller si es externa).
- Al tocar el aviso, se abre el detalle de esa orden de trabajo.

---

## CAM-99 / CAM-100 - Avisos al técnico sobre sus órdenes

**Sprint:** 9.

### Actor/es
- Técnico

### Funcionalidad
Como técnico, quiero enterarme cuando me asignan, me sacan o me cancelan una orden de trabajo, para
organizar mi día sin revisar "Mis órdenes" a cada rato.

### Valor aportado
El técnico se entera de los cambios en su trabajo en el momento, incluso con la aplicación cerrada.

### Criterios de aceptación
- Cuando me asignan una orden (al crearla o al reasignarla), recibo un aviso con el título, la patente
  y la fecha planificada si la tiene.
- Si la orden pasa a otro técnico, recibo un aviso de que ya no la tengo, con quién la tiene ahora.
- Si se cancela una orden que tenía a cargo (o la programación de la que salía), recibo un aviso.
- Una orden externa o sin técnico no genera avisos.

---

## CAM-101 - Recordatorio al chofer de cerrar el viaje

**Sprint:** 9.

### Actor/es
- Chofer

### Funcionalidad
Como chofer, quiero que me recuerden cerrar el viaje si me olvidé de hacer el post-viaje, para que el
vehículo no quede trabado con un viaje abierto.

### Valor aportado
Un viaje abierto deja al vehículo "en viaje" y no lo puede usar otro chofer. El recordatorio evita
que eso pase por un olvido.

### Criterios de aceptación
- Si mi viaje lleva más de 12 horas abierto, recibo un recordatorio con la patente y desde cuándo.
- Recibo un solo recordatorio por viaje.
- Si ya cerré el viaje, no recibo el recordatorio.

---

## CAM-119 - Notificaciones push al navegador

**Sprint:** 9.

### Actor/es
- Admin
- Técnico
- Chofer

### Funcionalidad
Como usuario, quiero recibir los avisos como notificación del navegador, aunque la aplicación esté
cerrada, para enterarme en el momento desde el celular o la computadora.

### Valor aportado
La campanita solo sirve con la aplicación abierta. El chofer y el técnico pasan la mayor parte del
tiempo fuera de ella.

### Criterios de aceptación
- Quiero activar las notificaciones en este dispositivo desde el panel de la campanita, dando permiso
  al navegador.
- Quiero recibir los avisos como notificación con la aplicación cerrada, y que al tocarla se abra la
  pantalla del aviso.
- Puedo activarlas en varios dispositivos (la computadora y el celular).
- Al cerrar sesión, ese dispositivo deja de recibir mis avisos.
- Si bloqueé las notificaciones en el navegador, quiero ver cómo volver a habilitarlas.
- Un usuario desactivado no recibe notificaciones.

---

## CAM-117 - Configuración de notificaciones

**Sprint:** 9.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero decidir qué avisos se mandan, por qué canal y a quién, para recibir solo los que me
sirven.

### Valor aportado
Cada empresa tiene su forma de trabajar: algunas quieren todo por email, otras solo lo urgente. Sin
configuración, los avisos terminan ignorados.

### Criterios de aceptación
- Quiero una sección "Notificaciones" en el menú con todos los avisos, agrupados por tema.
- Por cada aviso quiero activarlo o apagarlo y elegir sus canales: campanita, push y email.
- En los avisos al admin quiero elegir los destinatarios: roles enteros y usuarios puntuales.
- Los avisos al técnico y al chofer le llegan siempre a la persona involucrada.
- Cada cambio se guarda en el momento; si falla, vuelve a como estaba y veo el error.
- Quiero ver un ejemplo de cómo se ve cada aviso.
- Si elijo el email y hay destinatarios sin email cargado, quiero ver quiénes son y un acceso a
  Usuarios.
- Quiero poder volver a la configuración por defecto de cada aviso.
- Los emails llegan con el asunto del aviso y un link a la pantalla correspondiente.

---

## CAM-118 - Avisos de vencimiento de planes de mantenimiento

**Sprint:** 9.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero que me avisen cuando un mantenimiento está por vencer y cuando venció, y poder
configurar con cuánta anticipación, para no pasar por alto ninguno.

### Valor aportado
Hasta ahora los vencimientos solo se veían entrando al dashboard. Con el aviso, el mantenimiento
preventivo deja de depender de que alguien se acuerde de mirar.

### Criterios de aceptación
- Recibo un aviso cuando un plan asignado a un vehículo entra en la anticipación, y otro cuando vence.
- No recibo más avisos de ese vencimiento hasta que se registra el mantenimiento y empieza otro ciclo.
- Los vencimientos por fecha se revisan todos los días; los vencimientos por km, cada vez que sube el
  kilometraje del vehículo.
- La anticipación general es de 7 días o 1.000 km, y la puedo cambiar.
- Quiero que cada plan pueda tener su propia anticipación y sus propios canales, o usar la
  configuración general.
- La anticipación del aviso no cambia cuándo el dashboard marca un plan como "por vencer".
- Los vehículos dados de baja y los planes desactivados no avisan.

---

## Bugs corregidos

**Sprint:** 5.

- **CAM-86 - Un pre-viaje con defecto bloqueante dejaba el viaje abierto para siempre.** El vehículo
  quedaba con un viaje abierto y a la vez inhabilitado, así que nadie podía hacer el post-viaje ni
  darlo de baja. Ahora el pre-viaje guarda la inspección y el defecto, y el viaje nace cerrado: el
  vehículo no sale. Un vehículo que ya había quedado así se puede elegir para cerrar su viaje.
- **CAM-88 - El calendario mostraba en el día siguiente los turnos programados desde las 21:00.** El
  calendario agrupaba los turnos por la fecha en UTC, que en Argentina ya es la del día siguiente a
  partir de las 21:00. Ahora se agrupan y se piden por día local, en el calendario semanal, en el
  mensual y en la vista previa al planificar.

---

## Pendiente para la Entrega 3

- **CAM-102 / CAM-103** - Avisos al chofer cuando su vehículo queda no disponible y cuando vuelve a
  estar disponible (requieren definir la relación chofer ↔ vehículo).
- **CAM-109** - Gastos operativos del vehículo fuera de una orden de trabajo.
- **CAM-127** - Vencimientos de la documentación del vehículo (VTV, seguro, habilitación).
- **CAM-130** - Exportar el historial de un vehículo a PDF.
- **CAM-133** - Código QR en cada vehículo para ir directo a su inspección.
- **CAM-134 / CAM-135** - Tiempo fuera de servicio por vehículo y detección de defectos recurrentes.
- **CAM-136** - Tests del frontend.
- **CAM-73** - Validar el JWT en el resto de la API.
