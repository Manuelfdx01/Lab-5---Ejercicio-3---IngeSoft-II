# Laboratorio 5 – Ejercicio 3: Implementación de HTTPS y certificados digitales

## 1. Descripción del proyecto

Este repositorio contiene la implementación del ejercicio 3 del Laboratorio 5, cuyo objetivo es configurar una API REST desarrollada con Spring Boot para utilizar HTTPS mediante un certificado digital autofirmado.

También incluye un cliente Java que utiliza `HttpClient` para consumir el servicio HTTPS y verificar el certificado del servidor mediante un almacén de confianza (truststore).

El proyecto permite demostrar:

- Generación de certificados digitales mediante Java Keytool.
- Configuración de HTTPS en Spring Boot.
- Validación de certificados autofirmados.
- Consumo de una API HTTPS mediante Java HttpClient.
- Configuración de truststore.
- Implementación y comprobación de la cabecera HSTS.
- Conceptos de Let’s Encrypt, ACME y SSL stripping.

## 2. Estructura del repositorio

```text
Lab5/
│
├── laboratorio5/
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── co/edu/unal/laboratorio5/
│   │       │       ├── Laboratorio5Application.java
│   │       │       ├── SaludController.java
│   │       │       └── HstsFilter.java
│   │       │
│   │       └── resources/
│   │           └── application.properties
│   │
│   ├── pom.xml
│   └── mvnw
│
├── cliente-java/
│   └── SaludApiClient.java
│
├── .gitignore
└── README.md
```

Los certificados y almacenes de llaves se generan localmente y no se incluyen en el repositorio.

## 3. Requisitos

- Linux, Windows o macOS.
- Java JDK 17 o la versión compatible con el proyecto Spring Boot.
- Maven o Maven Wrapper.
- OpenSSL.
- Git.
- Java Keytool (incluido en el JDK).

Verificar las instalaciones:

```bash
java -version
javac -version
mvn -version
keytool -help
openssl version
```

## 4. Generación del certificado del servidor

Desde la raíz del proyecto Spring Boot, crear el directorio de recursos si no existe:

```bash
mkdir -p src/main/resources
```

Generar el certificado autofirmado:

```bash
keytool -genkeypair \
-alias seii \
-keyalg RSA \
-keysize 2048 \
-validity 30 \
-storetype PKCS12 \
-keystore src/main/resources/keystore.p12 \
-dname "CN=localhost" \
-ext "SAN=dns:localhost,ip:127.0.0.1"
```

Durante el proceso se solicitará una contraseña. Esta debe utilizarse posteriormente para definir `KEYSTORE_PASSWORD`.

Exportar el certificado público:

```bash
keytool -exportcert \
-alias seii \
-keystore src/main/resources/keystore.p12 \
-rfc \
-file seii.crt
```

Inspeccionar el certificado:

```bash
openssl x509 -in seii.crt -text -noout
```

**Importante:** `keystore.p12` contiene la llave privada del servidor. No debe compartirse ni subirse al repositorio.

## 5. Configuración de variables de entorno

La aplicación utiliza variables de entorno para evitar almacenar contraseñas directamente en el código.

### KEYSTORE_PASSWORD

Contiene la contraseña del almacén de llaves del servidor.

En Linux Mint:

```bash
export KEYSTORE_PASSWORD='TU_CONTRASEÑA'
```

Comprobar que está definida:

```bash
if [ -n "$KEYSTORE_PASSWORD" ]; then
    echo "KEYSTORE_PASSWORD configurada"
fi
```

### TRUSTSTORE_PASSWORD

Contiene la contraseña del almacén de confianza utilizado por el cliente Java.

Definirla:

```bash
export TRUSTSTORE_PASSWORD='TU_CONTRASEÑA'
```

Comprobar:

```bash
if [ -n "$TRUSTSTORE_PASSWORD" ]; then
    echo "TRUSTSTORE_PASSWORD configurada"
fi
```

Las variables deben definirse en la terminal desde la cual se ejecutará el proceso correspondiente.

## 6. Configuración HTTPS de Spring Boot

En `src/main/resources/application.properties` se establece:

```properties
server.port=8443

server.ssl.enabled=true
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-type=PKCS12
server.ssl.key-store-password=${KEYSTORE_PASSWORD}
server.ssl.key-alias=seii
```

La aplicación utiliza el puerto 8443 para las conexiones HTTPS.

## 7. Ejecución del servidor

Desde la carpeta `laboratorio5`:

```bash
export KEYSTORE_PASSWORD='TU_CONTRASEÑA'
./mvnw spring-boot:run
```

El servicio queda disponible en:

```text
https://localhost:8443/api/v1/salud
```

## 8. Pruebas HTTPS mediante curl

### Prueba 1. Conexión sin certificado de confianza

```bash
curl -i https://localhost:8443/api/v1/salud
```

Resultado esperado: error de validación del certificado autofirmado.

### Prueba 2. Conexión proporcionando el certificado

```bash
curl -i --cacert seii.crt \
https://localhost:8443/api/v1/salud
```

Resultado esperado: respuesta HTTP 200.

### Prueba 3. Intento de conexión mediante HTTP

```bash
curl -i http://localhost:8443/api/v1/salud
```

Resultado esperado: error, porque el puerto 8443 está configurado para HTTPS.

## 9. Cliente Java y configuración del truststore

El directorio `cliente-java` contiene `SaludApiClient.java`, que utiliza `HttpClient` para consumir el endpoint HTTPS.

Copiar el certificado público del servidor a `cliente-java`:

```bash
cp laboratorio5/seii.crt cliente-java/
```

Entrar en el directorio:

```bash
cd cliente-java
```

Crear el almacén de confianza:

```bash
keytool -importcert \
-alias seii \
-file seii.crt \
-keystore confianza.p12 \
-storetype PKCS12 \
-noprompt
```

Introducir la contraseña que se utilizará como `TRUSTSTORE_PASSWORD`.

### Compilar el cliente

```bash
javac SaludApiClient.java
```

### Ejecutar sin truststore

```bash
java SaludApiClient
```

Resultado esperado: error `SSLHandshakeException` debido a que Java no confía en el certificado autofirmado.

### Ejecutar con truststore

```bash
export TRUSTSTORE_PASSWORD='TU_CONTRASEÑA'

java \
-Djavax.net.ssl.trustStore=confianza.p12 \
-Djavax.net.ssl.trustStorePassword="$TRUSTSTORE_PASSWORD" \
SaludApiClient
```

Resultado esperado:

```json
{"estado":"ok"}
```

La validación SSL permanece habilitada. El cliente confía en el certificado porque se importó previamente en su almacén de confianza.

## 10. Configuración HSTS

La aplicación incorpora el filtro `HstsFilter`, que agrega la siguiente cabecera cuando la petición utiliza HTTPS:

```http
Strict-Transport-Security: max-age=31536000; includeSubDomains
```

Para verificarla:

```bash
curl -i --cacert seii.crt \
https://localhost:8443/api/v1/salud
```

Se debe comprobar la presencia de `Strict-Transport-Security` en las cabeceras de respuesta.

## 11. Temas teóricos desarrollados

### Let’s Encrypt y ACME

Let’s Encrypt es una autoridad certificadora gratuita que utiliza ACME para automatizar la validación de dominios, emisión y renovación de certificados TLS.

### HSTS

HSTS obliga a los navegadores a utilizar HTTPS durante un periodo determinado y ayuda a prevenir ataques de SSL stripping.

### SSL stripping

Es un ataque Man-in-the-Middle que intenta degradar una conexión HTTPS a HTTP para interceptar información.

## 12. Seguridad del repositorio

El archivo `.gitignore` excluye:

```gitignore
*.p12
*.crt
.env
```

Por seguridad, no se deben publicar:

- Llaves privadas.
- Almacenes de certificados.
- Contraseñas.
- Archivos de variables de entorno.

Cada persona que clone el proyecto deberá generar sus propios certificados y definir sus propias variables de entorno.

## 13. Tecnologías utilizadas

- Java.
- Spring Boot.
- Maven.
- Java HttpClient.
- Java Keytool.
- OpenSSL.
- HTTPS / TLS.
- PKCS12.
- Git y GitHub.

## 14. Objetivo académico

Demostrar la implementación de comunicaciones HTTPS en una API REST, la generación y validación de certificados digitales, el establecimiento de confianza entre servidor y cliente Java y la aplicación de mecanismos de seguridad como HSTS.

---

# Laboratorio 5 – Ejercicio 4: Seguridad avanzada en APIs

## 10. Objetivo del ejercicio

El objetivo del ejercicio 4 es analizar y aplicar mecanismos adicionales de seguridad en APIs REST, incluyendo:

- Evaluación de la configuración TLS de un servicio.
- Análisis de cabeceras HTTP de seguridad.
- Implementación de autenticación mutua TLS (mTLS).
- Verificación del comportamiento de la API con y sin certificado de cliente.
- Comparación entre mTLS y autenticación mediante JWT.
- Análisis de la seguridad de las conexiones HTTPS en Android y del mecanismo de certificate pinning.

Este ejercicio amplía la implementación HTTPS realizada en el ejercicio 3.

## 11. Análisis de seguridad TLS (literal 4.a)

Se utiliza SSL Labs para analizar la configuración TLS del servicio seleccionado en la guía del laboratorio.

**Servicio analizado:** [Completar con el dominio utilizado].

**Resultados obtenidos:**

| Aspecto | Resultado |
|---|---|
| Calificación SSL Labs | [Completar] |
| Versiones TLS soportadas | [Completar] |
| Certificado | [Completar] |
| Observaciones relevantes | [Completar] |

### Factores que influyen en la calificación

Entre los factores que pueden influir en la evaluación se encuentran:

1. **Versiones del protocolo TLS:** el uso de versiones modernas y seguras, como TLS 1.2 y TLS 1.3, evita depender de protocolos obsoletos o vulnerables.
2. **Configuración criptográfica:** los algoritmos de cifrado, los mecanismos de intercambio de claves y la configuración del certificado influyen en la seguridad de la conexión.

La calificación definitiva debe corresponder al resultado obtenido en SSL Labs y no a una estimación.

## 12. Análisis de cabeceras HTTP de seguridad (literal 4.b)

Se puede consultar la respuesta HTTP de las cabeceras de una API pública mediante:

```bash
curl -sI https://api.github.com
```

También se utiliza Mozilla Observatory para evaluar las cabeceras y otras configuraciones de seguridad del servicio seleccionado.

**Servicio analizado:** [Completar].

**Resultado de Mozilla Observatory:** [Completar].

### Principales cabeceras de seguridad

| Cabecera | Función |
|---|---|
| Strict-Transport-Security (HSTS) | Indica al navegador que debe comunicarse con el dominio utilizando HTTPS durante un periodo determinado. |
| Content-Security-Policy (CSP) | Define las fuentes de contenido que el navegador puede cargar y ayuda a mitigar ataques como XSS. |
| X-Content-Type-Options | Con el valor `nosniff`, evita que el navegador intente interpretar el contenido con un tipo MIME diferente al declarado. |
| Referrer-Policy | Controla qué información del origen se envía mediante la cabecera Referer. |

### Aplicación en una API REST

Una API debe utilizar HTTPS y configurar las cabeceras que sean pertinentes para sus consumidores.

- HSTS es útil cuando los clientes son navegadores y el dominio ofrece HTTPS.
- CSP es especialmente relevante cuando el servicio también entrega contenido web que se ejecuta en navegadores.
- X-Content-Type-Options ayuda a evitar interpretaciones incorrectas de los tipos MIME.
- Referrer-Policy controla la información de referencia que puede filtrarse en solicitudes realizadas desde navegadores.

No todas las cabeceras tienen el mismo efecto en clientes HTTP que no son navegadores.

## 13. Implementación de autenticación mutua TLS (literal 4.c)

En el ejercicio 3 se configuró HTTPS para que el cliente verificara la identidad del servidor.

En este ejercicio se amplía la configuración mediante mutual TLS (mTLS). Con este mecanismo, tanto el cliente como el servidor presentan certificados digitales durante el establecimiento de la conexión TLS.

### 13.1. Certificado del cliente

Se genera un almacén PKCS12 para el cliente mediante Java Keytool:

```bash
keytool -genkeypair \
  -alias pagos \
  -keyalg RSA \
  -keysize 2048 \
  -validity 365 \
  -storetype PKCS12 \
  -keystore cliente-pagos.p12 \
  -dname "CN=servicio-pagos"
```

Se exporta el certificado público:

```bash
keytool -exportcert \
  -alias pagos \
  -keystore cliente-pagos.p12 \
  -rfc \
  -file cliente-pagos.crt
```

El archivo PKCS12 contiene la llave privada del cliente, por lo que debe mantenerse protegido.

### 13.2. Truststore del servidor

El servidor necesita confiar en el certificado público del cliente.

Se importa el certificado en el truststore:

```bash
keytool -importcert \
  -alias pagos \
  -file cliente-pagos.crt \
  -noprompt \
  -storetype PKCS12 \
  -keystore src/main/resources/truststore.p12
```

Si se utiliza otro alias para una importación posterior, debe verificarse que el truststore contenga el certificado que corresponde al PKCS12 utilizado por el cliente.

Para inspeccionar las entradas:

```bash
keytool -list -v \
  -keystore src/main/resources/truststore.p12 \
  -storetype PKCS12
```

### 13.3. Configuración de Spring Boot

En `src/main/resources/application.properties` se conserva la configuración HTTPS del ejercicio 3 y se agregan las siguientes propiedades:

```properties
server.ssl.client-auth=need
server.ssl.trust-store=classpath:truststore.p12
server.ssl.trust-store-type=PKCS12
server.ssl.trust-store-password=${TRUSTSTORE_PASSWORD}
```

La configuración completa es:

```properties
server.port=8443

server.ssl.enabled=true
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-type=PKCS12
server.ssl.key-store-password=${KEYSTORE_PASSWORD}
server.ssl.key-alias=seii

server.ssl.client-auth=need
server.ssl.trust-store=classpath:truststore.p12
server.ssl.trust-store-type=PKCS12
server.ssl.trust-store-password=${TRUSTSTORE_PASSWORD}
```

La propiedad `server.ssl.client-auth=need` establece que el certificado del cliente es obligatorio.

Las contraseñas se suministran mediante variables de entorno para evitar almacenarlas directamente en el repositorio.

## 14. Pruebas de autenticación mutua TLS (literal 4.d)

### 14.1. Variables de entorno

Desde la terminal en la que se ejecutará Spring Boot:

```bash
export KEYSTORE_PASSWORD='CONTRASEÑA_DEL_SERVIDOR'
export TRUSTSTORE_PASSWORD='CONTRASEÑA_DEL_TRUSTSTORE'
```

Iniciar el servidor:

```bash
cd laboratorio5
./mvnw spring-boot:run
```

### 14.2. Solicitud sin certificado de cliente

Desde otra terminal, ejecutar:

```bash
curl -v -i --cacert laboratorio5/seii.crt \
  https://localhost:8443/api/v1/salud
```

**Resultado esperado:** la negociación TLS falla porque el servidor exige un certificado de cliente y la solicitud no lo proporciona.

### 14.3. Solicitud con certificado de cliente

Ejecutar:

```bash
curl -v -i --cacert laboratorio5/seii.crt \
  --cert-type P12 \
  --cert "$HOME/Descargas/Lab5/certificados/cliente-pagos.p12:CONTRASEÑA_DEL_CLIENTE" \
  https://localhost:8443/api/v1/salud
```

**Resultado esperado:** si el certificado del cliente es válido y está incluido en el truststore del servidor, la conexión TLS se establece y la API responde con HTTP 200.

La contraseña del PKCS12 debe reemplazarse por la que se utilizó al generar el certificado.

### 14.4. Interpretación

La diferencia entre ambas solicitudes demuestra el funcionamiento de mTLS:

- Sin certificado: el servidor rechaza la negociación TLS.
- Con certificado válido: el servidor autentica al cliente y permite establecer la conexión.

El rechazo sin certificado ocurre durante el handshake TLS, antes de que la solicitud llegue al controlador REST. Por tanto, no corresponde a una respuesta HTTP 401 o 403 generada por el endpoint.

## 15. Comparación entre mTLS y JWT (literal 4.e)

| Característica | mTLS | JWT |
|---|---|---|
| Mecanismo | Certificados digitales en TLS | Token firmado |
| Identificación | Certificado del cliente | Identidad y claims del token |
| Autenticación | Durante el handshake TLS | Durante la solicitud HTTP |
| Gestión | Certificados, llaves y confianza | Emisión, expiración y validación de tokens |
| Revocación | Requiere mecanismos de gestión de certificados | Puede controlarse mediante expiración y listas de revocación o mecanismos adicionales |
| Uso habitual | Comunicación entre servicios y clientes controlados | APIs consumidas por aplicaciones web, móviles y otros servicios |

### ¿Cuándo utilizar cada mecanismo?

mTLS es especialmente útil cuando se requiere autenticar criptográficamente a los dos extremos de una conexión, por ejemplo, en la comunicación entre servicios internos.

JWT es conveniente cuando diferentes usuarios o aplicaciones necesitan autenticarse y transportar información de autorización mediante tokens.

Ambos mecanismos pueden complementarse: mTLS protege y autentica la conexión, mientras JWT permite identificar usuarios y controlar permisos a nivel de aplicación.

## 16. Seguridad de conexiones HTTPS en Android y certificate pinning (literal 4.f)

Android dispone de mecanismos para controlar la seguridad de las conexiones de red de una aplicación.

### 16.1. Network Security Configuration

Mediante un archivo `network_security_config.xml` se pueden definir políticas de seguridad de red, por ejemplo:

- Permitir o restringir el tráfico HTTP sin cifrar.
- Establecer reglas de confianza para certificados.
- Definir políticas específicas para determinados dominios.

Para evitar tráfico HTTP sin cifrar, una aplicación puede declarar:

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
</network-security-config>
```

Este archivo se referencia desde el manifiesto de Android mediante el atributo `android:networkSecurityConfig` de la etiqueta `application`.

### 16.2. Certificate pinning

Certificate pinning es una técnica que permite restringir la confianza de una aplicación a un certificado o clave pública esperada, en lugar de confiar únicamente en las autoridades certificadoras del sistema.

Una alternativa es comparar el hash SHA-256 de la clave pública Subject Public Key Info (SPKI) del servidor con un valor previamente conocido.

Un ejemplo de cálculo mediante OpenSSL es:

```bash
openssl s_client -connect api.github.com:443 \
  -servername api.github.com </dev/null 2>/dev/null |
openssl x509 -pubkey -noout |
openssl pkey -pubin -outform DER |
openssl dgst -sha256 -binary |
openssl base64 -A
```

El resultado es un hash codificado en Base64 que puede utilizarse como referencia para una política de pinning.

El pin debe verificarse y actualizarse mediante una estrategia de rotación que contemple certificados y claves futuras. Una configuración incorrecta puede impedir que la aplicación se conecte cuando el servidor renueve su certificado o clave.

**Nota:** En este laboratorio se documenta el mecanismo de certificate pinning, pero no se incorpora un archivo independiente con el pin calculado.

## 17. Consideraciones de seguridad del repositorio

Los almacenes PKCS12 y las llaves privadas no deben subirse a GitHub.

El repositorio utiliza `.gitignore` para excluir archivos sensibles, entre ellos:

```gitignore
*.p12
*.crt
*.env
*.class
target/
```

Las contraseñas deben configurarse mediante variables de entorno:

- `KEYSTORE_PASSWORD`
- `TRUSTSTORE_PASSWORD`

Los certificados y almacenes necesarios para ejecutar el laboratorio deben generarse localmente siguiendo las instrucciones de este documento.

## 18. Conclusiones

El ejercicio 4 amplía la seguridad implementada en el ejercicio 3 al incorporar mecanismos de autenticación y protección adicionales.

La implementación de mTLS permite exigir la autenticación del cliente mediante certificados digitales durante la negociación TLS. Además, el análisis de cabeceras HTTP, la evaluación TLS y la revisión de mecanismos de seguridad para Android permiten comprender cómo se complementan las distintas capas de protección de una API REST.

En conjunto, los ejercicios demuestran la importancia de proteger tanto el canal de comunicación como la identidad de los participantes y la información intercambiada.

