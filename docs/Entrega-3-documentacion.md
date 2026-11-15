# Entrega 3 - Fleet Maintenance

## Resumen Ejecutivo

### Qué se agregó/modificó en esta iteración

En esta entrega el foco estuvo en cuatro frentes: que un vehículo no salga si no está en regla (documentación, turnos de taller), que el chofer pueda trabajar aunque no tenga señal o le pase algo en la ruta, que el admin tenga indicadores para decidir (disponibilidad, cumplimiento, comparación entre vehículos) y sumar los dos costos grandes que faltaban: combustible y neumáticos.

Este documento lista todas las historias terminadas.

En esta etapa se implementaron y validaron:

- **Documentación del vehículo**: VTV, seguro, cédulas, habilitación y otros documentos con su vencimiento y comprobante (foto o PDF), en una pestaña propia del detalle. Un documento vencido deja el vehículo "No disponible", y los avisos llegan antes de que venza.
- **Detalle del vehículo en pestañas**: Resumen, Historial, Checklist, Documentación, Combustible y Neumáticos, cada una con su dirección propia.
- **Código QR por vehículo**: lleva directo al pre-viaje o al post-viaje desde el celular del chofer.
- **Avisos nuevos**: al chofer (su vehículo no está disponible, vuelve a estar disponible, se resolvió el defecto que reportó, tiene turno de taller) y al admin (defectos recurrentes, defectos sin atender, salida con turno de taller, km anormal en un viaje, consumo de combustible anormal, problema en una cubierta, comentarios en una OT).
- **Chofer en la ruta**: reportar un problema durante el viaje sin esperar al post-viaje, y hacer la inspección sin conexión, que se envía sola cuando vuelve la señal.
- **Indicadores para el admin**: tiempo fuera de servicio y reparación promedio, defectos recurrentes, cumplimiento del mantenimiento, fecha estimada de los planes por km y una vista para comparar vehículos y decidir si conviene reparar o reemplazar.
- **Defectos con más contexto**: marca "Ocurrió en el viaje" cuando un control dio OK en el pre-viaje y falló en el post-viaje, y marca "Sin atender hace N días".
- **Órdenes de trabajo**: pausa con motivo ("En espera") y comentarios entre el admin y el técnico.
- **Gastos operativos** fuera de una OT (seguro, patente, peajes, etc.) y **exportar el historial** de un vehículo a PDF.
- **Carga de flota**: importar vehículos desde un Excel o CSV, y plantillas de checklist para aplicar a varios vehículos.
- **Combustible**: cargas desde el celular del chofer o desde el admin, consumo por vehículo y aviso de consumo anormal.
- **Neumáticos y remolques**: tipos de vehículo, remolques con su enganche, cubiertas por posición con sus km, rotaciones, el vehículo dibujado visto de arriba y el estado de cada cubierta reportado en el pre-viaje.
- **Auditoría de UI/UX** de toda la aplicación, con 29 hallazgos priorizados como base para la próxima entrega.
- **Calidad**: el backend pasó de 473 a más de 850 tests, y el frontend arrancó su suite con Vitest y Testing Library (más de 120 tests).

### Decisiones tomadas

#### Un solo lugar decide si un vehículo puede salir
Hasta ahora un vehículo quedaba "No disponible" solo por un defecto bloqueante abierto. Ahora también lo deja así un documento vencido, y la regla vive en un único servicio del backend que usan el pre-viaje, la tabla de flota, la lista del chofer y el detalle. La respuesta incluye los motivos, así cada pantalla dice por qué no sale. La VTV y el seguro son obligatorios: si falta uno se marca, pero no bloquea. Un documento vence desde el día siguiente a su fecha.

#### Documentos con nombre libre, y vinculados a los planes
El admin escribe el nombre del documento como lo conoce ("Póliza La Segunda") y el sistema reconoce el tipo por palabras clave, para saber cuáles son obligatorios. Renovar un documento crea una versión nueva y la anterior queda en el historial. Un plan por tiempo puede renovar un tipo de documento: si el vehículo lo tiene cargado, el plan vence cuando vence el documento, y cargar el documento nuevo registra el mantenimiento del plan. Avisa el documento, no el plan, para no duplicar avisos.

#### Los avisos al chofer van a quien tiene el vehículo
No existe un chofer asignado a cada vehículo, así que los avisos de disponibilidad y de turno de taller le llegan al chofer que lo tiene en viaje y a quienes reportaron el defecto. Un bloqueo por defecto no se le avisa a quien lo reportó, porque ya lo sabe. Cuando se resuelve el único bloqueante, el chofer recibe "Vehículo disponible" y no además "Se resolvió tu defecto".

#### El turno de taller avisa, pero no bloquea
Un vehículo con turno hoy se marca en la lista del chofer, y al elegirlo para el pre-viaje aparece una advertencia que deja seguir. El turno puede ser a última hora y la decisión es de la persona. Si sale igual, el admin recibe un aviso.

#### El QR se genera en el navegador
Cada vehículo tiene una dirección propia (`/inspeccion/{id}`) y el QR se arma en el momento a partir del id, con una librería del frontend. Así los vehículos que ya existían lo tienen sin hacer nada. Sin sesión, pide el login y sigue al vehículo; un admin o un técnico que lo escanea va a su propia vista.

#### Gastos operativos en una tabla aparte
Los gastos que no salen de una reparación se guardan en una entidad nueva, y `GET /api/expenses` une las dos fuentes. Generalizar la tabla de gastos de OT obligaba a migrar datos a mano (no hay herramienta de migraciones) y a tocar las OTs y la actividad de los usuarios. El costo del vehículo pasa a ser el costo total de tenerlo, no solo de mantenerlo.

#### El PDF del historial se arma en el navegador
Se usa una vista de impresión con estilos propios y "Imprimir → Guardar como PDF". No suma dependencias al backend y toma exactamente los datos que ya muestra la app, con el mismo selector de período que Gastos.

#### Un reporte en ruta es una inspección más
Todo lo que necesita saber de qué vehículo, chofer o viaje es un defecto llega por la inspección que lo originó. Para no tocar disponibilidad, recurrentes, tiempo fuera de servicio, OTs y listados, el reporte en viaje se guarda como una inspección de un tipo nuevo, dentro del viaje abierto, sin km y sin cerrar el viaje. No cuenta como inspección en la actividad del chofer. Solo puede reportar el chofer que abrió el viaje.

#### Inspección sin conexión: cola en el celular y envío sin duplicados
Si no hay señal, la inspección y sus fotos se guardan en el celular y se envían solas al volver la conexión, en el orden en que se hicieron. Cada envío lleva la hora real de la inspección y un identificador propio: si un reintento llega dos veces, el servidor devuelve la que ya guardó. Un pre-viaje pendiente deja el vehículo "en viaje" en la lista, para poder hacer el post-viaje sin señal. Si el servidor rechaza un pre-viaje, lo que sigue de ese vehículo no se manda, para no cerrar el viaje de otro. Abrir la app por primera vez sin conexión quedó fuera de alcance.

#### Lo que se puede calcular, se calcula
"Ocurrió en el viaje", el consumo de combustible, los km de cada cubierta, el cumplimiento del mantenimiento y los defectos sin atender se calculan con lo que ya está guardado, sin columnas nuevas. Así funcionan también sobre los datos viejos y no hay dos versiones del mismo dato. Cada listado resuelve estos cálculos de una sola vez, sin una consulta por fila.

#### Cada indicador sale del mismo lugar en todas las pantallas
La comparación de vehículos usa los mismos cálculos que Gastos, el tiempo fuera de servicio y los defectos recurrentes, así los números coinciden con el detalle de cada vehículo. La marca "Para revisar" aparece cuando un vehículo está peor que el promedio en al menos dos de tres indicadores (costo por km, disponibilidad y fallas recurrentes) y dice por qué. El sistema señala qué mirar; la decisión es del admin. Sin km suficientes, el costo por km se muestra "Sin datos" y no un número engañoso.

#### La fecha estimada de un plan por km es solo informativa
Con las lecturas de km de los últimos 60 días se calcula cuánto anda cada vehículo por día, y con eso una fecha aproximada ("Vence aprox. el 12/11"). El estado del plan, el health score y los avisos siguen saliendo de los km reales. Las lecturas mal cargadas se descartan.

#### "En espera" es una OT abierta
Una OT pausada sigue contando como abierta en todos lados: impide abrir otra para el mismo origen, mantiene el vehículo "No disponible" si el defecto es bloqueante y sigue apareciendo en el calendario. No se puede finalizar sin retomarla, pero sí cancelar. Cada pausa queda registrada con su motivo, inicio y fin. Pueden pausar el técnico y el admin, porque una OT externa no tiene técnico.

#### Los comentarios de una OT no se editan ni se borran
Son el registro de lo que se habló. Una OT cerrada los sigue mostrando pero no acepta nuevos. Si comenta el técnico, avisa a los admins; si comenta el admin, al técnico asignado. Nadie recibe aviso por su propio comentario.

#### Las plantillas de checklist se copian, no se vinculan
Aplicar una plantilla copia sus controles al vehículo como controles propios. Si quedara vinculado, editar la plantilla cambiaría de golpe el checklist de muchos vehículos, y los defectos recurrentes se identifican por el id de cada control. Aplicarla dos veces no duplica: se saltea el control con la misma etiqueta y sección, sin importar mayúsculas ni acentos.

#### Importar la flota: el navegador lee, el servidor valida
El archivo (CSV con coma o punto y coma, o Excel) se lee en el navegador, y el servidor valida cada fila con las mismas reglas del alta manual, más patentes repetidas. Primero se pide una vista previa con los errores por fila, y al confirmar se crean solo las filas válidas.

#### El tipo de vehículo define sus cubiertas
Un catálogo cerrado de tipos (utilitario, camión rígido, tractor, semirremolque, acoplado) define las posiciones de las cubiertas y si el vehículo arrastra o es arrastrado. Los remolques son vehículos, pero solo los ve el admin: no se inspeccionan ni cargan combustible. En el pre-viaje, el chofer elige qué remolque lleva, y los km que suma el tractor se le suman al remolque, así sus cubiertas y su mantenimiento por km andan solos.

#### Estado visual de la cubierta, sin medir el dibujo
La primera versión pedía la profundidad del dibujo en milímetros, pero el chofer no tiene medidor. Se reemplazó por lo que se ve: se ve la tela o el alambre, bulto, corte en el flanco, pinchada, baja presión, desgaste irregular o poco dibujo. El dibujo del vehículo aparece recién cuando el chofer marca "Neumáticos" con defecto, y lo que marca se suma al defecto, que sigue el circuito de siempre (programación → OT).

#### Consumo de combustible por tanque lleno
Un tramo va de un tanque lleno al siguiente y suma las cargas parciales del medio. Un tramo que consume más de 1,3 veces el promedio de los últimos 10 genera un aviso. Las cargas aparecen en Gastos con la categoría combustible y suman en el costo del vehículo.

#### Avisos que no se repiten
Los avisos que nacen de una regla (defectos recurrentes, defectos sin atender, turnos de taller, documentos por vencer) guardan qué se avisó, para no insistir todos los días. Un defecto sin atender se avisa una sola vez, y los avisos se agrupan por vehículo.

### Desafíos técnicos encontrados

- **Enums que la base no acepta**: Hibernate genera un `CHECK` para cada enum, y con `ddl-auto: update` no lo actualiza en una base que ya existe. Cada estado o tipo de aviso nuevo (OT en espera, inspección en ruta, km anormal, combustible, cubiertas) daba error 500 hasta borrarlo. El SQL quedó documentado en cada contrato de `docs/api/`, y el seed demo lo corre solo.
- **Kilometrajes mal cargados en los datos reales**: hay vehículos de 309.400 km con inspecciones cargadas con 310. La fecha estimada, la comparación y el aviso de km anormal descartan las lecturas menores a una anterior, las mayores al km actual y los saltos de más de 5.000 km, con la misma regla en los tres.
- **El mismo control con dos ids**: Luces y Fugas tenían un id en el pre-viaje y otro en el post-viaje, así que los defectos recurrentes no los sumaban (CAM-144). Se unificaron con una clave común, que también usa "Ocurrió en el viaje".
- **Sin conexión, sin perder ni duplicar**: las fotos se guardan como archivos en el almacenamiento del navegador hasta poder subirlas, el orden de envío respeta la hora real de cada inspección y el servidor acepta el mismo envío dos veces sin duplicarlo. Con la sesión vencida no se pierde nada.
- **Hora argentina vs. hora del servidor**: "turno de hoy", "vencido desde mañana" y los períodos de los indicadores se resuelven en hora argentina. El servidor todavía corre en UTC, así que entre las 21:00 y las 24:00 algunas fechas estimadas salen un día después; queda para el deploy (CAM-131).
- **El vehículo visto de arriba**: un dibujo en SVG propio que se arma según el tipo (cabina, perno rey o lanza, ejes simples y duales), se usa grande en la pestaña Neumáticos para rotar tocando el destino, y más chico en el pre-viaje del celular.
- **Excel en español al importar**: separador `;`, BOM, encabezados con o sin acentos y en cualquier orden. La librería para leer `.xlsx` se carga solo al abrir la importación, para no sumar peso al resto de la app.
- **Trabajo en paralelo entre dos personas**: cada sprint se integró primero en una rama de release y después en `develop`, en seis PRs por repo. La integración de combustible, neumáticos y plantillas con el resto del sprint 10 tuvo conflictos, que se resolvieron en la rama de release antes de llegar a `develop`.

# User Stories

## Historias incluidas

| Clave | Historia | Sprint |
|---|---|---|
| CAM-127 | Documentación del vehículo con vencimientos | 9 |
| CAM-102 / CAM-103 | Avisos al chofer: su vehículo no está disponible / vuelve a estarlo | 9 |
| CAM-133 | Código QR en cada vehículo | 9 |
| CAM-109 | Gastos operativos fuera de una orden de trabajo | 9 |
| CAM-130 | Exportar el historial de un vehículo a PDF | 9 |
| CAM-134 | Tiempo fuera de servicio y reparación promedio | 9 |
| CAM-135 | Defectos recurrentes en un mismo vehículo | 9 |
| CAM-137 | Fecha estimada de vencimiento de los planes por km | 9 |
| CAM-139 | Reportar un defecto durante el viaje | 9 |
| CAM-140 | Comparar vehículos: reparar o reemplazar | 9 |
| CAM-146 | Daños ocurridos durante el viaje | 9 |
| CAM-148 | Pausar una orden de trabajo con un motivo | 9 |
| CAM-152 | Aviso de kilometraje fuera de lo normal en un viaje | 9 |
| CAM-153 | Inspección sin conexión | 9 |
| CAM-154 | Importar la flota desde un Excel o CSV | 9 |
| CAM-155 | Indicadores de cumplimiento del mantenimiento | 9 |
| CAM-136 | Tests del frontend | 9 |
| CAM-141 | Aviso al chofer: se resolvió el defecto que reportó | 10 |
| CAM-142 | Defectos abiertos sin atender | 10 |
| CAM-143 | Turno de taller: aviso al chofer y advertencia antes de salir | 10 |
| CAM-149 | Comentarios en la orden de trabajo | 10 |
| CAM-150 | Plantillas de checklist | 10 |
| CAM-156 | Combustible: cargas, consumo y aviso de consumo anormal | 10 |
| CAM-157 | Neumáticos, tipos de vehículo y remolques | 10 |
| CAM-159 | Auditoría de UI/UX | 10 |
| CAM-144 | Bug corregido | 9 |

## CAM-127 - Documentación del vehículo con vencimientos

Sprint: 9.

### Actor/es
- Admin
- Chofer

### Funcionalidad
Como admin, quiero cargar los documentos de cada vehículo con su vencimiento y enterarme antes de que venzan, para que ningún vehículo salga con la VTV o el seguro vencidos.

### Valor aportado
La documentación se controlaba por fuera, y un vencimiento se notaba recién cuando lo detectaba un control en ruta. Ahora el sistema la conoce y la usa para decidir si el vehículo puede salir.

### Criterios de aceptación
- Quiero una pestaña "Documentación" en el detalle del vehículo, con una tarjeta por documento: estado (al día, por vencer, vencido), fecha de vencimiento y comprobante.
- Quiero cargar el documento con el nombre que yo uso, y que el sistema me diga a qué tipo corresponde ("Cuenta como seguro").
- El comprobante puede ser una foto o un PDF, también arrastrando el archivo.
- Quiero renovar un documento y que la versión anterior quede en su historial.
- Un documento sin vencimiento no bloquea nada.
- Al día siguiente del vencimiento, el vehículo queda "No disponible" con el motivo, y el chofer no puede iniciar el pre-viaje.
- Si falta la VTV o el seguro, quiero verlo marcado, sin que el vehículo quede bloqueado.
- Recibo un aviso cuando un documento está por vencer (15 días antes, configurable) y otro cuando vence.
- Un plan de mantenimiento puede renovar un tipo de documento: al cargar el documento nuevo, se registra el mantenimiento del plan.

## CAM-102 / CAM-103 - Avisos al chofer: su vehículo no está disponible / vuelve a estarlo

Sprint: 9.

### Actor/es
- Chofer

### Funcionalidad
Como chofer, quiero enterarme cuando el vehículo que uso queda "No disponible" y cuando vuelve a estar disponible, para no presentarme a salir con un vehículo que no puede circular ni tener que preguntarle al taller.

### Valor aportado
El chofer se enteraba del bloqueo recién al intentar el pre-viaje, y de la reparación, por nadie.

### Criterios de aceptación
- Si un documento vencido bloquea el vehículo que tengo en viaje, recibo un aviso con la patente y el motivo.
- Si el bloqueo es por un defecto, no recibo aviso: lo reporté yo.
- Cuando se resuelve el último motivo de bloqueo, reciben el aviso quienes reportaron los defectos resueltos y a quienes se avisó del bloqueo.
- Si al vehículo todavía le queda otro motivo de bloqueo, no se avisa.
- Al tocar el aviso, voy a la inspección de ese vehículo.

## CAM-133 - Código QR en cada vehículo

Sprint: 9.

### Actor/es
- Chofer
- Admin

### Funcionalidad
Como chofer, quiero escanear un código QR pegado en el vehículo y llegar directo a su inspección, para no buscarlo en la lista de la flota cada vez.

### Valor aportado
Con una flota grande, buscar la patente en la lista es lento y se puede elegir el vehículo equivocado.

### Criterios de aceptación
- Quiero ver el QR en el Resumen del vehículo y descargarlo como imagen con la patente y el modelo, para imprimirlo.
- Escaneo el QR de un vehículo disponible y se abre su pre-viaje; si tengo su viaje abierto, el post-viaje.
- Si no tengo sesión, entro y sigo a ese vehículo.
- Un admin o un técnico que escanea el QR va a su propia vista.
- Si el vehículo no puede salir, vuelvo a la flota con el motivo.
- Los vehículos que ya existían tienen su QR sin hacer nada.

## CAM-109 - Gastos operativos fuera de una orden de trabajo

Sprint: 9.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero registrar gastos del vehículo que no vienen de una reparación (seguro, patente, VTV, peajes), para conocer el costo total de tener cada vehículo y no solo el de mantenerlo.

### Valor aportado
Hasta ahora un gasto pertenecía siempre a una orden de trabajo, así que solo se conocía el costo de mantenimiento.

### Criterios de aceptación
- Quiero cargar un gasto con vehículo, fecha, categoría, descripción y monto, desde Gastos y desde el detalle del vehículo.
- Puedo cargar un gasto con fecha anterior a hoy, pero no futura.
- Los gastos operativos aparecen en Gastos junto a los de OT, se distinguen y no llevan a una OT.
- Los gastos de OT siguen igual que antes y se editan solo desde su OT.
- Los costos del vehículo y su costo por km pasan a incluir los gastos operativos.

## CAM-130 - Exportar el historial de un vehículo a PDF

Sprint: 9.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero descargar en un PDF todo lo que le pasó a un vehículo en un período, para presentarlo en una auditoría o entregarlo al vender el vehículo.

### Valor aportado
El historial y los costos del vehículo solo se veían dentro de la aplicación.

### Criterios de aceptación
- Quiero un botón "Exportar historial" en el detalle del vehículo, con el mismo selector de período que Gastos.
- El PDF tiene un encabezado con patente, marca, modelo, km actual y período, y las secciones de inspecciones, defectos, órdenes de trabajo, mantenimientos hechos y resumen de costos.
- Las fechas están en hora argentina y los montos con el formato de la app.
- Un período sin actividad genera el PDF con cada sección vacía y un texto que lo dice.

## CAM-134 - Tiempo fuera de servicio y reparación promedio

Sprint: 9.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero saber cuánto tiempo estuvo cada vehículo sin poder usarse y cuánto tardamos en promedio en repararlos, para detectar los vehículos que más paran y si el taller se está demorando.

### Valor aportado
Se sabía cuánto cuesta cada vehículo, pero no cuánto tiempo estuvo parado.

### Criterios de aceptación
- Un vehículo está fuera de servicio mientras tiene al menos un defecto bloqueante abierto; dos bloqueantes a la vez no cuentan doble.
- Quiero ver en el Resumen la disponibilidad de la flota en el período, la reparación promedio y el vehículo que más tiempo estuvo parado.
- Quiero ver en el detalle del vehículo cuántas horas estuvo fuera de servicio y cuántas veces paró.
- La reparación promedio se puede ver por tipo de ejecución (taller propio o externo).
- El período se corta en el momento actual: "Este mes" el día 10 mide del 1 al 10.
- Un período sin paradas muestra 100% disponible; sin OTs finalizadas, la reparación promedio muestra "Sin datos".

## CAM-135 - Defectos recurrentes en un mismo vehículo

Sprint: 9.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero enterarme cuando el mismo control falla varias veces en el mismo vehículo, para detectar reparaciones que no resuelven el problema de fondo.

### Valor aportado
Cada defecto se veía suelto: si los frenos de un vehículo fallaban tres veces en un mes y se reparaban cada vez, nada lo marcaba.

### Criterios de aceptación
- Un control es recurrente cuando falla 3 veces en 30 días en el mismo vehículo (configurable). Cuentan los bloqueantes y los no bloqueantes, abiertos y resueltos.
- Al llegar al umbral, recibo un aviso: "Frenos de AB123CD: 3 defectos en 30 días".
- Un cuarto defecto dentro de la misma ventana no repite el aviso.
- Tres fallas del mismo control en vehículos distintos, o en 45 días, no son recurrentes.
- Quiero ver los controles recurrentes en el detalle del vehículo y en el Resumen, aunque el aviso esté apagado.

## CAM-137 - Fecha estimada de vencimiento de los planes por km

Sprint: 9.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero ver una fecha estimada de vencimiento en los planes por kilómetros, para programar el taller con tiempo.

### Valor aportado
Los planes por km solo avisaban cuando faltaban 1.000 km, y según lo que ande el vehículo eso puede ser un día o un mes.

### Criterios de aceptación
- Cada plan por km muestra una fecha aproximada ("Vence aprox. el 12/11"), calculada con lo que anduvo el vehículo en los últimos 60 días.
- Los planes por km aparecen en "Próximos vencimientos" según esa fecha.
- Si el vehículo no tiene lecturas suficientes, el plan se muestra aparte con los km que faltan.
- Una lectura de km mal cargada no cambia la estimación.
- El estado del plan sigue dependiendo de los km reales.

## CAM-139 - Reportar un defecto durante el viaje

Sprint: 9.

### Actor/es
- Chofer
- Admin

### Funcionalidad
Como chofer, quiero reportar un problema del vehículo en el momento en que aparece, para que el admin se entere enseguida sin esperar al final del viaje.

### Valor aportado
Si algo se rompía en la ruta, nadie se enteraba hasta que el chofer cerraba el viaje, y el aviso de un defecto grave llegaba tarde.

### Criterios de aceptación
- Con un viaje abierto, tengo un botón "Reportar un problema".
- Elijo qué control falla (u "Otro problema"), la gravedad, una descripción escrita o dictada y una foto, obligatoria si es bloqueante.
- Un defecto bloqueante avisa al admin en el momento y deja el vehículo "No disponible"; el viaje sigue abierto y lo cierro con el post-viaje.
- Solo puede reportar el chofer que tiene el viaje abierto.
- El admin ve el defecto en Defectos marcado "Reportado en viaje" y lo planifica como cualquier otro.
- El reporte no cuenta como una inspección en la actividad del chofer.

## CAM-140 - Comparar vehículos: reparar o reemplazar

Sprint: 9.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero comparar los vehículos de la flota por costo, disponibilidad y fallas repetidas, para decidir cuáles conviene seguir reparando y cuáles reemplazar.

### Valor aportado
Los datos existían, pero de a un vehículo: para comparar dos había que abrir cada uno y anotar los números.

### Criterios de aceptación
- Quiero una vista "Comparar" en Vehículos, con un filtro de período.
- Cada vehículo activo muestra gastos de mantenimiento y operativos, km recorridos, costo por km, disponibilidad y fallas recurrentes, con una fila de promedio de la flota.
- Puedo ordenar por cualquier columna; los vehículos sin dato quedan al final con "Sin datos".
- Puedo ver el costo por km solo de mantenimiento o total.
- Los vehículos peores que el promedio en al menos dos indicadores se marcan "Para revisar", con el motivo.
- Los números coinciden con los del detalle de cada vehículo para el mismo período.
- Al tocar un vehículo, se abre su detalle.

## CAM-146 - Daños ocurridos durante el viaje

Sprint: 9.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero saber qué defectos aparecieron durante un viaje, para saber cuándo y con qué chofer ocurrió un daño.

### Valor aportado
Nada comparaba la inspección del pre-viaje con la del post-viaje: había que abrir las dos y compararlas a mano.

### Criterios de aceptación
- Un defecto del post-viaje se marca "Ocurrió en el viaje" si ese control dio OK en el pre-viaje del mismo viaje.
- Los daños nuevos en la carrocería siempre se marcan; un control que ya fallaba en el pre-viaje, no.
- Quiero ver la marca con el chofer, las fechas del viaje y la hora del pre-viaje en OK, y filtrar por ella en Defectos.
- Los defectos viejos también se marcan, con lo que ya estaba guardado.
- Solo el admin ve esta marca.

## CAM-148 - Pausar una orden de trabajo con un motivo

Sprint: 9.

### Actor/es
- Técnico
- Admin

### Funcionalidad
Como técnico, quiero pausar una orden de trabajo e indicar el motivo, para que el admin sepa que no está olvidada y qué la está frenando.

### Valor aportado
Una OT frenada porque falta un repuesto se veía igual que una en la que se está trabajando.

### Criterios de aceptación
- Una OT en proceso se puede pausar eligiendo un motivo: esperando repuesto, esperando al proveedor, esperando el vehículo u otro (con comentario obligatorio).
- La retomo cuando puedo seguir.
- Una OT en espera no se puede finalizar sin retomarla, pero sí cancelar.
- El admin ve "En espera" con su motivo en el listado, el detalle y el historial del vehículo, y puede filtrar por ese estado.
- En el detalle se ve cuánto tiempo estuvo en espera en total.
- El vehículo sigue "No disponible" si el defecto es bloqueante.

## CAM-152 - Aviso de kilometraje fuera de lo normal en un viaje

Sprint: 9.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero enterarme cuando un viaje tiene un kilometraje muy distinto al habitual del vehículo, para detectar errores de carga o un uso indebido del camión.

### Valor aportado
Un km mal cargado o un viaje fuera de lo normal pasaban sin que nadie lo notara.

### Criterios de aceptación
- Al cerrar un viaje, si recorrió más del doble o menos de la mitad del promedio de los últimos 10 viajes del vehículo, recibo un aviso.
- También recibo un aviso si el km del post-viaje es menor al que ya tenía el vehículo.
- El aviso muestra el vehículo, el chofer, los km del viaje y el promedio.
- El viaje queda marcado en el historial del vehículo.
- Con menos de 3 viajes previos no se compara contra el promedio.

## CAM-153 - Inspección sin conexión

Sprint: 9.

### Actor/es
- Chofer

### Funcionalidad
Como chofer, quiero completar la inspección aunque me quede sin señal, para no perder lo que cargué ni quedar trabado al salir.

### Valor aportado
El chofer trabaja en playones, rutas y depósitos donde la señal es mala, y una inspección que no se podía enviar se perdía.

### Criterios de aceptación
- Si no hay conexión al enviar, la inspección y sus fotos se guardan en el celular.
- La app muestra que la inspección quedó pendiente de envío.
- Cuando vuelve la conexión se envía sola, en el orden en que se hicieron, con la hora real de cada una.
- Con un pre-viaje pendiente, puedo hacer el post-viaje de ese vehículo sin señal.
- Si el servidor rechaza una inspección, veo el motivo y la puedo descartar.
- Un envío repetido no duplica la inspección.

## CAM-154 - Importar la flota desde un Excel o CSV

Sprint: 9.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero cargar todos mis vehículos de una vez desde un archivo, para empezar a usar la app sin darlos de alta uno por uno.

### Valor aportado
Dar de alta una flota de 200 vehículos de a uno es la primera barrera para empezar a usar el producto.

### Criterios de aceptación
- Quiero una opción "Importar" en Vehículos que acepte un Excel o un CSV.
- Puedo descargar una planilla de ejemplo con las columnas esperadas.
- Antes de guardar veo una vista previa con las filas válidas y las que tienen errores, con el motivo.
- Una patente que ya existe, o repetida en el archivo, se informa como error y no se duplica.
- Al confirmar, se crean solo las filas válidas.

## CAM-155 - Indicadores de cumplimiento del mantenimiento

Sprint: 9.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero ver qué tan al día está la flota con el mantenimiento, para saber si el preventivo se cumple y dónde se traba el taller.

### Valor aportado
Mide si el preventivo se hace a tiempo, que es la promesa del producto.

### Criterios de aceptación
- Quiero una tarjeta "Cumplimiento del mantenimiento" en el Resumen, con un período a elegir.
- Veo el porcentaje de mantenimientos preventivos hechos a tiempo frente a los hechos después de vencer.
- Veo el tiempo promedio desde que se reporta un defecto hasta que se resuelve.
- Veo las órdenes de trabajo abiertas según su antigüedad: menos de 7 días, de 7 a 30 y más de 30.

## CAM-136 - Tests del frontend

Sprint: 9.

### Actor/es
- Equipo de desarrollo

### Funcionalidad
Como equipo, queremos tests automáticos en el frontend, para cambiar las pantallas críticas sin miedo a romperlas.

### Valor aportado
El backend tenía tests desde el principio y el frontend ninguno. El flujo de inspección del chofer es el más sensible y el que más cambió en esta entrega.

### Criterios de aceptación
- `npm test` corre la suite con Vitest, Testing Library y jsdom.
- Hay tests del checklist, del flujo de pre-viaje y post-viaje, del servicio de inspecciones y de las utilidades nuevas.
- Las historias siguientes de la entrega suman sus propios tests.

## CAM-141 - Aviso al chofer: se resolvió el defecto que reportó

Sprint: 10.

### Actor/es
- Chofer

### Funcionalidad
Como chofer, quiero enterarme cuando se resuelve un defecto que reporté, para saber que no quedó en la nada y que vale la pena seguir reportando.

### Valor aportado
El chofer reportaba un defecto y no volvía a saber de él, salvo que fuera bloqueante.

### Criterios de aceptación
- Cuando se finaliza la OT de un defecto que reporté, recibo un aviso con el vehículo, el defecto y lo que se hizo ("AF102ZT: se resolvió «Luz de freno». Lámpara reemplazada.").
- Si era el único bloqueante, recibo solo "Vehículo disponible", no los dos.
- Cancelar la OT, o finalizar una que no viene de un defecto, no avisa.
- Con el aviso apagado en Notificaciones, no se manda.
- Una falla al avisar no le da error a quien finalizó la OT.

## CAM-142 - Defectos abiertos sin atender

Sprint: 10.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero que me avisen cuando un defecto lleva días abierto sin que nadie lo atienda, para que un problema chico no quede olvidado hasta volverse caro.

### Valor aportado
Un defecto no bloqueante no frena al vehículo, así que podía quedar abierto indefinidamente sin OT ni turno.

### Criterios de aceptación
- Un defecto está sin atender si está abierto, lleva más de 7 días (configurable) y no tiene una OT abierta ni un turno programado. Una OT o un turno cancelados no cuentan.
- Recibo un solo aviso por vehículo, que dice cuántos defectos tiene sin atender.
- Cada defecto se avisa una sola vez.
- En Defectos veo la marca "Sin atender hace N días" y un filtro para ver solo esos.
- En el Resumen, un contador "N sin atender" lleva a ese filtro.
- Un vehículo dado de baja no avisa.

## CAM-143 - Turno de taller: aviso al chofer y advertencia antes de salir

Sprint: 10.

### Actor/es
- Chofer
- Admin

### Funcionalidad
Como chofer, quiero enterarme cuando un vehículo tiene turno de taller, para no salir con él y que se pierda el turno.

### Valor aportado
Programar un mantenimiento no avisaba a los choferes, y nada impedía salir de viaje con un vehículo que tenía turno en una hora.

### Criterios de aceptación
- Si tengo un vehículo en viaje con turno de taller hoy o mañana, recibo un aviso con el vehículo, la fecha y la hora.
- En la lista de vehículos veo la marca "Turno de taller hoy, 15:00".
- Al empezar el pre-viaje, por la lista o por el QR, veo una advertencia y puedo seguir igual.
- Si salgo igual, el admin recibe un aviso.
- Un turno cancelado o hecho no avisa ni se muestra; uno reprogramado se avisa de nuevo.
- Un turno a las 22:00 se ve ese día, no el siguiente.

## CAM-149 - Comentarios en la orden de trabajo

Sprint: 10.

### Actor/es
- Admin
- Técnico

### Funcionalidad
Como admin o técnico, quiero dejar comentarios dentro de una orden de trabajo, para coordinar el trabajo sin salir de la app y que quede registrado por qué se decidió cada cosa.

### Valor aportado
Las preguntas ("¿lo reparo o lo cambio?") se resolvían por fuera, y la decisión se perdía.

### Criterios de aceptación
- Quiero una sección "Comentarios" en el detalle de la OT, del más viejo al más nuevo, con autor y fecha.
- Puedo dictar el comentario.
- Si comenta el técnico, el admin recibe un aviso que lleva a la OT; si comenta el admin, lo recibe el técnico asignado.
- Nadie recibe aviso por su propio comentario.
- Los comentarios no se editan ni se borran.
- En una OT cerrada los comentarios se ven y no se puede agregar otro.

## CAM-150 - Plantillas de checklist

Sprint: 10.

### Actor/es
- Admin

### Funcionalidad
Como admin, quiero armar una plantilla de checklist y aplicarla a varios vehículos, para no cargar los mismos controles uno por uno cada vez que sumo un camión.

### Valor aportado
Con diez camiones frigoríficos, "Temperatura de la cámara" se cargaba diez veces.

### Criterios de aceptación
- Quiero una sección "Plantillas de checklist" en Vehículos para crear, editar y borrar plantillas.
- Puedo aplicar una plantilla a varios vehículos a la vez, al crear un vehículo o desde su pestaña Checklist.
- Aplicarla otra vez no duplica controles, y el resultado dice cuántos se agregaron y cuántos se saltearon.
- Editar o borrar la plantilla no cambia los vehículos ni las inspecciones viejas.
- Un vehículo dado de baja no se puede elegir.

## CAM-156 - Combustible: cargas, consumo y aviso de consumo anormal

Sprint: 10.

### Actor/es
- Chofer
- Admin

### Funcionalidad
Como admin, quiero registrar las cargas de combustible de cada vehículo y ver cuánto consume, para controlar el gasto y detectar desperdicio o robo.

### Valor aportado
El combustible es uno de los costos más grandes de una flota, y no estaba en el sistema.

### Criterios de aceptación
- Como chofer, quiero cargar combustible desde la tarjeta del vehículo: litros, monto, km y si llené el tanque.
- Como admin, quiero una pestaña Combustible en el vehículo con el resumen, el consumo por mes y las cargas, y poder corregirlas o borrarlas.
- Una carga con menos km que la anterior se rechaza.
- El consumo se calcula de un tanque lleno al siguiente.
- Un consumo muy por encima del promedio del vehículo genera un aviso.
- Las cargas aparecen en Gastos como combustible y suman en el costo del vehículo.
- Los remolques no cargan combustible.

## CAM-157 - Neumáticos, tipos de vehículo y remolques

Sprint: 10.

### Actor/es
- Admin
- Chofer

### Funcionalidad
Como admin, quiero saber qué cubierta tiene cada vehículo en cada posición y cuánto rindió, para cambiarlas a tiempo y gastar menos en neumáticos.

### Valor aportado
Los neumáticos son el segundo costo de una flota después del combustible, y cambiarlos tarde es un riesgo de seguridad.

### Criterios de aceptación
- Cada vehículo tiene un tipo de un catálogo (utilitario, camión rígido, tractor, semirremolque, acoplado), que define sus posiciones.
- Quiero ver vehículos y remolques en pestañas separadas, con "Enganchado a" en los remolques.
- Como chofer, elijo en el pre-viaje qué remolque llevo, y los km del viaje se le suman al remolque.
- Quiero ver el vehículo dibujado desde arriba, con cada cubierta pintada según su estado, y al tocarla ver qué se reportó, quién y cuándo.
- Puedo montar, cambiar, sacar y rotar cubiertas; al rotar, sus km se siguen sumando en la nueva posición. El auxilio no suma km.
- Como chofer, al marcar "Neumáticos" con defecto, toco la cubierta en el dibujo y elijo lo que veo (pinchada, corte en el flanco, poco dibujo, etc.), también sin conexión.
- Un problema en una cubierta genera un aviso al admin.
- Quiero un inventario de cubiertas con tres vistas: por vehículo, stock y bajas, y costo por km por marca.

## CAM-159 - Auditoría de UI/UX

Sprint: 10.

### Actor/es
- Equipo de desarrollo

### Funcionalidad
Como equipo, queremos revisar la paleta de colores y la interfaz de toda la aplicación, para saber qué mejorar antes de seguir sumando pantallas.

### Valor aportado
La aplicación creció mucho en esta entrega. Revisarla entera, con datos medidos, ordena las prioridades de la próxima.

### Criterios de aceptación
- Se recorrieron las pantallas del chofer, el técnico y el admin en celular (390 px) y en escritorio (1440 px).
- Se midió el contraste de los colores de estado contra WCAG 2.2 AA, los tamaños de los botones y los toques necesarios para completar un pre-viaje.
- El resultado es una tabla de 29 hallazgos con pantalla, rol, severidad, evidencia y recomendación, y los cinco problemas de mayor impacto con la oportunidad que abre cada uno.

## Bugs corregidos

Sprint: 9.

- **CAM-144 - Las fallas recurrentes no unían el mismo control del pre-viaje y del post-viaje.** Luces y Fugas tenían un id en el pre-viaje y otro en el post-viaje, así que dos fallas en cada momento se contaban como dos controles con 2 fallas y no llegaban a las 3 de la regla. Ahora se cuentan como un solo control, en el listado y en el aviso, y los avisos ya enviados se tienen en cuenta para no repetirlos.

