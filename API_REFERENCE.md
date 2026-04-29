# API REST - Referencia Rápida

## URL Base
```
http://localhost:8080/api
```

## 🔐 Autenticación

### Login
```http
POST /auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}

Response 200 OK:
{
  "success": true,
  "token": "uuid-token-string",
  "role": "ADMIN",
  "userId": 1,
  "username": "admin"
}
```

### Validar Token
```http
GET /auth/validate-token
Authorization: <token>

Response: Boolean
```

### Logout
```http
POST /auth/logout
Authorization: <token>

Response 200 OK
```

## 🎢 Atracciones

### Obtener Todas
```http
GET /attractions

Response 200 OK:
[
  {
    "id": 1,
    "name": "Montaña Rusa",
    "type": "MECANICA",
    "maxCapacityPerCycle": 30,
    "minHeight": 140,
    "minAge": 12,
    "additionalCost": 5.00,
    "accumulatedVisitors": 250,
    "estimatedWaitTime": 15,
    "status": "ACTIVA",
    "zoneId": 1
  },
  ...
]
```

### Obtener por ID
```http
GET /attractions/{id}

Response 200 OK:
{ attraction object }
```

### Buscar por Nombre
```http
GET /attractions/search?name=Montaña

Response 200 OK:
[ matching attractions ]
```

### Obtener por Zona
```http
GET /attractions/zone/{zoneId}

Response 200 OK:
[ attractions in zone ]
```

### Actualizar Atracción
```http
PUT /attractions/{id}
Content-Type: application/json

{
  "estimatedWaitTime": 20,
  "accumulatedVisitors": 250
}

Response 200 OK:
{ updated attraction }
```

### Cambiar Estado
```http
POST /attractions/change-status/{id}
Content-Type: application/json

{
  "status": "MANTENIMIENTO",
  "reason": "Revisión técnica programada"
}

Response 200 OK
```

### Obtener Todas las Zonas
```http
GET /attractions/zones

Response 200 OK:
[
  {
    "id": 1,
    "name": "Zona Aventura",
    "maxCapacity": 500,
    "currentOccupancy": 250,
    "attractionIds": [1, 2],
    "operatorIds": [2]
  },
  ...
]
```

### Crear Nueva Zona
```http
POST /attractions/zones
Content-Type: application/json

{
  "name": "Zona Nueva",
  "maxCapacity": 400
}

Response 201 CREATED:
{ new zone }
```

### Obtener Zona por ID
```http
GET /attractions/zones/{id}

Response 200 OK:
{ zone object }
```

### Verificar Mantenimiento Requerido
```http
POST /attractions/check-maintenance

Response 200 OK:
[ attractions needing maintenance ]
```

### Cerrar por Clima
```http
POST /attractions/close-by-weather
Content-Type: application/json

{
  "weather": "TORMENTA"
}

Response 200 OK
```

## 📍 Colas Virtuales

### Agregar Visitante a Fila
```http
POST /queue/add-visitor
Content-Type: application/json

{
  "visitorId": 3,
  "visitorName": "Juan Pérez",
  "attractionId": 1,
  "ticketType": "FAST_PASS"  // o "GENERAL"
}

Response 200 OK:
{
  "positionInQueue": 1,
  "estimatedWaitTime": 15
}
```

### Obtener Siguiente Visitante
```http
GET /queue/next/{attractionId}

Response 200 OK:
{
  "visitorId": 3,
  "visitorName": "Juan Pérez",
  "ticketType": "FAST_PASS"
}
```

### Obtener Posición en Cola
```http
GET /queue/position/{attractionId}/{visitorId}

Response 200 OK:
{
  "position": 5,
  "estimatedWaitTime": 45
}
```

### Obtener Tamaño de Cola
```http
GET /queue/size/{attractionId}

Response 200 OK:
{
  "size": 12,
  "estimatedWaitTime": 45
}
```

### Obtener Cola Completa
```http
GET /queue/full/{attractionId}

Response 200 OK:
[
  {
    "visitorId": 2,
    "visitorName": "María López",
    "ticketType": "FAST_PASS",
    "priority": 1,
    "positionInQueue": 1,
    "entryTime": "2024-04-28T10:15:00"
  },
  {
    "visitorId": 3,
    "visitorName": "Juan Pérez",
    "ticketType": "GENERAL",
    "priority": 2,
    "positionInQueue": 2,
    "entryTime": "2024-04-28T10:16:00"
  },
  ...
]
```

### Remover de Cola
```http
DELETE /queue/remove-visitor/{attractionId}/{visitorId}

Response 200 OK
```

### Estimar Tiempo de Espera
```http
POST /queue/estimate-wait-time/{attractionId}

Response 200 OK:
{
  "waitTime": 45,
  "queueSize": 12,
  "capacityPerCycle": 30
}
```

### Obtener Estadísticas de Cola
```http
GET /queue/stats

Response 200 OK:
{
  "totalPeopleInQueues": 45,
  "averageWaitTime": 30,
  "busyestAttraction": "Montaña Rusa"
}
```

## 👤 Visitantes

### Obtener Visitante por ID
```http
GET /visitors/{id}

Response 200 OK:
{
  "id": 3,
  "username": "visitor",
  "email": "visitor@park.com",
  "document": "12345678",
  "age": 25,
  "height": 175,
  "virtualBalance": 500.00,
  "ticketType": "GENERAL",
  "favoriteAttractions": [1, 2],
  "positionInQueue": 5
}
```

### Obtener Todos los Visitantes
```http
GET /visitors

Response 200 OK:
[ visitors array ]
```

### Registrar Nuevo Visitante
```http
POST /visitors/register
Content-Type: application/json

{
  "username": "newvisitor",
  "password": "password123",
  "email": "new@email.com",
  "document": "87654321",
  "age": 20,
  "height": 170,
  "ticketType": "GENERAL"
}

Response 201 CREATED:
{ new visitor object }
```

### Agregar a Favoritos
```http
PUT /visitors/{id}/add-favorite
Content-Type: application/json

{
  "attractionId": 1
}

Response 200 OK
```

### Remover de Favoritos
```http
PUT /visitors/{id}/remove-favorite
Content-Type: application/json

{
  "attractionId": 1
}

Response 200 OK
```

### Agregar Saldo Virtual
```http
PUT /visitors/{id}/add-balance
Content-Type: application/json

{
  "amount": 50.00
}

Response 200 OK:
{
  "newBalance": 550.00
}
```

### Obtener Historial
```http
GET /visitors/{id}/history

Response 200 OK:
[
  {
    "attractionId": 1,
    "attractionName": "Montaña Rusa",
    "visitTime": "2024-04-28T10:30:00"
  },
  ...
]
```

## 📊 Reportes

### Generar Reporte
```http
POST /reports/generate?date=2024-04-28

Response 201 CREATED:
{
  "id": 1,
  "reportDate": "2024-04-28",
  "dailyRevenue": 2500.00,
  "totalVisitors": 450,
  "mostVisitedAttractions": {
    "Montaña Rusa": 150,
    "Río Salvaje": 120
  },
  "averageWaitTimes": {
    "Montaña Rusa": 25,
    "Río Salvaje": 20
  },
  "weatherClosures": ["Tormenta"],
  "maintenanceAlerts": ["Tierra Sky"],
  "capacityPercentage": 75.5
}
```

### Obtener Todos los Reportes
```http
GET /reports

Response 200 OK:
[ reports array ]
```

### Obtener Reporte por Fecha
```http
GET /reports/by-date?date=2024-04-28

Response 200 OK:
[ reports for that date ]
```

### Obtener Último Reporte
```http
GET /reports/latest

Response 200 OK:
{ latest report }
```

## Headers Requeridos

Todos los endpoints (excepto /auth/login) requieren:

```http
Authorization: <token-recibido-del-login>
Content-Type: application/json
```

Ejemplo:
```javascript
fetch('http://localhost:8080/api/attractions', {
  headers: {
    'Authorization': 'uuid-token-12345-uuid',
    'Content-Type': 'application/json'
  }
})
```

## Códigos de Respuesta

| Código | Significado |
|--------|------------|
| 200 | OK - Operación exitosa |
| 201 | Created - Recurso creado |
| 400 | Bad Request - Datos inválidos |
| 401 | Unauthorized - Token requerido o inválido |
| 403 | Forbidden - Acceso denegado |
| 404 | Not Found - Recurso no existe |
| 500 | Server Error - Error interno |

## Ejemplos con cURL

### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### Obtener Atracciones
```bash
curl -X GET http://localhost:8080/api/attractions \
  -H "Authorization: <token>"
```

### Agregar a Fila
```bash
curl -X POST http://localhost:8080/api/queue/add-visitor \
  -H "Authorization: <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "visitorId": 3,
    "visitorName": "Juan",
    "attractionId": 1,
    "ticketType": "GENERAL"
  }'
```

## Valores Permitidos

### Tipos de Atracción
- MECANICA
- ACUATICA
- INFANTIL

### Estados de Atracción
- ACTIVA
- MANTENIMIENTO
- CERRADA

### Tipos de Tickets
- GENERAL
- FAMILIAR
- FAST_PASS

### Departamentos (Admin)
- Recursos Humanos
- Operaciones
- Técnico
- Mantenimiento

---

**Última actualización**: 2024
**Versión API**: 1.0.0
