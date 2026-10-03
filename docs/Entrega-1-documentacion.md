# Entrega 1 - Fleet Maintenance

## Resumen Ejecutivo

### Qué se agregó/modificó en esta iteración

La Entrega 1 del proyecto **Fleet Maintenance** cubre los sprints 4 y 5. La PoC había validado
el flujo inspección del chofer → defecto → visibilidad para mantenimiento → mantenimiento
programado. En esta entrega se cerró ese ciclo: el defecto ahora termina en una orden de
trabajo que un técnico resuelve desde su celular, y al finalizarla el defecto queda
resuelto.

Este documento lista todas las historias terminadas.

En esta etapa se implementaron y validaron:

- **Órdenes de trabajo**, generadas desde un defecto, desde una programación del
  calendario o de forma manual, con responsable, estados (asignada → en proceso →
  finalizada, o cancelada), fotos del trabajo y descripción de cierre. Al finalizar una
  orden se cierra su origen: el defecto pasa a resuelto, la programación a realizada y,
  si viene de un plan, se registra el mantenimiento y se recalcula el próximo vencimiento.
- **Ejecución interna o externa y gastos**, para indicar si el trabajo lo hace el taller
  propio o un proveedor, y cargar los gastos de cada orden (repuestos, mano de obra,
  otros) con su total.
- **Rol Técnico con vista propia**, mobile-first como la del chofer: "Mis órdenes" con
  los defectos bloqueantes primero, y una pantalla para empezar el trabajo, cargar fotos
  y finalizar.
- **Gestión de usuarios y roles**, con alta, edición y desactivación desde el panel de
  admin, y validación del JWT con rol `ADMIN` en los endpoints de usuarios.
- **Checklist de inspección configurable por vehículo**, para agregar o quitar ítems del
  pre-trip según lo que corresponde a cada unidad.
- **Descripciones por audio**, con dictado por voz para la descripción del defecto
  (chofer) y para el cierre de la orden de trabajo (técnico). El defecto pasa a tener
  título y descripción.
- **Detalle e historial de vehículo**, con la ficha, las inspecciones, los defectos, los
  mantenimientos realizados y las órdenes de trabajo de cada unidad.
- **Mejoras en la programación de mantenimientos**: planificar genera la orden de trabajo
  en el mismo paso, replanificar no la duplica, cancelar una programación cancela su
  orden abierta, y cada programación tiene un detalle para editarla o eliminarla.
- **Mejoras en el dashboard**: tarjetas de resumen de la flota, vista de próximos
  vencimientos por franja de días, y scroll en "Estado de la flota" y "Defectos abiertos
  recientes" para ver todos los registros.
- **Ajustes de UI**: títulos limitados a 30 caracteres con contador, calendario sin
  desbordes, visor de fotos a pantalla completa y la pantalla del admin reflejada en la
  URL.
- **Calidad e infraestructura**: integración continua del backend con GitHub Actions,
  más de 260 tests con un mínimo de 85% de cobertura de líneas, y un seed de datos demo
  con seis meses de operación simulada.

### Decisiones tomadas

#### Cerrar el ciclo del defecto con órdenes de trabajo

Se priorizó completar el recorrido de punta a punta antes de sumar funcionalidades
nuevas: sin órdenes de trabajo, un defecto reportado no tenía forma de resolverse dentro
del sistema. La orden de trabajo es la única vía para resolver un defecto, y al
finalizarla el servidor cierra automáticamente lo que le dio origen. Cancelar una orden
no dispara ese cierre, para que el trabajo se pueda volver a atender con otra orden.

#### Planificar y generar la orden de trabajo en un solo paso

En lugar de dos acciones separadas (poner fecha y después crear la orden), "Planificar"
pide la fecha y el responsable y genera las dos cosas. Se definió además que haya una
sola orden abierta por origen: replanificar mueve la fecha y mantiene la orden
existente, para no duplicarle el trabajo al técnico.

#### Vista propia para el técnico

El técnico tiene su propio shell, mobile-first y con el mismo molde que la vista del
chofer (una lista y una pantalla de trabajo), en lugar de reutilizar el panel de admin
con pestañas filtradas. Trabaja desde el celular, al lado del vehículo.

#### Checklist por vehículo con ítems libres

En la PoC se había descartado un checklist distinto según los accesorios del vehículo.
En esta entrega se retomó con una variante más simple: en lugar de modelar un catálogo
de accesorios, el admin agrega o quita ítems por vehículo sobre el checklist base. Solo
se configura el pre-trip, y el ítem de kilómetros actuales no se puede quitar.

#### Dictado con el reconocimiento de voz del navegador

Para las descripciones por audio se evaluaron tres opciones: el reconocimiento de voz del
navegador (Web Speech API), transcribir desde el backend con un servicio externo pago, y
un modelo propio. Se eligió la primera por no tener costo y no necesitar backend ni
claves. No se guarda el audio, solo el texto, que siempre queda editable. El dictado se
usa solo en descripciones; los títulos se escriben a mano.

#### Protección de la API en forma incremental

Al publicar el alta de usuarios, dejar esos endpoints abiertos habría permitido que
cualquiera se creara un usuario administrador. Por eso en esta entrega se armó la
validación del JWT y se aplicó solo a `/api/users` (exige un `ADMIN` activo). El resto de
la API sigue sin validar el token y al chofer se lo sigue identificando con el header
temporal `X-Driver-Id`; extender la protección al resto queda para una historia aparte
(CAM-73), que reutiliza el mismo mecanismo.

#### Título corto y descripción larga

Los títulos de programaciones, órdenes de trabajo, planes y defectos se limitaron a 30
caracteres, porque los títulos largos rompían el calendario y las tablas. Para no perder
detalle, el defecto sumó una descripción larga aparte (hasta 1000 caracteres), que es la
que se puede dictar.

#### Fotos obligatorias al finalizar una orden

La propuesta de producto original planteaba una foto opcional al cerrar una orden. Se
decidió exigir al menos una, y permitir varias, para dejar evidencia del trabajo hecho.

#### Tests y cobertura mínima como condición del build

Se sumó integración continua al backend y se fijó un mínimo de 85% de cobertura de
líneas: si un test falla o la cobertura baja, el build queda en rojo antes de mergear. Se
priorizaron tests que protegen una regla de negocio concreta por sobre la cantidad.

#### Ruteo propio en el frontend

Para que la pantalla del admin quede en la URL se usó la History API del navegador en
lugar de sumar una librería de ruteo: son pocas rutas planas y no hacía falta más.

### Desafíos técnicos encontrados

- Cerrar el origen de una orden de trabajo sin duplicar lógica: al finalizar se
  reutiliza el registro de mantenimiento realizado que ya existía, que recalcula el
  próximo vencimiento y cierra la programación. Hubo que evitar además una dependencia
  circular entre el service de órdenes de trabajo y el de programaciones.
- Evitar órdenes de trabajo duplicadas al replanificar o al cancelar y volver a
  programar, identificando la orden abierta por su origen de fondo (el defecto o la
  asignación del plan) y no solo por la programación.
- Hacer configurable el checklist sin alterar el historial: cada respuesta de inspección
  guarda el nombre del ítem, para que las inspecciones viejas se sigan viendo igual
  aunque el ítem se quite o se elimine.
- Comportamiento desparejo del dictado por voz entre navegadores: en Android el modo
  continuo repite frases y hubo que encadenar sesiones cortas; no funciona en Firefox;
  necesita conexión y una página servida por HTTPS, lo que obligó a sumar un modo de
  desarrollo con HTTPS para probar desde un celular.
- Devolver errores de autenticación legibles para el navegador: la validación del JWT se
  hizo con un interceptor de Spring MVC y no con un filtro, porque un filtro corre antes
  que CORS y el navegador mostraría el 401 como un error de red.
- Agregar columnas obligatorias a tablas con datos sin herramienta de migraciones: con
  el schema generado por Hibernate, la columna de usuario activo necesitó un valor por
  defecto a nivel columna.
- Bugs que aparecieron al escribir los tests: un path traversal al pedir una foto por
  id, y varios errores del cliente (JSON mal formado, ids inválidos, parámetros fuera de
  rango) que respondían 500 en lugar de 400, 404 o 422.
- Operaciones simultáneas sobre las fotos de una orden: subir o borrar una foto mientras
  se finalizaba podía dejar la orden en un estado inconsistente.
- Desbordes de layout con contenido variable: los títulos largos ensanchaban las
  columnas del calendario, y el scroll de las tablas del resumen tuvo que medirse en
  pantalla porque las filas no tienen todas el mismo alto.
- Integración de trabajo en paralelo: órdenes de trabajo, historial de vehículo y rol
  técnico se desarrollaron en ramas separadas y se unificaron en una rama de integración
  antes de llegar a `develop`.

---

# User Stories

## Historias incluidas

| Clave | Historia | Etapa |
|---|---|---|
| CAM-43 / CAM-45 | Autenticación por roles | PoC, ampliada |
| CAM-67 / CAM-68 | Rol Técnico y vista propia | Entrega 1 |
| CAM-23 | Gestión de usuarios y roles | Entrega 1 |
| CAM-11 | Formulario de inspección DVIR desde móvil | PoC, ampliada |
| CAM-12 | Reporte de defectos con fotografía | PoC, ampliada |
| CAM-31 | Checklist de inspección configurable por vehículo | Entrega 1 |
| CAM-32 | Carga de descripciones por audio | Entrega 1 |
| CAM-13 | Gestión de defectos reportados | PoC, ampliada |
| CAM-37 / CAM-81 | Defectos abiertos recientes en el panel de admin | PoC, ampliada |
| CAM-53 | Ver la foto sin salir de la página | Entrega 1 |
| CAM-14 | Órdenes de trabajo desde defectos | Entrega 1 |
| CAM-62 | Ejecución interna o externa | Entrega 1 |
| CAM-63 | Sección de órdenes de trabajo y gastos | Entrega 1 |
| CAM-60 | Vista de taller con órdenes de trabajo | Entrega 1 |
| CAM-25 | Carga y gestión de flota | PoC |
| CAM-18 | Carga y actualización de kilometraje | Entrega 1 |
| CAM-15 / CAM-22 | Detalle e historial de vehículo | Entrega 1 |
| CAM-16 | Planes de mantenimiento preventivo | PoC |
| CAM-17 | Cálculo automático de vencimientos | Entrega 1 |
| CAM-42 / CAM-50 / CAM-51 | Programación de mantenimientos | PoC, ampliada |
| CAM-77 | Cancelar una programación cancela su orden de trabajo | Entrega 1 |
| CAM-20 / CAM-38 / CAM-40 / CAM-80 | Dashboard de estado de flota | PoC, ampliada |
| CAM-21 | Vista de próximos mantenimientos | Entrega 1 |
| CAM-39 / CAM-49 / CAM-54 | Vista de chofer y shell de administración | PoC, ampliada |
| CAM-79 | Límite de caracteres en los títulos | Entrega 1 |
| CAM-35 | Conectar front y back | PoC |
| T | Data initializer y seed de datos demo | PoC, ampliada |
| CAM-30 | Integración continua del backend | Entrega 1 |
| CAM-78 | Tests del backend | Entrega 1 |
| CAM-82 | Ruteo por URL en el panel de admin | Entrega 1 |
| CAM-58 | Evaluar rediseño de UI | Entrega 1 |
| CAM-57 / CAM-61 / CAM-74 / CAM-75 | Bugs corregidos | Entrega 1 |

---

## CAM-43 / CAM-45 - Autenticación por roles

**Etapa:** PoC, ampliada en la Entrega 1.

### Actor/es
- Usuario (Admin, Chofer, Técnico)

### Funcionalidad
Como usuario, quiero iniciar sesión con mis credenciales para acceder a la aplicación con
los permisos correspondientes a mi rol.

### Valor aportado
Permite diferenciar la experiencia de cada rol desde el ingreso a la aplicación, y es la
base sobre la que se protege la información de cada uno.

### Criterios de aceptación
- Quiero poder iniciar sesión con usuario y contraseña y recibir un token de acceso.
- Quiero que la aplicación me lleve a la vista que corresponde a mi rol (chofer, admin
  o técnico).
- Quiero poder ver la contraseña que estoy escribiendo antes de enviarla.
- Si mi usuario fue desactivado, quiero ver un aviso claro al intentar ingresar.

---

## CAM-67 / CAM-68 - Rol Técnico y vista propia

**Etapa:** Entrega 1.

### Actor/es
- Técnico

### Funcionalidad
Como técnico, quiero que al iniciar sesión se me lleve a una vista propia de taller,
separada de la de admin y de la de chofer.

### Valor aportado
Incorpora al personal de taller como usuario del sistema, que es la base para asignarle
órdenes de trabajo y que registre el trabajo hecho.

### Criterios de aceptación
- Quiero poder iniciar sesión con un usuario de rol Técnico.
- Quiero ver una vista propia, pensada para el celular, y no el panel de admin ni el
  flujo del chofer.
- Los usuarios Admin y Chofer siguen viendo lo mismo que antes.

---

## CAM-23 - Gestión de usuarios y roles

**Etapa:** Entrega 1.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero dar de alta usuarios en la plataforma, asignarles un rol y poder
editarlos o desactivarlos, para que cada persona entre con su propio usuario y solo vea
lo que corresponde a su rol.

### Valor aportado
Elimina la carga manual de usuarios en la base de datos y deja la administración de
accesos en manos de quien gestiona la flota.

### Criterios de aceptación
- Quiero ver el listado de usuarios con su rol y su estado en una pestaña "Usuarios".
- Quiero crear un usuario con nombre, contraseña inicial y rol (Admin, Chofer o
  Técnico), y que ese usuario pueda ingresar a su vista.
- No quiero poder crear un usuario con un nombre que ya existe.
- Quiero cambiar el rol de un usuario, resetear su contraseña y desactivarlo o
  reactivarlo.
- Un usuario desactivado no puede ingresar, pero se conserva su historial.
- No quiero poder desactivarme a mí mismo ni quitarme el rol de admin.
- Un técnico con órdenes de trabajo abiertas no puede cambiar de rol hasta reasignarlas.
- Solo un admin con sesión válida puede consultar o modificar usuarios.

---

## CAM-11 - Formulario de inspección DVIR desde móvil

**Etapa:** PoC, ampliada en la Entrega 1.

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
- Quiero que el checklist del pre-trip sea el que corresponde al vehículo que elegí
  (ver CAM-31).

---

## CAM-12 - Reporte de defectos con fotografía

**Etapa:** PoC, ampliada en la Entrega 1.

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
- Quiero cargar un título corto del defecto y, si hace falta, una descripción más larga.
- Si la foto no es JPG ni PNG, quiero un mensaje claro que me lo diga.

---

## CAM-31 - Checklist de inspección configurable por vehículo

**Etapa:** Entrega 1.

### Actor/es
- Admin, Chofer

### Funcionalidad
Como admin, quiero poder agregar o quitar ítems del checklist de inspección para un
vehículo en particular, para que cada chofer revise lo que corresponde a ese vehículo
(por ejemplo, un camión con acoplado tiene controles que una camioneta no).

### Valor aportado
Adapta la inspección a cada unidad sin perder un checklist base común, y evita que el
chofer responda ítems que no aplican a su vehículo.

### Criterios de aceptación
- Quiero ver, en el detalle del vehículo, su checklist de inspección con todos los
  ítems.
- Quiero agregar un ítem propio a un vehículo, indicando qué revisar y en qué sección, y
  elegir si entra o no en el checklist del chofer.
- Quiero quitar un ítem del checklist del chofer y volver a incluirlo después.
- Quiero eliminar un ítem que agregué; los ítems del checklist base no se eliminan.
- No quiero poder quitar los ítems obligatorios (kilómetros actuales).
- Un ítem agregado a un vehículo aparece en la inspección de ese vehículo y no en las de
  otros.
- Si un ítem agregado se marca con falla, genera un defecto igual que un ítem base.
- Las inspecciones anteriores se siguen viendo igual aunque el ítem se quite o se
  elimine.

---

## CAM-32 - Carga de descripciones por audio

**Etapa:** Entrega 1.

### Actor/es
- Chofer, Técnico

### Funcionalidad
Como chofer y como técnico, quiero poder dictar una descripción en lugar de escribirla,
para describir el problema o el trabajo hecho rápido desde el celular.

### Valor aportado
Baja la fricción de cargar texto desde el celular en la calle o en el taller, y permite
descripciones más completas que las que alguien escribiría a mano.

### Criterios de aceptación
- Como chofer, quiero dictar la descripción del defecto al reportarlo en la inspección.
- Como técnico, quiero dictar la descripción de cierre al finalizar una orden de
  trabajo.
- Quiero ver lo que se va entendiendo mientras hablo y poder corregir el texto a mano.
- Quiero que el dictado no me impida enviar: si el navegador no lo soporta, no hay
  micrófono o no hay conexión, puedo escribir el texto.
- Quiero ver la descripción del defecto en la orden del técnico, en el listado de
  defectos y en el detalle del vehículo.

---

## CAM-13 - Gestión de defectos reportados

**Etapa:** PoC, ampliada en la Entrega 1.

### Actor/es
- Admin

### Funcionalidad
Como responsable de mantenimiento, quiero ver un listado de defectos ordenados por
gravedad y fecha para priorizar qué intervenciones atender primero.

### Valor aportado
Da visibilidad inmediata y centralizada de los problemas de la flota, sin depender de
que alguien reenvíe o busque el mensaje original.

### Criterios de aceptación
- Quiero ver todos los defectos reportados, ordenados primero por gravedad y luego por
  fecha de reporte (más reciente primero).
- Cada defecto en el listado muestra gravedad, fecha, título, descripción y patente del
  vehículo asociado.
- Si no hay defectos reportados, quiero ver un listado vacío, no un error.
- Quiero ver si un defecto ya tiene una orden de trabajo abierta y quién la tiene a
  cargo.
- Quiero distinguir los defectos ya resueltos de los que siguen abiertos.

---

## CAM-37 / CAM-81 - Defectos abiertos recientes en el panel de admin

**Etapa:** PoC, ampliada en la Entrega 1.

### Actor/es
- Admin

### Funcionalidad
Como administrador, quiero ver los defectos abiertos más recientes en el dashboard,
para priorizar qué revisar sin tener que entrar al listado completo de defectos.

### Valor aportado
Da a quien administra la flota una vista rápida de lo más urgente apenas entra al
dashboard, sin un paso extra de navegación.

### Criterios de aceptación
- Quiero ver un widget de "Defectos abiertos recientes" con gravedad, patente,
  descripción y quién y cuándo lo reportó.
- Quiero que se respete el mismo orden que el listado completo (bloqueante primero,
  luego más reciente).
- Quiero un link "Ver todos" que me lleve al listado completo (CAM-13), con forma de
  volver al dashboard.
- Quiero ver 4 defectos a la vez y poder desplazarme dentro del cuadro para ver el
  resto.
- Quiero que el cuadro esté por encima de "Próximos vencimientos".

---

## CAM-53 - Ver la foto sin salir de la página

**Etapa:** Entrega 1.

### Actor/es
- Admin, Técnico

### Funcionalidad
Como admin, quiero ver una foto dentro de la misma pantalla, para no perder lo que estoy
revisando.

### Valor aportado
Evita abrir pestañas nuevas y perder el contexto al revisar la evidencia de un defecto o
de un trabajo.

### Criterios de aceptación
- Quiero que la foto se abra a pantalla completa encima de la pantalla en la que estoy.
- Quiero cerrarla con Esc o con un clic afuera y volver a donde estaba.
- Quiero ese visor en el detalle de una orden de trabajo, al finalizarla y en la vista
  del técnico.

Pendiente: en el listado de Defectos, "Ver foto" todavía abre la foto en otra pestaña.

---

## CAM-14 - Órdenes de trabajo desde defectos

**Etapa:** Entrega 1.

### Actor/es
- Admin

### Funcionalidad
Como responsable de mantenimiento, quiero generar una orden de trabajo directamente
desde un defecto reportado, asignarla a un responsable y hacer seguimiento hasta su
cierre.

### Valor aportado
Cierra el ciclo del defecto dentro del sistema: deja registrado quién lo arregla, en qué
estado está el trabajo y qué se hizo, en lugar de coordinarlo por fuera.

### Criterios de aceptación
- Quiero generar una orden de trabajo desde un defecto abierto, desde una programación
  del calendario o de forma manual (eligiendo vehículo y título).
- Quiero asignarle un responsable y una descripción de lo que hay que hacer.
- Quiero seguir su estado: asignada, en proceso, finalizada o cancelada.
- Para finalizarla, quiero que se exija una descripción de cierre y al menos una foto
  del trabajo.
- Quiero que, al finalizarla, el defecto de origen quede resuelto y la programación
  asociada quede como realizada.
- Si la orden viene de un plan de mantenimiento, quiero que al finalizarla se registre
  el mantenimiento y se recalcule el próximo vencimiento.
- Quiero que cancelar una orden no resuelva el defecto, para poder atenderlo con otra.
- No quiero poder abrir una orden sobre un defecto ya resuelto.

---

## CAM-62 - Ejecución interna o externa

**Etapa:** Entrega 1.

### Actor/es
- Admin

### Funcionalidad
Como responsable de mantenimiento, quiero indicar si un trabajo se resuelve con personal
propio o si se lleva a un taller externo.

### Valor aportado
Refleja cómo trabaja una flota real, que resuelve parte del mantenimiento en su taller y
deriva el resto, y deja registrado a dónde fue cada vehículo.

### Criterios de aceptación
- Quiero elegir si la orden de trabajo es interna o externa al crearla o planificarla.
- Si es interna, quiero asignarla a un técnico del sistema.
- Si es externa, quiero que se me pida el proveedor y poder cargar un contacto.
- Quiero poder cambiar el tipo de ejecución mientras la orden esté abierta.

---

## CAM-63 - Sección de órdenes de trabajo y gastos

**Etapa:** Entrega 1.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero una sección donde controlar las órdenes de trabajo y cargar la
información de gastos de cada una.

### Valor aportado
Centraliza el seguimiento del trabajo de taller y empieza a registrar cuánto cuesta
mantener cada vehículo.

### Criterios de aceptación
- Quiero una sección "Órdenes de trabajo" en el menú del admin, con el listado de
  órdenes: vehículo, título, estado, tipo de ejecución, responsable y total de gastos.
- Quiero filtrar el listado por estado, por vehículo y por técnico.
- Quiero abrir el detalle de una orden con sus datos, fotos y gastos.
- Quiero cargar gastos por categoría (repuesto, mano de obra u otro), con descripción y
  monto, y ver el total de la orden.
- No quiero poder cargar ni borrar gastos en una orden ya cerrada.

---

## CAM-60 - Vista de taller con órdenes de trabajo

**Etapa:** Entrega 1.

### Actor/es
- Técnico, Admin

### Funcionalidad
Como técnico, quiero ver las órdenes de trabajo que tengo asignadas y poder trabajarlas
desde el celular, para saber qué hacer y registrar el trabajo hecho. Como admin, quiero
asignar cada orden a un técnico del sistema.

### Valor aportado
Lleva el trabajo hasta quien lo ejecuta: el técnico sabe qué tiene que hacer sin que se
lo avisen por otro canal, y el resultado queda cargado por quien hizo el trabajo.

### Criterios de aceptación
- Como admin, quiero asignar, cambiar o quitar el técnico de una orden interna.
- Como técnico, quiero ver "Mis órdenes": mis órdenes abiertas, con las de defecto
  bloqueante primero y marcadas.
- Quiero ver, en cada orden, el defecto de origen con su gravedad, quién lo reportó y la
  foto del chofer.
- Quiero empezar el trabajo y finalizarlo con descripción, fotos y kilometraje (si la
  orden viene de un plan).
- Quiero que se me avise qué falta para poder finalizar.
- Quiero que replanificar algo que ya tiene una orden abierta no cree otra: se mueve la
  fecha y se avisa que la orden se mantuvo.

---

## CAM-25 - Carga y gestión de flota

**Etapa:** PoC.

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

---

## CAM-18 - Carga y actualización de kilometraje

**Etapa:** Entrega 1.

### Actor/es
- Admin

### Funcionalidad
Como usuario, quiero actualizar el kilometraje de los vehículos de forma manual para que
el sistema recalcule los próximos mantenimientos.

### Valor aportado
Mantiene al día el dato del que dependen los vencimientos por kilómetros, sin necesidad
de integrarse todavía con un sistema de GPS.

### Criterios de aceptación
- Quiero actualizar el kilometraje de un vehículo ya existente en cualquier momento, no
  solo al darlo de alta.
- No quiero poder cargar un kilometraje menor al que ya tiene el vehículo.
- Quiero que los vencimientos por kilómetros reflejen el nuevo valor.

---

## CAM-15 / CAM-22 - Detalle e historial de vehículo

**Etapa:** Entrega 1.

### Actor/es
- Admin

### Funcionalidad
Como supervisor, quiero ver el historial completo de un vehículo (inspecciones, defectos
reportados, mantenimientos realizados y órdenes de trabajo) para tener trazabilidad y
hacer mantenimiento informado.

### Valor aportado
Reúne en un solo lugar todo lo que le pasó a una unidad, que antes había que reconstruir
entre varias pantallas, y sirve de respaldo ante una auditoría.

### Criterios de aceptación
- Quiero abrir el detalle de un vehículo desde el listado de Vehículos.
- Quiero ver su ficha completa (patente, marca y modelo, año, tipo, kilometraje, número
  de chasis y estado), también si está dado de baja.
- Quiero ver su historial de inspecciones, defectos, mantenimientos realizados y órdenes
  de trabajo, cada lista de más reciente a más antigua.
- Quiero ver cuántos defectos bloqueantes tiene abiertos y cuántas órdenes en curso.
- Quiero un mensaje claro si no hay registros o si el vehículo no existe.
- Quiero que los vehículos del listado estén ordenados alfabéticamente por patente.

---

## CAM-16 - Interfaz para definir planes de mantenimiento preventivo a un vehículo

**Etapa:** PoC.

### Actor/es
- Admin

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

## CAM-17 - Cálculo automático de vencimientos

**Etapa:** Entrega 1.

### Actor/es
- Sistema

### Funcionalidad
Como sistema, quiero recalcular automáticamente los próximos mantenimientos preventivos
cuando se actualiza el kilometraje o se registra un mantenimiento, para mantener los
vencimientos siempre correctos.

### Valor aportado
Evita que alguien tenga que llevar la cuenta a mano de qué vence y cuándo, que es el
origen de los mantenimientos salteados.

### Criterios de aceptación
- Quiero que el estado de cada plan asignado (al día / por vencer / vencido) se calcule
  por kilómetros y por fecha.
- Quiero que el vencimiento se recalcule al cargar kilometraje y al registrar un
  mantenimiento hecho.
- Quiero ver el resultado en el dashboard, en "Estado de la flota" y en "Próximos
  vencimientos".

---

## CAM-42 / CAM-50 / CAM-51 - Programación de mantenimientos

**Etapa:** PoC, ampliada en la Entrega 1.

### Actor/es
- Admin

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
- Quiero que, al planificar, se genere la orden de trabajo con su responsable en el
  mismo paso.
- Quiero abrir el detalle de una programación para ver su origen, sus notas y su orden
  de trabajo, y poder editarla o eliminarla.
- Quiero ver hasta 2 mantenimientos por día y desplazarme dentro del día para ver el
  resto.

---

## CAM-77 - Cancelar una programación cancela su orden de trabajo

**Etapa:** Entrega 1.

### Actor/es
- Admin

### Funcionalidad
Como admin, cuando cancelo un mantenimiento programado quiero que su orden de trabajo
abierta también se cancele, para que el técnico no siga viendo un trabajo que ya no hay
que hacer.

### Valor aportado
Mantiene sincronizados el calendario y la lista de trabajo del técnico, y evita que
alguien trabaje sobre algo que se canceló.

### Criterios de aceptación
- Al cancelar una programación con una orden de trabajo asignada, quiero que la orden
  se cancele.
- Si la orden ya está en proceso, quiero un aviso que me diga quién la tiene y me pida
  confirmar antes de cancelar las dos.
- Las órdenes finalizadas no se modifican.
- Cancelar por esta vía no resuelve el defecto ni cierra el plan de origen.

---

## CAM-20 / CAM-38 / CAM-40 / CAM-80 - Dashboard de estado de flota

**Etapa:** PoC, ampliada en la Entrega 1.

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
- Quiero ver un resumen de la flota en tarjetas: vehículos al día, por vencer, vencidos
  y defectos abiertos.
- Quiero ver 5 vehículos a la vez en "Estado de la flota" y poder desplazarme dentro del
  cuadro para ver todos.

---

## CAM-21 - Vista de próximos mantenimientos

**Etapa:** Entrega 1.

### Actor/es
- Admin

### Funcionalidad
Como usuario, quiero ver un listado de próximos mantenimientos (próximos 7, 14 y 30
días) ordenado por prioridad para planificar la carga de trabajo de mantenimiento.

### Valor aportado
Permite anticipar la carga del taller de las próximas semanas en lugar de reaccionar
cuando un mantenimiento ya venció.

### Criterios de aceptación
- Quiero ver, en el dashboard, los próximos vencimientos agrupados por franja: próximos
  7 días, 8 a 14 días y 15 a 30 días.
- Quiero que, dentro de cada franja, aparezca primero lo que vence antes.
- Quiero que los mantenimientos ya vencidos aparezcan en la franja más urgente.

---

## CAM-39 / CAM-49 / CAM-54 - Ajustes de UI: vista de chofer y shell de administración

**Etapa:** PoC, ampliada en la Entrega 1.

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
  Mantenimiento, Órdenes de trabajo, Usuarios) desde un menú hamburguesa, con el
  dashboard a ancho completo.
- Como admin, quiero que la sección del menú se resalte al pasar el cursor por encima.

---

## CAM-79 - Límite de caracteres en los títulos

**Etapa:** Entrega 1.

### Actor/es
- Admin, Chofer

### Funcionalidad
Como usuario, quiero que los títulos de las programaciones, los defectos, los planes de
mantenimiento y las órdenes de trabajo tengan un largo máximo, para que se vean bien en
el calendario y en las tablas.

### Valor aportado
Evita que un título largo rompa el layout del calendario y de los listados, y obliga a
títulos breves que se leen de un vistazo.

### Criterios de aceptación
- Quiero que los títulos acepten hasta 30 caracteres, con un contador que me muestre
  cuántos llevo.
- Quiero que el calendario semanal y el mensual no se desborden con títulos largos.
- Quiero que los títulos anteriores al límite se vean recortados, con el texto completo
  al pasar el cursor.

---

## CAM-35 - Conectar front y back

**Etapa:** PoC.

### Objetivo
Integrar el frontend (React/Vite) con la API real del backend, dejando de depender de
datos mock para cualquier pantalla de la aplicación.

### Valor aportado
Es la base técnica sin la cual ninguna de las demás historias se podía mostrar de punta
a punta en la presentación.

---

## T - Data initializer y seed de datos demo

**Etapa:** PoC, ampliada en la Entrega 1.

### Objetivo
Crear un mecanismo para inicializar datos del sistema (flota, usuarios, planes de
mantenimiento) y facilitar el desarrollo, el testing y la demo de la aplicación. En la
Entrega 1 se sumó un seed de datos demo que simula seis meses de operación.

### Valor aportado
Reduce tiempos de setup, mejora la consistencia de las pruebas durante el desarrollo, y
permite mostrar la aplicación con una historia creíble: vehículos al día, por vencer y
vencidos, defectos abiertos y órdenes de trabajo en distintos estados.

### Resultado esperado
- Disponibilidad de datos iniciales de flota, usuarios de los tres roles y planes de
  mantenimiento.
- Seed demo de 10 camiones con seis meses de inspecciones, defectos, mantenimientos,
  órdenes de trabajo, gastos y fotos.
- Fechas relativas al día en que se corre, para que los estados se mantengan aunque se
  cargue meses después.
- Script generado por un programa con semilla fija, que se puede volver a correr las
  veces que haga falta.

---

## CAM-30 - Integración continua del backend

**Etapa:** Entrega 1.

### Objetivo
Configurar integración continua en el repositorio del backend, para que cada push y
cada pull request compile y corra los tests automáticamente.

### Valor aportado
Un cambio que rompe los tests se ve antes de mergearlo, en lugar de descubrirse días
después en `develop`.

### Resultado esperado
- Workflow de GitHub Actions que corre en push y en pull request hacia `develop` y
  `main`.
- El build compila, corre los tests y verifica la cobertura mínima; si algo falla, el
  check queda en rojo.
- El build no depende de una base de datos ni de configuración local.
- Reportes de tests y de cobertura disponibles en cada corrida, y badge de estado en el
  README.

---

## CAM-78 - Tests del backend

**Etapa:** Entrega 1.

### Objetivo
Que el backend tenga tests con sentido, para detectar regresiones antes de mergear y
poder mostrar la calidad del código.

### Valor aportado
Da respaldo para seguir cambiando el código sin romper reglas de negocio ya acordadas, y
deja documentado en los tests qué se espera de cada regla.

### Resultado esperado
- Tests de services (reglas de negocio y validaciones), de controllers (rutas, códigos
  HTTP y formato de las respuestas), de modelo y de almacenamiento de fotos.
- 263 tests y 92,5% de cobertura de líneas al cerrar la historia (antes, 48,9%), con un
  mínimo de 85% exigido por el build.
- Cada test protege una regla concreta y su nombre dice cuál.
- Bugs detectados por los tests y corregidos, entre ellos un path traversal en la
  lectura de fotos y errores del cliente que respondían 500.

---

## CAM-82 - Ruteo por URL en el panel de admin

**Etapa:** Entrega 1.

### Objetivo
Que la URL refleje la sección o el vehículo que el admin está viendo.

### Valor aportado
Recargar la página no devuelve al Resumen, se puede compartir el link de un vehículo y
funcionan los botones atrás y adelante del navegador.

### Resultado esperado
- Cada sección del admin tiene su propia dirección: Resumen, Vehículos, Planes de
  Mantenimiento, Órdenes de trabajo, Usuarios y Defectos.
- El detalle de un vehículo tiene una dirección propia que incluye su identificador.
- Una dirección desconocida lleva al Resumen.

---

## CAM-58 - Evaluar rediseño de UI

**Etapa:** Entrega 1.

### Objetivo
Evaluar si hace falta un rediseño de la interfaz, y en qué pantallas, para que toda la
aplicación se vea consistente. La aplicación creció por partes (admin, chofer, técnico,
calendario, órdenes de trabajo) y no todas las pantallas siguen igual el sistema de
diseño.

### Valor aportado
Ordena las mejoras visuales en una propuesta priorizada, en lugar de corregir pantallas
sueltas sin un criterio común.

### Resultado esperado
- Recorrido de todas las pantallas, en escritorio y en celular, con las inconsistencias
  encontradas.
- Propuesta de qué unificar primero y decisión del equipo sobre qué se hace.

---

## Bugs corregidos

**Etapa:** Entrega 1.

- **CAM-57 - Error al programar un mantenimiento desde el calendario.** Programar desde
  un día del calendario fallaba; se corrigió el flujo.
- **CAM-61 - El calendario no se actualizaba al programar.** Al planificar desde
  "Estado de la flota", el turno nuevo no aparecía en el calendario semanal hasta
  cambiar de semana o recargar. Ahora el calendario se refresca solo.
- **CAM-74 - Validaciones al finalizar una orden de trabajo.** Se podía finalizar una
  orden con un kilometraje negativo, con decimales o menor al del vehículo, y con una
  foto todavía subiéndose o borrándose. Ahora el kilometraje se valida en el frontend y
  en el backend, y no se puede finalizar con una operación de fotos en curso.
- **CAM-75 - Edición de órdenes de trabajo cerradas.** Se podía editar una orden ya
  finalizada o cancelada. Ahora el backend lo rechaza y el detalle de una orden cerrada
  se muestra en solo lectura.
