# Guía de Compilación y Ejecución

## Requisitos Previos

### Software Necesario
1. **Java Development Kit (JDK) 17 o superior**
   - Descargar desde: https://www.oracle.com/java/technologies/downloads/
   - Verificar instalación: `java -version`

2. **Apache Maven 3.8 o superior**
   - Descargar desde: https://maven.apache.org/download.cgi
   - Verificar instalación: `mvn --version`

3. **Git (opcional, pero recomendado)**
   - Para gestionar versiones
   - Descargar desde: https://git-scm.com/

4. **Navegador Web Moderno**
   - Chrome, Firefox, Safari o Edge

## Pasos de Compilación

### Paso 1: Clonar/Descargar el Proyecto

```bash
# Si tienes Git
git clone <repositorio-url>
cd ProyectoFinalETD

# O simplemente extrae el archivo ZIP
# y navega a la carpeta del proyecto
```

### Paso 2: Limpiar y Compilar

```bash
# Limpiar compilaciones anteriores
mvn clean

# Compilar el proyecto (descargará todas las dependencias)
mvn compile
```

**Tiempo esperado**: 2-5 minutos en primer intento (menos en posteriores)

### Paso 3: Ejecutar Pruebas Unitarias

```bash
# Ejecutar todas las pruebas
mvn test

# Ejecutar una prueba específica
mvn test -Dtest=GraphTest
mvn test -Dtest=PriorityQueueTest
mvn test -Dtest=LinkedListTest
mvn test -Dtest=BinarySearchTreeTest
```

**Resultado esperado**: Todas las pruebas deben pasar ✅

### Paso 4: Empaquetar la Aplicación

```bash
# Crear JAR ejecutable
mvn package

# El JAR estará en: target/tech-park-uq-1.0.0.jar
```

## Ejecución de la Aplicación

### Opción A: Ejecutar directamente con Maven

```bash
# Desde la carpeta del proyecto
mvn spring-boot:run
```

**Salida esperada**:
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_|\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.2.0)

2024-04-28 10:00:00.000  INFO ... : Starting TechParkApplication v1.0.0
...
2024-04-28 10:00:05.123  INFO ... : Tomcat started on port(s): 8080
2024-04-28 10:00:05.150  INFO ... : Started TechParkApplication in 5.123 seconds
```

### Opción B: Ejecutar el JAR generado

```bash
# Primero compilar y empaquetar
mvn package

# Ejecutar el JAR
java -jar target/tech-park-uq-1.0.0.jar
```

### Opción C: Ejecutar sin tests (más rápido)

```bash
mvn spring-boot:run -DskipTests
```

## Acceso a la Aplicación

Una vez que la aplicación esté corriendo:

1. Abre tu navegador web
2. Ve a: `http://localhost:8080`
3. Usa las credenciales de prueba:

| Tipo | Usuario | Contraseña |
|------|---------|-----------|
| Admin | admin | admin123 |
| Operador | operator | operator123 |
| Visitante | visitor | visitor123 |

## Comandos Útiles de Maven

```bash
# Ver dependencias
mvn dependency:tree

# Generar reporte de pruebas
mvn surefire-report:report

# Limpiar cache local
mvn clean -U

# Instalar sin ejecutar pruebas
mvn install -DskipTests

# Compilar y empaquetar
mvn clean package

# Ejecutar solo pruebas de una clase
mvn test -Dtest=GraphTest#testDijkstra

# Ver propiedades del proyecto
mvn help:describe

# Actualizar snapshots
mvn -U clean install
```

## Troubleshooting

### Error: "Maven command not found"
```bash
# En Windows, agregar Maven al PATH
# En Linux/Mac, instalar Maven correctamente
brew install maven  # macOS

# Verificar
mvn --version
```

### Error: "Java version not supported"
```bash
# Verificar versión de Java
java -version

# Debe ser 17 o superior
# Actualizar JAVA_HOME si es necesario
export JAVA_HOME=/path/to/java17  # Linux/Mac
set JAVA_HOME=C:\Program Files\Java\jdk-17  # Windows
```

### Puerto 8080 ya en uso
```bash
# Opción 1: Cambiar puerto en application.yml
# server.port=8081

# Opción 2: Matar proceso en puerto 8080
# Linux/Mac:
lsof -i :8080
kill -9 <PID>

# Windows:
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

### Error de compilación: "cannot find symbol"
```bash
# Limpiar cache de Maven
mvn clean
rm -rf ~/.m2/repository  # Linux/Mac
# O eliminar carpeta .m2 en Windows

# Volver a compilar
mvn compile
```

### Base de datos H2 no accesible
```
# Acceder a consola H2 en:
http://localhost:8080/h2-console

# Configuración por defecto:
Driver Class: org.h2.Driver
JDBC URL: jdbc:h2:mem:testdb
User Name: sa
Password: (dejar en blanco)
```

## Verificación de Instalación

### Verificar que todo está correcto:

```bash
# 1. Verificar Java
java -version
# Debe mostrar: java version "17" o superior

# 2. Verificar Maven
mvn --version
# Debe mostrar: Apache Maven 3.8.0 o superior

# 3. Compilar
mvn clean compile
# Debe terminar con: BUILD SUCCESS

# 4. Pruebas
mvn test
# Debe mostrar todos los tests PASSED

# 5. Empaquetar
mvn package
# Debe crear JAR en target/

# 6. Ejecutar
mvn spring-boot:run
# Debe mostrar "Tomcat started on port(s): 8080"
```

## Desarrollo e Iteración

Durante el desarrollo, puedes usar:

```bash
# Compilar continuamente en background
mvn compile watch

# O en terminal separada:
while true; do mvn compile; sleep 30; done

# Spring Boot DevTools (recarga automática)
# Agregará reinicio automático cuando cambies archivos
```

## Deployment

### Para producción:

```bash
# 1. Compilar optimizado
mvn clean package -DskipTests -P production

# 2. Crear JAR ejecutable
java -jar target/tech-park-uq-1.0.0.jar \
    --spring.profiles.active=production \
    --server.port=8080 \
    --spring.datasource.url=jdbc:mysql://localhost:3306/techpark
```

## Documentación Adicional

- **Documentación Maven**: https://maven.apache.org/guides/
- **Documentación Spring Boot**: https://spring.io/projects/spring-boot
- **Documentación Java**: https://docs.oracle.com/en/java/

---

**Última actualización**: 2024
