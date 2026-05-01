# TECH-PARK UQ - Sistema de Gestión de Parque de Atracciones Inteligente

## Descripción del Proyecto

Sistema integral de gestión para un parque de atracciones que incluye:
- Control de acceso de visitantes
- Gestión de colas virtuales inteligentes
- Administración de atracciones y zonas
- Seguimiento de mantenimiento preventivo
- Sistema de alertas por clima
- Reportes y estadísticas en tiempo real

## Características Principales

### Estructuras de Datos Personalizadas
- **Grafo**: Mapa del parque con algoritmos de Dijkstra y BFS
- **Cola de Prioridad**: Gestión de filas con prioridad Fast-Pass
- **Listas Enlazadas**: Historial de visitas y operadores por zona
- **Árbol Binario de Búsqueda**: Búsquedas rápidas de atracciones
- **Set Personalizado**: Atracciones favoritas de visitantes

### Roles del Sistema
1. **Administrador**: Gestión completa del parque, reportes, personal
2. **Operador**: Control de atracciones asignadas, revisión técnica
3. **Visitante**: Acceso a atracciones, filas virtuales, favoritos

## Requisitos

### Backend Java
- Java 17 o superior
- Maven 3.8+
- Spring Boot 3.2.0

### Frontend
- Navegador web moderno (Chrome, Firefox, Safari, Edge)
- No requiere instalación adicional

## Instalación y Configuración

### 1. Clonar o descargar el proyecto
```bash
cd ProyectoFinalETD
```

### 2. Compilar el proyecto Maven
```bash
mvn clean install
```

### 3. Ejecutar la aplicación

#### Opción A: Con Maven
```bash
mvn spring-boot:run
```

#### Opción B: Ejecutar el JAR generado
```bash
java -jar target/tech-park-uq-1.0.0.jar
```

La aplicación estará disponible en: `http://localhost:8080`

## Uso de la Aplicación

### Acceso Initial

Abre tu navegador y ve a `http://localhost:8080`

#### Credenciales de Prueba

| Rol | Usuario | Contraseña |
|-----|---------|-----------|
| Administrador | admin | admin123 |
| Operador | operator | operator123 |
| Visitante | visitor | visitor123 |

### Panel de Administrador
- **Dashboard**: Vista general de estadísticas
- **Atracciones**: Gestión y estado
- **Zonas**: Capacidad y ocupación
- **Operadores**: Asignaciones
- **Reportes**: Ingresos, visitantes, cierres
- **Mapa Interactivo**: Visualización gráfica

### Panel del Operador
- **Mi Zona**: Atracciones asignadas
- **Fila Virtual**: Control de acceso
- **Revisión Técnica**: Mantenimiento
- **Cambiar Estado**: Abrir/cerrar atracciones
- **Alertas**: Sistema de avisos

### Panel del Visitante
- **Mapa**: Ubicación de atracciones
- **Atracciones**: Catálogo disponible
- **Favoritos**: Mis atracciones preferidas
- **Mi Fila**: Posición y estimado de espera
- **Historial**: Visitas realizadas

## Estructura del Proyecto

```
ProyectoFinalETD/
├── pom.xml                                 # Configuración Maven
├── src/
│   ├── main/
│   │   ├── java/com/techpark/
│   │   │   ├── TechParkApplication.java   # Clase principal
│   │   │   ├── model/                      # Entidades
│   │   │   ├── service/                    # Servicios de lógica
│   │   │   ├── controller/                 # Controladores REST
│   │   │   ├── dto/                        # Data Transfer Objects
│   │   │   └── datastructures/             # Estructuras personalizadas
│   │   └── resources/
│   │       ├── application.yml             # Configuración
│   │       └── static/                     # Frontend (HTML/CSS/JS)
│   │           ├── index.html              # Login
│   │           ├── admin/                  # Panel administrador
│   │           ├── operator/               # Panel operador
│   │           ├── visitor/                # Panel visitante
│   │           ├── css/                    # Estilos
│   │           └── js/                     # Scripts
│   └── test/java/                          # Pruebas unitarias
└── README.md
```

## API REST Endpoints

### Autenticación
- `POST /api/auth/login` - Iniciar sesión
- `POST /api/auth/logout` - Cerrar sesión
- `GET /api/auth/validate-token` - Validar token

### Atracciones
- `GET /api/attractions` - Obtener todas
- `GET /api/attractions/{id}` - Obtener por ID
- `GET /api/attractions/zone/{zoneId}` - Por zona
- `PUT /api/attractions/{id}` - Actualizar
- `POST /api/attractions/change-status/{id}` - Cambiar estado

### Colas Virtuales
- `POST /api/queue/add-visitor` - Agregar a fila
- `GET /api/queue/next/{attractionId}` - Siguiente visitante
- `GET /api/queue/position/{attractionId}/{visitorId}` - Posición
- `GET /api/queue/full/{attractionId}` - Fila completa
- `DELETE /api/queue/remove-visitor/{attractionId}/{visitorId}` - Remover

### Visitantes
- `GET /api/visitors/{id}` - Obtener datos
- `GET /api/visitors` - Listado
- `POST /api/visitors/register` - Registrar
- `PUT /api/visitors/{id}/add-favorite` - Agregar favorito
- `PUT /api/visitors/{id}/add-balance` - Agregar saldo

### Reportes
- `POST /api/reports/generate` - Generar reporte
- `GET /api/reports/latest` - Último reporte
- `GET /api/reports` - Todos los reportes

## Pruebas Unitarias

Las pruebas incluyen al menos 4 pruebas unitarias para las estructuras de datos:

```bash
# Ejecutar todas las pruebas
mvn test

# Ejecutar una prueba específica
mvn test -Dtest=GraphTest
```

### Clases de Prueba
- `GraphTest` - Pruebas del Grafo (Dijkstra, BFS)
- `PriorityQueueTest` - Cola de prioridad
- `LinkedListTest` - Listas enlazadas
- `BinarySearchTreeTest` - Árbol binario de búsqueda

## Características Implementadas

### ✅ Obligatorias
- [x] Estructuras de datos personalizadas (Grafo, Cola de Prioridad, Lista Enlazada, ABB, Set)
- [x] Interfaz gráfica funcional e intuitiva
- [x] Lógica en Java (Backend con Spring Boot)
- [x] Frontend en HTML/CSS/JavaScript
- [x] Carga de datos iniciales
- [x] Mapa interactivo del parque
- [x] Sistema de colas inteligentes
- [x] Búsquedas eficientes con árboles
- [x] Mantenimiento automatizado (500 visitantes)
- [x] Alertas por clima
- [x] Repositorio Git con commits descriptivos (24+ commits por integrante)
- [x] Diagramas de clases y estructuras
- [x] Pruebas unitarias (4+)

### 🔄 Validaciones Funcionales
- Control de restricciones (altura, edad)
- Gestión de capacidad máxima
- Sistema de alertas de mantenimiento
- Cierre automático por clima
- Garantía de operadores por zona
- Reportes dinámicos

## Validación de Requisitos

### 1. Estructura de Datos
Todas las estructuras están en `src/main/java/com/techpark/datastructures/`:
- ✅ `Graph.java` - Grafo con Dijkstra y BFS
- ✅ `PriorityQueue.java` - Cola de prioridad
- ✅ `LinkedList.java` - Lista enlazada
- ✅ `BinarySearchTree.java` - ABB
- ✅ `CustomSet.java` - Set personalizado

### 2. Pruebas Unitarias
En `src/test/java/com/techpark/datastructures/`:
- ✅ `GraphTest.java` - 4 casos de prueba
- ✅ `PriorityQueueTest.java` - 4 casos de prueba
- ✅ `LinkedListTest.java` - 4 casos de prueba
- ✅ `BinarySearchTreeTest.java` - 4 casos de prueba

### 3. Interfaces
- ✅ Login funcional
- ✅ Dashboard administrador con reportes
- ✅ Mapa interactivo
- ✅ Panel operador
- ✅ Panel visitante con filas

## Convenciones de Commits

Los commits siguen el formato de Conventional Commits:

```
feat: agregar nueva funcionalidad
fix: corregir bug
refactor: refactorizar código
docs: actualizar documentación
test: agregar o actualizar pruebas
style: cambios de formato
chore: tareas de mantenimiento
```

Ejemplo:
```bash
git commit -m "feat: implementar sistema de colas con prioridad"
git commit -m "test: agregar pruebas unitarias para Grafo"
git commit -m "fix: corregir validación de restricciones de altura"
```

## Troubleshooting

### Error: Puerto 8080 ya está en uso
```bash
# Cambiar puerto en application.yml
server.port=8081
```

### Error de compilación Java
```bash
# Verificar versión de Java
java -version

# Debe ser 17 o superior
```

### Errores de CORS en frontend
Los CORS ya están configurados en `TechParkApplication.java`

### Base de datos H2
La aplicación usa H2 en memoria. Para acceder a la consola:
```
http://localhost:8080/h2-console
```

## Notas de Desarrollo

1. **Base de datos**: Usa H2 en memoria (se limpia al reiniciar)
2. **Autenticación**: Sistema simple en memoria (expandible)
3. **Persistencia**: Para producción, usar PostgreSQL/MySQL
4. **Scalabilidad**: Las estructuras de datos pueden optimizarse

## Licencia

Este es un proyecto académico para la Universidad Distrital Francisco José de Caldas.

## Autores

Proyecto desarrollado como trabajo final de la materia de Estructuras de Datos Avanzadas.

## Contacto y Soporte

Para reportar errores o sugerencias, documentar en el repositorio Git.

---

**Última actualización**: 2024
**Versión**: 1.0.0
