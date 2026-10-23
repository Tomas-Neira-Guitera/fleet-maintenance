# CAM-119 — Push al navegador

Acompaña a `openapi.yaml` (mismo directorio) con las decisiones que el spec no
expresa por sí solo.

## Motivación

La campanita (CAM-96) solo sirve si la app está abierta. El chofer que se olvidó
de cerrar el viaje o el técnico al que le asignaron una OT no la tienen abierta:
la push les llega al celular o a la compu como cualquier otra notificación del
sistema, con la app cerrada.

## Endpoints

| Método | Endpoint | Qué hace |
|---|---|---|
| GET | `/api/push/public-key` | Clave VAPID pública, para suscribirse |
| POST | `/api/push/subscriptions` | Activa las push en este navegador |
| DELETE | `/api/push/subscriptions?endpoint=` | Las desactiva |
| POST | `/api/push/test` | Manda una push de prueba a mis navegadores |

Cualquier rol, con su JWT. Cada usuario maneja solo sus suscripciones.

## Cómo funciona

1. El front registra un service worker (`public/sw.js`) al arrancar.
2. En el panel de la campanita, "Activar en este dispositivo" pide el permiso
   del navegador, se suscribe con la clave pública y manda la suscripción al
   backend.
3. Cada aviso que tiene la push entre sus canales (por defecto, todos; se elige
   por tipo en la configuración de CAM-117) sale como push a todos los
   navegadores de sus destinatarios.
4. El service worker la muestra; tocarla abre la app (o la trae al frente) en el
   `link` del aviso.

## Decisiones

### 1. La push es un canal más
Desde CAM-117 la push es uno de los tres canales de entrega (`PushNotificationSender`,
junto a la campanita y el email). `DefaultNotificationService` decide, según la
configuración de cada tipo, a quién y por qué canales sale cada aviso; los
avisos concretos no saben nada de la push. Reemplaza al decorador
`PushingNotificationService` de la primera versión, que la mandaba siempre.

### 2. La push sale después del commit, y en segundo plano
Se manda recién cuando se confirma la transacción del aviso: si se deshace, no
llega una push de algo que no pasó. Y es `@Async`: el envío sale a internet (los
servicios de push de Google, Mozilla o Apple) y no tiene que demorar la
respuesta de quien asignó la OT.

### 3. Una suscripción vencida se borra sola
Si el servicio de push responde 404 o 410 (el usuario revocó el permiso o el
navegador venció la suscripción), se borra. Cualquier otro error queda en el log
y no frena a los demás navegadores.

### 4. Un navegador es de un solo usuario
Si en la misma compu entra otro usuario y abre la campanita, la suscripción pasa
a ser suya. Además, el front la da de baja al cerrar sesión, así el siguiente que
use la compu no recibe avisos del anterior. Solo al cerrar sesión con el token
vigente: si la sesión venció (el JWT dura 12 h), las push siguen, porque el
chofer que abre la app al día siguiente sigue siendo el mismo y no tiene por qué
perderlas.

### 4b. Solo los servicios de push conocidos, y con límite de tiempo
El backend le hace un POST al `endpoint` de cada suscripción, así que aceptar
cualquier URL https serviría para hacerle pedidos a hosts internos (SSRF). Solo
se aceptan los dominios de Google, Mozilla, Microsoft y Apple (ver
`openapi.yaml`), en el puerto 443 y sin usuario en la URL. Las claves se validan
al suscribir (P-256 de 65 bytes y secreto de 16), para no guardar una suscripción
que falle en cada envío. Cada envío espera la respuesta como mucho 10 segundos:
la librería no pone límite, y un servicio que no contesta colgaría el hilo.
Solo un 2xx cuenta como entregado.

### 5. Claves VAPID de desarrollo commiteadas
Igual que el secreto del JWT: el par de `application.yml` es solo para
desarrollo, y está ahí para que el proyecto ande sin configurar nada. Para un
deploy hay que generar otro y pisarlo en `application-local.yml`. Cambiar las
claves invalida las suscripciones existentes (hay que volver a activar). Sin
claves, la push queda apagada: `publicKey` es null y el front no la ofrece.

Para generar un par nuevo (Node, sin instalar nada):

```bash
node -e "const c=require('crypto');const j=c.generateKeyPairSync('ec',{namedCurve:'prime256v1'}).privateKey.export({format:'jwk'});const b=s=>Buffer.from(s,'base64url');console.log('public-key:',Buffer.concat([Buffer.from([4]),b(j.x),b(j.y)]).toString('base64url'));console.log('private-key:',j.d)"
```

### 6. Dependencia nueva
`nl.martijndwars:web-push` + Bouncy Castle (`bcprov-jdk18on`), decidido con
Guido el 2026-10-23. Firmar el JWT de VAPID y cifrar el mensaje (RFC 8291) a mano
no tenía sentido.

## Límites conocidos

- **Solo anda en `localhost` o HTTPS.** El navegador no permite push en otro
  origen. Hoy se puede probar en la compu (`http://localhost:5173`), pero no en
  un celular que entra por la IP de la red: eso queda para cuando haya un deploy
  con HTTPS (o un túnel).
- **iPhone:** las push web solo andan si la app está agregada a la pantalla de
  inicio (iOS 16.4+), y hace falta un manifest que todavía no hay.
- **Sin preferencias:** todos los avisos salen como push (CAM-24).
