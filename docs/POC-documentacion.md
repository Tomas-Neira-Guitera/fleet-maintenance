# PoC - Fleet Maintenance

## Resumen Ejecutivo

### Qué se agregó/modificó en esta iteración

Durante la PoC (Proof of Concept) del proyecto **Fleet Maintenance** se desarrolló un recorte
funcional de la plataforma de mantenimiento preventivo de flotas, con el objetivo de
validar la viabilidad técnica y de producto antes de avanzar a un MVP completo, y de
sostener una presentación a personas interesadas en evaluar si la idea tenía sentido para
llevar al mercado.

En esta etapa se implementaron y validaron:

- **Autenticación por roles**, con login real (`ADMIN` / `CHOFER`) y JWT emitido por el
  backend, como base para diferenciar la experiencia de chofer y de administración.
- **Inspección de vehículo (DVIR) pre-trip y post-trip**, con checklist fijo, reporte de
  defectos con severidad (bloqueante / no bloqueante), foto de evidencia y resolución
  100% server-side del estado del vehículo (disponible / en viaje), sin que el cliente
  trackee ningún identificador de viaje.
- **Gestión de defectos**, con listado ordenado por gravedad y fecha, visible para el
  equipo de mantenimiento apenas se envía una inspección con algún problema reportado, y
  un widget de "defectos abiertos recientes" en el dashboard de admin.
- **Alta, edición y baja de vehículos de la flota**, con sus datos principales (patente
  única, marca, modelo, año, chasis, tipo) y kilometraje, con baja lógica que preserva el
  historial de cada vehículo.
- **Modelo de mantenimiento preventivo**, con catálogo de planes (qué se mantiene y cada
  cuánto, por km y/o por tiempo), asignación/desasignación de planes a vehículos,
  registro de mantenimientos realizados y cálculo automático de próximo vencimiento y
  estado (al día / por vencer / vencido).
- **Dashboard de estado de flota**, con el health score y el anillo circular del design
  system, próximo mantenimiento más urgente por vehículo, y widget de defectos abiertos
  recientes.
- **Programación de mantenimientos**, con calendario semanal (y modal de vista mensual)
  para fijar cuándo se va a atender un mantenimiento o un defecto, ya sea disparado desde
  el estado de flota, desde un defecto puntual o cargado manualmente desde el calendario.
- **Ajustes de UI** para separar claramente la experiencia mobile-first del chofer (sin
  la pantalla de defectos, con indicación de vehículo no disponible por defecto
  bloqueante) del shell de administración a ancho de escritorio, con navegación por menú
  hamburguesa.
- **Data initializer (seed de datos)** para disponer de una flota, choferes y planes de
  mantenimiento de ejemplo, y poder mostrar la aplicación con datos consistentes durante
  la presentación.

### Decisiones tomadas

#### Validación temprana de funcionalidades críticas

Se priorizó implementar el flujo central del producto —inspección del chofer → defecto
detectado → visibilidad para mantenimiento → mantenimiento preventivo programado— por ser
la parte de mayor riesgo de producto: es la que determina si la propuesta de valor
("de WhatsApp y Excel a un sistema simple") realmente resuelve el dolor de la empresa
tipo, antes de invertir en funcionalidades secundarias (órdenes de trabajo formales,
alertas por email, multiempresa).

#### Severidad de defectos simplificada a dos niveles

Se definió `blocking` / `non-blocking` en lugar de una escala de tres niveles
(bajo/medio/alto), para mantener el reporte del chofer con la menor fricción posible y
una regla de validación simple (foto obligatoria solo si es bloqueante).

#### El servidor es dueño de la lógica de negocio

Tanto el catálogo del checklist DVIR como el estado del vehículo (disponible/en viaje),
el health score y el estado de cada plan de mantenimiento (al día/por vencer/vencido) se
calculan y resuelven en el backend. El cliente nunca manda ni asume esta lógica —
decisión tomada para no duplicar reglas de negocio entre frontend y backend y evitar
inconsistencias tempranas.

#### Autenticación real, pero sin proteger todavía los endpoints

Se implementó login real con JWT para poder mostrar la diferenciación de roles en la
presentación, aceptando conscientemente que el backend todavía no valida ese token en el
resto de los endpoints (se sigue identificando al chofer con el header temporal
`X-Driver-Id`). Fue una decisión de alcance para la PoC: mostrar el flujo de acceso
diferenciado sin invertir todavía en el filter chain completo de Spring Security.

#### Checklist configurable por accesorios, descartado por ahora

Se evaluó permitir un checklist distinto según los accesorios del vehículo (faja, traca,
grúa, rampa) y se decidió dejarlo fuera de esta etapa: el checklist pre-trip de la PoC es
una lista fija, para no sumar complejidad de modelado antes de validar que el flujo base
funciona.

#### Creación de datos iniciales para desarrollo y demo

Se incorporó un data initializer para simplificar las pruebas funcionales durante el
desarrollo y para poder mostrar la aplicación con una flota y un historial de ejemplo
coherente frente a las personas interesadas, sin depender de carga manual previa.

### Desafíos técnicos encontrados

- Modelar el estado de "viaje" de un vehículo (abierto/cerrado) sin que el cliente lo
  trackee, resolviendo enteramente en el backend si corresponde pre-trip o post-trip.
- Separar el evento de "mantenimiento realizado" (historial que se acumula) del estado
  "próximo vencimiento" (cache derivado, para que el dashboard liste toda la flota sin
  recalcular en cada request).
- Definir una regla de urgencia comparable entre vencimientos por kilómetros y por
  tiempo (porcentaje del intervalo consumido), para poder ordenar el dashboard con un
  solo criterio.
- Convivencia temporal de dos mecanismos de identificación del chofer (JWT de login real
  y header temporal `X-Driver-Id`) mientras no existe todavía la validación completa del
  token en el backend.
- Ajustar el layout compartido entre la vista mobile-first del chofer y el dashboard de
  administración de escritorio, que originalmente heredaban el mismo contenedor con un
  ancho máximo pensado solo para mobile.
- Verificación limitada del entorno de desarrollo: el build y los tests reales del
  backend (Gradle) requieren correrse siempre en la máquina del desarrollador, y no fue
  posible en todos los casos validar visualmente los cambios de frontend contra un
  navegador real antes de la demo.

---

# User Stories

## CAM-43 / CAM-45 - Autenticación por roles

### Actor/es
- Usuario (Admin, Chofer)

### Funcionalidad
Como usuario, quiero iniciar sesión con mis credenciales para acceder a la aplicación con
los permisos correspondientes a mi rol.

### Valor aportado
Permite diferenciar la experiencia de chofer y de administración desde el ingreso a la
aplicación, y sienta la base para proteger la información de cada rol más adelante.

### Criterios de aceptación
- Quiero poder iniciar sesión con usuario y contraseña y recibir un token de acceso.
- Quiero que la aplicación me lleve a la vista que corresponde a mi rol (chofer o admin).

---

## CAM-11 - Formulario de inspección DVIR desde móvil

### Actor/es
- Chofer

### Funcionalidad
Como chofer, quiero completar un checklist de inspección rápido desde mi celular (antes
y después de manejar) para reportar el estado del vehículo.

### Valor aportado
Reemplaza el reporte informal por WhatsApp o papel por un registro estructurado y
visibilidad inmediata para el equipo de mantenimiento.

### Criterios de aceptación
- Quiero ver el listado de vehículos de la flota con su estado (disponible / en viaje).
- Quiero que se me abra automáticamente el formulario correspondiente (pre-trip o
  post-trip) según el estado del vehículo que elijo.
- Quiero poder marcar cada ítem del checklist como OK o reportar un defecto.
- Quiero que, al enviar el post-trip, el vehículo vuelva a quedar disponible para otro
  chofer.

---

## CAM-12 - Reporte de defectos con fotografía

### Actor/es
- Chofer

### Funcionalidad
Como chofer, quiero reportar un defecto indicando su gravedad (bloqueante / no
bloqueante) y adjuntar una foto como evidencia para que el equipo de mantenimiento
entienda el problema.

### Valor aportado
Da evidencia visual y una clasificación clara de urgencia a cada problema reportado, en
vez de una descripción suelta por WhatsApp.

### Criterios de aceptación
- Si reporto un defecto, quiero indicar su gravedad (bloqueante / no bloqueante).
- Si el defecto es bloqueante, el sistema me exige adjuntar una foto antes de dejarme
  enviar la inspección; si es no bloqueante, la foto es opcional.
- Quiero poder cargar la foto en un paso separado y liviano, pensado para conexión
  inestable en movimiento.

---

## CAM-13 - Gestión de defectos reportados

### Actor/es
- Mantenimiento

### Funcionalidad
Como técnico de mantenimiento, quiero ver un listado de defectos ordenados por gravedad
y fecha para priorizar qué intervenciones atender primero.

### Valor aportado
Da visibilidad inmediata y centralizada de los problemas de la flota, sin depender de
que alguien reenvíe o busque el mensaje original.

### Criterios de aceptación
- Quiero ver todos los defectos reportados, ordenados primero por gravedad y luego por
  fecha de reporte (más reciente primero).
- Cada defecto en el listado muestra gravedad, fecha, descripción y patente del
  vehículo asociado.
- Si no hay defectos reportados, quiero ver un listado vacío, no un error.

---

## CAM-37 - Visualizar los defectos reportados en el panel de admin

### Actor/es
- Admin

### Funcionalidad
Como administrador, quiero ver los defectos abiertos más recientes en el dashboard,
para priorizar qué revisar sin tener que entrar al listado completo de defectos.

### Valor aportado
Da a quien administra la flota una vista rápida de lo más urgente apenas entra al
dashboard, sin un paso extra de navegación.

### Criterios de aceptación
- Quiero ver un widget de "Defectos abiertos recientes" con hasta 3 defectos: gravedad,
  patente, descripción y quién y cuándo lo reportó.
- Quiero que se respete el mismo orden que el listado completo (bloqueante primero,
  luego más reciente).
- Quiero un link "Ver todos" que me lleve al listado completo (CAM-13), con forma de
  volver al dashboard.

---

## CAM-25 - Carga y gestión de flota

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero cargar la información de los vehículos (patente, marca, modelo, año,
número de chasis, tipo) y su kilometraje inicial para comenzar a hacer seguimiento.

### Valor aportado
Es la base de datos maestra de la flota sobre la que corren el resto de las
funcionalidades (inspecciones, defectos, mantenimiento preventivo).

### Criterios de aceptación
- Quiero dar de alta un vehículo desde el panel admin, con patente, marca y modelo
  obligatorios, y tipo/año/chasis/kilometraje opcionales.
- Quiero que la patente sea única en toda la flota (sin distinguir mayúsculas de
  minúsculas).
- Quiero poder editar los datos de un vehículo ya cargado.
- Quiero poder dar de baja un vehículo (baja lógica, sin perder su historial) y
  reactivarlo después; no debería poder dar de baja uno con un viaje abierto.
- Un vehículo dado de baja no debería aparecer para el chofer ni en el estado de flota.
- Quiero poder actualizar el kilometraje de un vehículo ya existente en cualquier
  momento, no solo al darlo de alta.

---

## CAM-16 - Interfaz para definir planes de mantenimiento preventivo a un vehículo

### Actor/es
- Mantenimiento

### Funcionalidad
Como jefe de mantenimiento, quiero definir planes preventivos (ej: cambio de aceite
cada 10.000 km o 6 meses) y asignarlos a los vehículos que correspondan, para
automatizar el seguimiento.

### Valor aportado
Permite pasar de un mantenimiento reactivo a uno planificado, sin depender de que
alguien recuerde manualmente cuándo toca cada intervención.

### Criterios de aceptación
- Quiero ver, por vehículo, qué planes de mantenimiento tiene asignados.
- Quiero asignar un plan existente del catálogo a un vehículo, o crear uno nuevo
  (nombre, categoría opcional, intervalo por km/tiempo/ambos) y asignarlo en el mismo
  paso.
- Quiero poder desasignar un plan de un vehículo.
- Quiero marcar un mantenimiento como hecho (fecha, kilometraje, notas), y que el
  próximo vencimiento se recalcule automáticamente.
- Quiero un catálogo de planes editable de forma independiente (crear, editar,
  desactivar/reactivar, borrar), bloqueando el borrado si el plan tiene alguna
  asignación.

---

## CAM-20 / CAM-40 - Dashboard de estado de flota

### Actor/es
- Admin / Supervisor

### Funcionalidad
Como supervisor, quiero ver un dashboard con el estado general de la flota (vehículos al
día / por vencer / vencidos) y acceso rápido a los que están en riesgo, para
anticiparme a los problemas.

### Valor aportado
Da, de un vistazo, la respuesta a "¿qué vehículo está crítico hoy y por qué?", sin tener
que revisar vehículo por vehículo.

### Criterios de aceptación
- Quiero ver el estado de cada vehículo (al día / por vencer / vencido) con su health
  score en el anillo circular del design system.
- Quiero ver, por vehículo, el próximo mantenimiento más urgente (el de mayor
  severidad, y entre empates, el de mayor porcentaje de intervalo consumido).
- Quiero que el estado y el health score vengan calculados desde el backend, no
  recalculados en el cliente.

---

## CAM-42 / CAM-50 / CAM-51 - Programación de mantenimientos

### Actor/es
- Mantenimiento

### Funcionalidad
Como responsable de mantenimiento, quiero programar en un calendario cuándo voy a
atender un mantenimiento o un defecto, ya sea a partir de un vencimiento, de un defecto
puntual, o de forma manual.

### Valor aportado
Da una vista unificada de la carga de trabajo de taller por semana, sin importar si la
programación nace de un plan de mantenimiento, de un defecto o de una decisión manual.

### Criterios de aceptación
- Quiero ver un calendario semanal con lo que ya está programado, con acceso a una vista
  mensual.
- Quiero programar un mantenimiento desde el estado de flota, desde un defecto abierto,
  o directamente desde un día del calendario.
- Quiero ver, al elegir fecha y hora, qué más hay programado ese día para no
  sobrecargar el taller.

---

## CAM-49 - Ajustes de UI: vista de chofer y shell de administración

### Actor/es
- Chofer, Admin

### Funcionalidad
Como usuario, quiero que la interfaz esté ordenada según mi rol: simple y mobile-first
si soy chofer, a ancho de escritorio y con navegación clara si soy administrador.

### Valor aportado
Mejora la experiencia de uso real en el dispositivo de cada rol y evita que una vista
pensada para celular se vea comprimida en escritorio (o viceversa).

### Criterios de aceptación
- Como chofer, quiero un flujo reducido a listado de vehículos e inspección, sin
  pantallas que no uso.
- Como chofer, quiero ver claramente marcado un vehículo "no disponible" si tiene un
  defecto bloqueante abierto.
- Como admin, quiero navegar entre secciones (Resumen, Vehículos, Planes de
  Mantenimiento) desde un menú hamburguesa, con el dashboard a ancho completo.

---

## CAM-35 - Conectar front y back

### Objetivo
Integrar el frontend (React/Vite) con la API real del backend, dejando de depender de
datos mock para cualquier pantalla de la aplicación.

### Valor aportado
Es la base técnica sin la cual ninguna de las demás historias se podía mostrar de punta
a punta en la presentación.

---

## T - Crear data initializer

### Objetivo
Crear un mecanismo para inicializar datos del sistema (flota, choferes, planes de
mantenimiento) y facilitar el desarrollo, el testing y la demo de la aplicación.

### Valor aportado
Reduce tiempos de setup, mejora la consistencia de las pruebas durante el desarrollo, y
permitió mostrar la aplicación con datos realistas durante la presentación a los
interesados.

### Resultado esperado
- Disponibilidad de datos iniciales de flota, choferes y planes de mantenimiento.
- Simplificación de pruebas funcionales.
- Menor dependencia de carga manual de información antes de una demo.
