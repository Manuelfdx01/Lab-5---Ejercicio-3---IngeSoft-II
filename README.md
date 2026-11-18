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
