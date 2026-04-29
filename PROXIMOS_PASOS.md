# Próximos Pasos - Guía de Uso Inmediato

## 🚀 Inicio Rápido (5 minutos)

### Paso 1: Verificar Requisitos
```bash
# Abrir terminal/cmd en la carpeta del proyecto
cd ProyectoFinalETD

# Verificar Java
java -version
# Debe mostrar version 17+

# Verificar Maven
mvn --version
# Debe mostrar version 3.8+
```

### Paso 2: Compilar el Proyecto
```bash
# Limpiar y compilar
mvn clean install

# Esto descargará ~500MB de dependencias en primer intento
# Tiempo estimado: 3-5 minutos
```

### Paso 3: Ejecutar la Aplicación
```bash
# Iniciar el servidor
mvn spring-boot:run

# O en Windows PowerShell:
mvn spring-boot:run -DskipTests

# Esperar hasta ver:
# "Tomcat started on port(s): 8080"
```

### Paso 4: Acceder a la Aplicación
```
1. Abre navegador web
2. Ve a: http://localhost:8080
3. Verás la pantalla de login

Usa credenciales:
   Usuario: admin
   Contraseña: admin123
```

## 📋 Acciones Inmediatas Después del Login

### Como Administrador (admin/admin123)

#### En el Dashboard:
1. **Ver Estadísticas**
   - Total de visitantes
   - Ingresos del día
   - Atracciones activas
   - Alertas pendientes

2. **Gestionar Atracciones**
   - Ver lista de todas las atracciones
   - Cambiar estado
   - Ver tiempo de espera

3. **Ver Zonas**
   - Ocupación de cada zona
   - Operadores asignados
   - Capacidad disponible

4. **Visualizar Mapa**
   - Ver zonas como círculos
   - Ver conexiones entre zonas
   - Interactivo (click para seleccionar)

5. **Generar Reportes**
   - Click en "Generar Reporte"
   - Ver atracciones más visitadas
   - Ingresos calculados
   - Alertas registradas

### Como Operador (operator/operator123)

#### En Mi Zona:
1. **Ver Atracciones Asignadas**
   - Montaña Rusa (Zona Aventura)
   - Río Salvaje (Zona Aventura)
   - Estado actual de cada una

2. **Gestionar Fila Virtual**
   - Seleccionar atracción
   - Ver visitantes en cola
   - Botón "Admitir Siguiente"
   - Remover visitantes si es necesario

3. **Registrar Revisión Técnica**
   - Seleccionar atracción
   - Marcar como Satisfactoria/Insatisfactoria
   - Agregar comentarios
   - Historial de revisiones

4. **Cambiar Estado de Atracción**
   - Activa → Mantenimiento → Cerrada
   - Agregar motivo del cambio
   - Ver histórico de cambios

### Como Visitante (visitor/visitor123)

#### En la Interfaz:
1. **Explorar Atracciones**
   - Ver todas las atracciones disponibles
   - Información: tipo, tiempo espera, restricciones
   - Saldo virtual: $500

2. **Unirse a Cola**
   - Click en botón "Unirse a Fila"
   - Aparecerá en cola virtual
   - Ver posición en fila

3. **Agregar Favoritos**
   - Click en corazón en tarjeta
   - Ver tab "Favoritos"

4. **Ver Historial**
   - Tab "Historial"
   - Atracciones visitadas

5. **Mapa Interactivo**
   - Ver ubicación de zonas
   - Ver atracción seleccionada
   - Click para más detalles

## 🧪 Pruebas Unitarias

### Ejecutar todas las pruebas:
```bash
# Mientras el servidor está corriendo en otra terminal
mvn test
```

### Ejecutar prueba específica:
```bash
mvn test -Dtest=GraphTest
mvn test -Dtest=PriorityQueueTest
mvn test -Dtest=LinkedListTest
mvn test -Dtest=BinarySearchTreeTest
```

**Resultado esperado**: Todos los tests deben pasar ✅

## 📊 Datos de Prueba Iniciales

### Atracciones (4 totales)
1. **Montaña Rusa** - Mecánica, Zona Aventura, Alto riesgo
2. **Río Salvaje** - Acuática, Zona Aventura, Apta para familias
3. **Tierra Sky** - Mecánica, Zona Entretenimiento, Baja turbulencia
4. **Carrusel Mágico** - Infantil, Zona Infantil, Para niños

### Zonas (3 totales)
1. **Zona Aventura** - Atracciones emocionantes
2. **Zona Infantil** - Atracciones para niños
3. **Zona Entretenimiento** - Atracciones variadas

### Usuarios (3 totales)
1. **admin** - Administrador
2. **operator** - Operador de Zona Aventura
3. **visitor** - Visitante con $500 de saldo

## 🔍 Cosas Que Puedes Probar Ahora

### Flujo de Visitante Completo:
```
1. Login como visitor
2. Ver todas las atracciones
3. Click en "Montaña Rusa"
4. Ver detalles y restricciones
5. Click "Unirse a Fila"
6. Ver posición en fila
7. Agregar a favoritos
8. Ver en tab de Favoritos
9. Consultar Historial
10. Ver Mapa
```

### Flujo de Operador:
```
1. Login como operator
2. Ver mis atracciones en dashboard
3. Ir a "Fila Virtual"
4. Seleccionar una atracción
5. Ver visitantes en cola
6. Click "Admitir Siguiente"
7. Ir a "Revisión Técnica"
8. Registrar revisión
9. Ir a "Cambiar Estado"
10. Cambiar estado a Mantenimiento
```

### Flujo de Administrador:
```
1. Login como admin
2. Ver Dashboard (estadísticas)
3. Ir a Atracciones
4. Ver todas las atracciones
5. Ir a Zonas
6. Ver ocupación
7. Ir a Operadores
8. Ver asignaciones
9. Ir a Mapa
10. Visualizar parque
11. Ir a Reportes
12. Generar reporte del día
```

## 📚 Documentación Complementaria

### Archivos de Referencia:
- `README.md` - Descripción general del proyecto
- `COMPILACION.md` - Instrucciones detalladas de compilación
- `DIAGRAMAS.md` - Diagramas de arquitectura y estructuras
- `ARCHIVOS.md` - Descripción de cada archivo del proyecto

### Carpetas Importantes:
- `src/main/java/com/techpark/` - Código Java (Backend)
- `src/main/resources/static/` - HTML/CSS/JS (Frontend)
- `src/test/java/com/techpark/` - Pruebas unitarias

## 🐛 Si Algo No Funciona

### Error: Puerto 8080 en uso
```bash
# Solución: Cambiar puerto
# Editar src/main/resources/application.yml
# Agregar: server.port=8081
mvn spring-boot:run
```

### Error: Compilación fallida
```bash
# Solución: Limpiar cache de Maven
mvn clean
rm -rf ~/.m2/repository
mvn install
```

### CORS Error en consola del navegador
```bash
# No es un error real, está configurado en:
# src/main/java/com/techpark/TechParkApplication.java
# Reload de página (F5) generalmente lo resuelve
```

## 📞 Estructura de Directorios para Referencia Rápida

```
ProyectoFinalETD/
├── README.md                    ← Lee primero
├── COMPILACION.md               ← Si tienes problemas de compilación
├── DIAGRAMAS.md                 ← Ver arquitectura
├── ARCHIVOS.md                  ← Descripción de cada archivo
├── pom.xml                       ← Configuración Maven
├── src/
│   ├── main/
│   │   ├── java/com/techpark/
│   │   │   ├── datastructures/  ← Estructuras: Graph, PriorityQueue, etc
│   │   │   ├── model/           ← Entidades: User, Visitor, Attraction, etc
│   │   │   ├── service/         ← Lógica: AuthService, QueueService, etc
│   │   │   └── controller/      ← API: @RestController endpoints
│   │   └── resources/static/    ← Frontend (index.html, CSS, JS)
│   └── test/
│       └── java/.../datastructures/  ← Pruebas unitarias
└── target/                      ← Archivos compilados (autogenerado)
```

## ⏰ Estimado de Tiempo

| Actividad | Tiempo |
|-----------|--------|
| Verificar requisitos | 2 min |
| Compilar proyecto | 5 min |
| Iniciar servidor | 2 min |
| Login y exploración | 5 min |
| Probar un flujo completo | 10 min |
| **Total** | **24 min** |

## ✅ Checklist Final

Antes de considerar el proyecto completo:

- [ ] Compilación sin errores: `mvn clean install`
- [ ] Servidor iniciado: `mvn spring-boot:run`
- [ ] Login funciona: http://localhost:8080
- [ ] Pruebas pasan: `mvn test`
- [ ] Admin panel accesible
- [ ] Operador panel accesible
- [ ] Visitante panel accesible
- [ ] Atracciones se cargan correctamente
- [ ] Colas virtuales funcionan
- [ ] Reportes se generan
- [ ] Mapa interactivo aparece

## 🎓 Aprendizajes Clave

Este proyecto demuestra:

1. **Estructuras de Datos**
   - Grafo con algoritmos
   - Cola de prioridad
   - Árbol binario
   - Listas enlazadas
   - Sets personalizados

2. **Arquitectura Backend**
   - Patrón MVC
   - REST API
   - Servicios y lógica de negocio
   - Spring Boot

3. **Frontend Moderno**
   - HTML5 semántico
   - CSS3 responsivo
   - JavaScript vanilla (ES6+)
   - Integración con API

4. **Buenas Prácticas**
   - Pruebas unitarias
   - Documentación
   - Manejo de errores
   - Validaciones

---

**¡Ahora está listo para usar! 🎉**

Inicia con el Paso 1 de "Inicio Rápido" y sigue adelante.

Última actualización: 2024
