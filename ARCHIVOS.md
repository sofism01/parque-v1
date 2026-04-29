# Estructura y Descripción de Archivos del Proyecto

## Documentación Raíz

### 📄 README.md
- **Descripción**: Guía completa del proyecto
- **Contenido**:
  - Descripción del proyecto
  - Características principales
  - Requisitos e instalación
  - Uso de la aplicación
  - Estructura del proyecto
  - API REST endpoints
  - Validación de requisitos
  - Troubleshooting

### 📄 COMPILACION.md
- **Descripción**: Guía detallada de compilación y ejecución
- **Contenido**:
  - Requisitos previos
  - Pasos de compilación
  - Opciones de ejecución
  - Comandos útiles de Maven
  - Troubleshooting
  - Verificación de instalación

### 📄 DIAGRAMAS.md
- **Descripción**: Diagramas conceptuales del sistema
- **Contenido**:
  - Diagrama de clases
  - Jerarquía de usuarios
  - Estructuras de datos propias
  - Flujos de datos principales
  - Componentes de la API
  - Arquitectura general

### 📄 pom.xml
- **Descripción**: Configuración de Maven
- **Contenido**:
  - Parent: Spring Boot 3.2.0
  - Dependencias principales
  - Plugins de compilación
  - Versión Java: 17

## Estructura Backend (src/main/java/com/techpark)

### 📁 Root

#### TechParkApplication.java
- **Propósito**: Punto de entrada de la aplicación
- **Responsabilidad**: Configurar CORS, inicializar Spring Boot
- **Características**: WebMvcConfigurer bean

### 📁 model/

Clases de entidades del dominio:

#### User.java
- **Herencia**: Base de todos los usuarios
- **Atributos**: id, username, password, email, role, active, createdAt
- **Anotaciones**: Lombok (@Data, @NoArgsConstructor, @AllArgsConstructor)

#### Administrator.java
- **Herencia**: extends User
- **Atributos**: department (Recursos Humanos, Operaciones, etc.)
- **Responsabilidad**: Gestión del parque

#### Operator.java
- **Herencia**: extends User
- **Atributos**: zoneId, assignedAttractionsIds (LinkedList)
- **Métodos**: addAttraction, removeAttraction, managesAttraction

#### Visitor.java
- **Herencia**: extends User
- **Atributos**: document, age, height, virtualBalance, ticketType, favoriteAttractions (CustomSet), visitHistory (LinkedList)
- **Métodos**: addFavorite, removeFavorite, addVisit, canAccessAttraction, payForAttraction

#### Attraction.java
- **Interfaces**: implements Comparable<Attraction>
- **Atributos**: id, name, type, maxCapacityPerCycle, minHeight, minAge, additionalCost, accumulatedVisitors, estimatedWaitTime, status, closureReason, zoneId
- **Métodos**: needsMaintenance(), changeStatus(), isAvailable()

#### Zone.java
- **Atributos**: id, name, maxCapacity, currentOccupancy, operatorIds (LinkedList), attractionIds (List)
- **Métodos**: addOperator, removeOperator, addAttraction, hasOperators, canAcceptMoreVisitors

#### Ticket.java
- **Tipos**: GENERAL, FAMILIAR, FAST_PASS
- **Atributos**: id, type, price, description, maxCapacity, currentCount, active
- **Métodos**: isAvailable, incrementCount

#### QueueEntry.java
- **Interfaces**: implements Comparable<QueueEntry>
- **Atributos**: visitorId, visitorName, ticketType, priority, positionInQueue, entryTime
- **Responsabilidad**: Entrada individual en cola

#### ParkReport.java
- **Atributos**: id, reportDate, dailyRevenue, totalVisitors, mostVisitedAttractions, averageWaitTimes, weatherClosures, maintenanceAlerts, operativeIncidents, capacityPercentage
- **Responsabilidad**: Estadísticas diarias del parque

### 📁 datastructures/

Estructuras de datos personalizadas:

#### Graph.java
- **Propósito**: Representar mapa del parque
- **Algoritmos**: Dijkstra (camino más corto), BFS (búsqueda exploratoria)
- **Métodos**: addNode, addEdge, dijkstra, bfs
- **Clase Interna**: Edge (destino, peso)
- **Complejidad**: O(log n) para Dijkstra

#### PriorityQueue.java
- **Propósito**: Gestionar colas con prioridad
- **Implementación**: Min-heap binario
- **Métodos**: enqueue, dequeue, peek, siftUp, siftDown, isEmpty, size
- **Prioridad**: 1 = Fast-Pass, 2 = General

#### LinkedList.java
- **Propósito**: Almacenar historial cronológico
- **Métodos**: add, addFirst, addAt, remove, removeFirst, toList, iterator
- **Complejidad**: O(n) acceso, O(1) inserción al inicio
- **Caso de Uso**: Historial de visitas, operadores por zona

#### BinarySearchTree.java
- **Propósito**: Búsquedas rápidas de atracciones
- **Métodos**: insert, search, delete, inOrder, preOrder, postOrder
- **Complejidad**: O(log n) promedio
- **Clase Interna**: Node (valor, izq, der)

#### CustomSet.java
- **Propósito**: Gestionar atracciones favoritas
- **Backing**: ArrayList
- **Métodos**: add, remove, contains, union, intersection, difference, size, isEmpty
- **Operaciones**: Set standard + operaciones de conjunto

### 📁 service/

Servicios con lógica de negocio:

#### AuthService.java
- **Responsabilidad**: Autenticación y autorización
- **Métodos**:
  - authenticate(username, password)
  - generateToken(user)
  - validateToken(token)
  - registerVisitor(visitor)
  - getAllOperators/getAllVisitors
- **Almacenamiento**: En memoria con HashMap
- **Usuarios Iniciales**: admin/admin123, operator/operator123, visitor/visitor123

#### AttractionService.java
- **Responsabilidad**: Gestión de atracciones y zonas
- **Estructuras Internas**: 
  - BinarySearchTree para búsquedas
  - Graph para rutas
  - Map<id, Attraction> para acceso rápido
- **Métodos**:
  - addAttraction/getAttractionById/searchAttractionByName
  - getAttractionsByZone
  - findShortestPath (usa Graph.bfs)
  - checkMaintenanceRequirements
  - closeAttractionsByWeather
- **Datos Iniciales**: 4 atracciones, 3 zonas

#### QueueService.java
- **Responsabilidad**: Gestión de filas virtuales
- **Estructura Interna**: Map<attractionId, PriorityQueue<QueueEntry>>
- **Métodos**:
  - addVisitorToQueue (respeta prioridades)
  - getNextInQueue
  - getQueuePosition
  - getFullQueue
  - estimateWaitTime
- **Prioridad**: Fast-Pass > General

#### ReportService.java
- **Responsabilidad**: Generación de reportes
- **Métodos**:
  - generateDailyReport(date)
  - calculateRevenue/calculateVisitors
  - getMostVisitedAttractions (top 5)
  - getAverageWaitTimes
  - getWeatherClosures/getMaintenanceAlerts
  - calculateCapacityPercentage
- **Agregación**: De múltiples servicios

### 📁 controller/

Controladores REST API:

#### AuthController.java
- **Endpoints**:
  - POST /api/auth/login
  - POST /api/auth/logout
  - GET /api/auth/validate-token
- **DTOs**: LoginResponse con token, role, userId, username

#### AttractionController.java
- **Endpoints**: 12 rutas
  - GET /api/attractions (todas)
  - GET /api/attractions/{id}
  - GET /api/attractions/search?name=
  - GET /api/attractions/zone/{zoneId}
  - PUT /api/attractions/{id}
  - POST /api/attractions/change-status/{id}
  - GET /api/attractions/zones (todas)
  - POST /api/attractions/zones
  - POST /api/attractions/check-maintenance
  - POST /api/attractions/close-by-weather

#### QueueController.java
- **Endpoints**: 7 rutas
  - POST /api/queue/add-visitor
  - GET /api/queue/next/{attractionId}
  - GET /api/queue/position/{attractionId}/{visitorId}
  - GET /api/queue/size/{attractionId}
  - GET /api/queue/full/{attractionId}
  - DELETE /api/queue/remove-visitor
  - POST /api/queue/estimate-wait-time
  - GET /api/queue/stats

#### VisitorController.java
- **Endpoints**: 6 rutas
  - GET /api/visitors/{id}
  - GET /api/visitors
  - POST /api/visitors/register
  - PUT /api/visitors/{id}/add-favorite
  - PUT /api/visitors/{id}/remove-favorite
  - PUT /api/visitors/{id}/add-balance
  - GET /api/visitors/{id}/history

#### ReportController.java
- **Endpoints**: 4 rutas
  - POST /api/reports/generate?date=
  - GET /api/reports
  - GET /api/reports/by-date?date=
  - GET /api/reports/latest

### 📁 configuration/

Configuración de la aplicación:

#### application.yml
- **H2 Database**:
  - URL: jdbc:h2:mem:testdb
  - Console: /h2-console
- **Spring Settings**:
  - Logging levels
  - MVC settings
- **Servidor**: puerto 8080

## Estructura Frontend (src/main/resources/static)

### 📄 index.html
- **Propósito**: Login principal
- **Elementos**:
  - Formulario de autenticación
  - Sección de demostración de credenciales
  - Estilos integrados

### 📁 css/

#### style.css (Global)
- **Contenido**:
  - Estilos base: botones, tablas, modales
  - Formularios y inputs
  - Badges y etiquetas
  - Animaciones
  - Scrollbars personalizadas

#### login.css
- **Contenido**:
  - Gradiente de fondo (púrpura)
  - Layout centrado con card
  - Estilos de formulario
  - Estados: focus, hover, disabled

#### admin.css
- **Contenido**:
  - Sidebar fijo (250px)
  - Main content flexible
  - Stat cards
  - Tabla de atracciones
  - Canvas para mapa
  - Grid de zonas
  - Estilos de reportes

#### visitor.css
- **Contenido**:
  - Navbar con perfil/balance
  - Tabs con indicador activo
  - Tarjetas de atracciones
  - Grid responsive
  - Modales
  - Canvas para mapa

#### operator.css
- **Contenido**:
  - Header con gradient
  - Sidebar de navegación
  - Paneles de atracciones
  - Formularios
  - Colas con entradas
  - Badges de estado

### 📁 js/

#### login.js
- **Funciones**:
  - handleLogin(e) - POST /api/auth/login
  - checkAuth() - Validar token
  - getAuthHeaders() - Headers con autorización
  - logout() - Limpiar sesión
- **Almacenamiento**: localStorage para token y username

#### admin.js
- **Funciones**:
  - loadDashboard() - Cargar estadísticas
  - loadAttractions/loadZones/loadOperators()
  - generateDailyReport()
  - triggerWeatherAlert/triggerMaintenanceCheck()
  - drawParkMap() - Canvas
  - Modales para crear atracciones
- **Tabs**: 7 secciones principales
- **Interactividad**: Click handlers para tablas

#### visitor.js
- **Funciones**:
  - loadVisitorInfo() - Perfil y balance
  - loadAttractions/loadFavorites()
  - showAttractionModal()
  - joinQueue()
  - toggleFavorite()
  - loadQueueInfo()
  - switchTab()
  - drawVisitorMap()
- **Tabs**: 5 secciones
- **Modales**: Detalles de atracción

#### operator.js
- **Funciones**:
  - loadOperatorInfo() - Nombre y zona
  - loadAttractions()
  - populateAttractionSelects()
  - initNavigation()
  - loadQueueForAttraction()
  - displayQueue()
  - admitNextVisitor()
  - removeFromQueue()
  - openRevisionForm()
  - submitRevision()
  - changeAttractionStatus()
- **Secciones**: 5 principales (zona, fila, revisión, estado, alertas)

### 📁 admin/
- admin.html, admin.css (en css/), admin.js (en js/)

### 📁 visitor/
- visitor.html, visitor.css (en css/), visitor.js (en js/)

### 📁 operator/
- operator.html, operator.css (en css/), operator.js (en js/)

## Estructura de Pruebas (src/test/java/com/techpark/datastructures)

### GraphTest.java
- **Pruebas**: 4 casos
  1. testAddNode - Verificar adición de nodos
  2. testAddEdge - Verificar adición de aristas
  3. testBFS - Búsqueda en amplitud
  4. testDijkstra - Camino más corto

### PriorityQueueTest.java
- **Pruebas**: 4 casos
  1. testEnqueueDequeue
  2. testPeek
  3. testIsEmpty
  4. testSize

### LinkedListTest.java
- **Pruebas**: 4 casos
  1. testAdd
  2. testAddFirst
  3. testRemove
  4. testClear

### BinarySearchTreeTest.java
- **Pruebas**: 4 casos
  1. testInsertSearch
  2. testInOrder
  3. testDelete
  4. testHeight

## Resumen Estadístico del Proyecto

| Aspecto | Cantidad |
|---------|----------|
| Clases de Modelo | 9 |
| Estructuras de Datos | 5 |
| Servicios | 4 |
| Controladores | 5 |
| Endpoints REST | 40+ |
| Archivos Frontend | 4 HTML, 5 CSS, 4 JS |
| Pruebas Unitarias | 4 clases × 4 = 16 pruebas |
| Líneas de Código (Backend) | ~3000+ |
| Líneas de Código (Frontend) | ~2500+ |

---

**Última actualización**: 2024
