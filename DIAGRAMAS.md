# Diagramas del Proyecto

## Diagrama de Clases del Modelo

```
┌─────────────────┐
│      User       │
├─────────────────┤
│- id: Long       │
│- username: String
│- password: String
│- email: String │
│- role: String  │
│- active: boolean
└────────┬────────┘
         │
    ┌────┴────┬────────┬──────────┐
    │         │        │          │
┌───▼──┐  ┌───▼──┐ ┌──▼──┐  ┌───▼────┐
│Admin │  │Oper  │ │Visi │  │ Ticket │
│       │  │      │ │     │  │        │
└───────┘  └──────┘ └─────┘  └────────┘
```

### Jerarquía de Usuarios

**User (Base)**
- Administrator extends User
  - Gestión de empleados
  - Crear/modificar zonas
  - Consultar reportes

- Operator extends User
  - Control de atracciones
  - Gestión de estado
  - Revisiones técnicas

- Visitor extends User
  - Perfil y saldo virtual
  - Favoritos e historial
  - Fila virtual

### Relaciones Principales

```
┌──────────────────┐
│     Visitor      │
└────────┬─────────┘
         │
         ├─→ Ticket (1:1)
         ├─→ CustomSet<Attraction> (Favoritos)
         ├─→ LinkedList<Attraction> (Historial)
         └─→ Zone (Ubicación actual)

┌──────────────────┐
│    Attraction    │
└────────┬─────────┘
         │
         ├─→ Zone (1:1)
         ├─→ QueueEntry (1:*)
         └─→ PriorityQueue (Cola)

┌──────────────────┐
│      Zone        │
└────────┬─────────┘
         │
         ├─→ LinkedList<Operator>
         └─→ List<Attraction>
```

## Diagrama de Estructuras de Datos Propias

### 1. Grafo (Graph)
```
Grafo del Parque
================

  [Aventura] ────50km──── [Infantil]
      │                        │
    100km                      150km
      │                        │
  [Acuática] ────200km──── [Entretenimiento]

Algoritmos:
- Dijkstra: Camino más corto
- BFS: Búsqueda exploratoria
```

### 2. Cola de Prioridad (PriorityQueue)
```
Estructura de Heap Binario

         Head (Mínima Prioridad)
           │
         [1] ← Fast-Pass (Prioridad 1)
        /   \
      [1]   [2] ← General (Prioridad 2)
     / \    / \
   [2] [3][2] [3]
```

### 3. Lista Enlazada (LinkedList)
```
Historial de Visitas

Head → [Montaña Rusa]
        │
        ├─next→ [Río Salvaje]
                 │
                 ├─next→ [Tierra Sky]
                          │
                          └─next→ null
```

### 4. Árbol Binario de Búsqueda (BinarySearchTree)
```
Catálogo de Atracciones (Ordenado por Nombre)

              Tierra Sky
             /          \
        Río Salvaje    [Vacio]
        /          \
  Montaña Rusa    [Vacio]
```

### 5. Set Personalizado (CustomSet)
```
Atracciones Favoritas

{
  Montaña Rusa,
  Tierra Sky,
  Carrusel Mágico
}

Operaciones:
- Unión (∪)
- Intersección (∩)
- Diferencia (−)
```

## Flujo de Datos

### Flujo de Acceso a Atracción

```
┌─────────────────┐
│ Visitante Click │
└────────┬────────┘
         │
         ▼
┌──────────────────────────┐
│ Validar Restricciones    │
│ - Altura mínima          │
│ - Edad mínima            │
│ - Saldo suficiente       │
└────────┬─────────────────┘
         │
    ┌────▼────┐
    │ Válido? │
    └────┬────┘
    ┌────▼────┐
    NO        SÍ
    │         │
    ▼         ▼
[Error]  [Unirse Cola]
         │
         ▼
    [PriorityQueue]
    ├─ Fast-Pass (Prioridad 1)
    └─ General (Prioridad 2)
         │
         ▼
    [Mostrar Posición]
```

### Flujo de Cierre por Clima

```
┌─────────────────────┐
│ Alerta Climática    │
│ (Tormenta/Lluvia)   │
└────────┬────────────┘
         │
         ▼
┌──────────────────────┐
│ Encontrar Atracciones│
│ - Tipo: Acuática     │
│ - Tipo: Mecánica     │
└────────┬─────────────┘
         │
         ▼
┌──────────────────────┐
│ Cambiar Estado a     │
│ CERRADA              │
└────────┬─────────────┘
         │
         ▼
┌──────────────────────┐
│ Notificar Visitantes │
│ en Cola Virtual      │
└──────────────────────┘
```

## Componentes de la API REST

```
┌──────────────────────────────────────┐
│        Spring Boot Application        │
└──────────────────────────────────────┘
         │
         ├─ AuthController
         │  ├─ POST /auth/login
         │  ├─ POST /auth/logout
         │  └─ GET /auth/validate-token
         │
         ├─ AttractionController
         │  ├─ GET /attractions
         │  ├─ PUT /attractions/{id}
         │  └─ POST /attractions/change-status
         │
         ├─ QueueController
         │  ├─ POST /queue/add-visitor
         │  ├─ GET /queue/full/{attractionId}
         │  └─ DELETE /queue/remove-visitor
         │
         ├─ VisitorController
         │  ├─ GET /visitors/{id}
         │  └─ PUT /visitors/{id}/add-favorite
         │
         └─ ReportController
            ├─ POST /reports/generate
            └─ GET /reports/latest
```

## Arquitectura del Sistema

```
┌─────────────────────────────────────────────────────┐
│           Frontend (HTML/CSS/JavaScript)             │
│  ┌────────────────┬────────────────┬──────────────┐ │
│  │     Login      │   Dashboard    │  Visitor/Op  │ │
│  │                │   (Admin)      │   Panel      │ │
│  └────────────────┴────────────────┴──────────────┘ │
└────────────────────────────────────────────────────┬┘
                                                      │
                        HTTP/JSON
                                                      │
┌────────────────────────────────────────────────────▼┐
│          Backend (Spring Boot - Java)                │
│  ┌─────────────────────────────────────────────┐    │
│  │         Controllers (REST API)               │    │
│  ├─────────────────────────────────────────────┤    │
│  │         Services (Business Logic)            │    │
│  │  - AuthService                              │    │
│  │  - AttractionService                        │    │
│  │  - QueueService                             │    │
│  │  - ReportService                            │    │
│  ├─────────────────────────────────────────────┤    │
│  │      Data Structures (Algoritmos)            │    │
│  │  - Graph<T>                                 │    │
│  │  - PriorityQueue<T>                         │    │
│  │  - LinkedList<T>                            │    │
│  │  - BinarySearchTree<T>                      │    │
│  │  - CustomSet<T>                             │    │
│  ├─────────────────────────────────────────────┤    │
│  │         Data Models (Entities)               │    │
│  │  - User, Visitor, Operator, Admin           │    │
│  │  - Attraction, Zone, Ticket                 │    │
│  │  - QueueEntry, ParkReport                   │    │
│  └─────────────────────────────────────────────┘    │
└────────────────────────────────────────────────────┘
                            │
                            │ (H2 Database)
                            ▼
                    ┌──────────────┐
                    │   In-Memory  │
                    │   Database   │
                    └──────────────┘
```

## Matriz de Responsabilidades

| Componente | Responsabilidad |
|-----------|-----------------|
| Graph | Gestionar rutas y caminos más cortos |
| PriorityQueue | Ordenar visitantes por prioridad |
| LinkedList | Mantener historial cronológico |
| BinarySearchTree | Búsquedas rápidas de atracciones |
| CustomSet | Gestionar favoritos únicos |
| AttractionService | Lógica de atracciones |
| QueueService | Gestión de colas |
| ReportService | Generación de reportes |
| AuthService | Autenticación y autorización |

---

**Notas**: Los diagramas muestran una visión conceptual del sistema. Para más detalles, consultar el código fuente.
