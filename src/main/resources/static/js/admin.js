// Verificar autenticación
if (!localStorage.getItem('token')) {
    window.location.href = '../index.html';
}

const API_BASE_URL = 'http://localhost:8080/api';
let attractions = [];
let zones = [];
let operators = [];

// Inicializar
document.addEventListener('DOMContentLoaded', function() {
    loadUsername();
    initNavigation();
    loadDashboard();
    loadAttractions();
    loadZones();
    loadOperators();
    drawParkMap();
});

// Cargar nombre de usuario
function loadUsername() {
    document.getElementById('username').textContent = localStorage.getItem('username') || 'Administrador';
}

// Inicializar navegación
function initNavigation() {
    const navLinks = document.querySelectorAll('.nav-link');
    navLinks.forEach(link => {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            
            const href = this.getAttribute('href');
            if (href && href.startsWith('#')) {
                // Ocultar todas las secciones
                document.querySelectorAll('.section').forEach(section => {
                    section.classList.remove('active');
                });
                
                // Mostrar la seleccionada
                const sectionId = href.substring(1);
                const section = document.getElementById(sectionId);
                if (section) {
                    section.classList.add('active');
                }
                
                // Actualizar nav activo
                navLinks.forEach(l => l.classList.remove('active'));
                this.classList.add('active');
            }
        });
    });
}

// Cargar Dashboard
async function loadDashboard() {
    try {
        const response = await fetch(`${API_BASE_URL}/reports/latest`, {
            headers: getAuthHeaders()
        });
        
        if (response.ok) {
            const report = await response.json();
            
            document.getElementById('totalVisitors').textContent = report.totalVisitors;
            document.getElementById('dailyRevenue').textContent = '$' + report.dailyRevenue.toFixed(2);
            
            // Contar atracciones activas
            const activeCount = attractions.filter(a => a.status === 'ACTIVA').length;
            document.getElementById('activeAttractions').textContent = activeCount;
            
            // Contar alertas
            const alerts = report.maintenanceAlerts.length + report.weatherClosures.length;
            document.getElementById('alertCount').textContent = alerts;
        }
    } catch (error) {
        console.error('Error loading dashboard:', error);
    }
}

// Cargar Atracciones
async function loadAttractions() {
    try {
        const response = await fetch(`${API_BASE_URL}/attractions`, {
            headers: getAuthHeaders()
        });
        
        if (response.ok) {
            attractions = await response.json();
            renderAttractionsTable();
        }
    } catch (error) {
        console.error('Error loading attractions:', error);
    }
}

// Renderizar tabla de atracciones
function renderAttractionsTable() {
    const tbody = document.getElementById('attractionsTableBody');
    tbody.innerHTML = '';
    
    attractions.forEach(attraction => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${attraction.name}</td>
            <td>${attraction.type}</td>
            <td>${attraction.accumulatedVisitors}</td>
            <td><span class="badge ${getStatusBadge(attraction.status)}">${attraction.status}</span></td>
            <td>${attraction.estimatedWaitTime} min</td>
            <td>
                <button class="btn-secondary" onclick="editAttraction(${attraction.id})">Editar</button>
                <button class="btn-danger" onclick="deleteAttraction(${attraction.id})">Eliminar</button>
            </td>
        `;
        tbody.appendChild(row);
    });
}

// Cargar Zonas
async function loadZones() {
    try {
        const response = await fetch(`${API_BASE_URL}/attractions/zones`, {
            headers: getAuthHeaders()
        });
        
        if (response.ok) {
            zones = await response.json();
            renderZonesGrid();
        }
    } catch (error) {
        console.error('Error loading zones:', error);
    }
}

// Renderizar grid de zonas
function renderZonesGrid() {
    const grid = document.getElementById('zonesGrid');
    grid.innerHTML = '';
    
    zones.forEach(zone => {
        const occupancyPercent = (zone.currentOccupancy / zone.maxCapacity) * 100;
        const card = document.createElement('div');
        card.className = 'zone-card';
        card.innerHTML = `
            <h3>${zone.name}</h3>
            <div class="zone-info">
                <p><strong>Ocupación:</strong> ${zone.currentOccupancy}/${zone.maxCapacity}</p>
                <div class="zone-progress">
                    <div class="zone-progress-bar" style="width: ${occupancyPercent}%"></div>
                </div>
                <p><strong>Operadores:</strong> ${zone.operatorIds ? zone.operatorIds.size : 0}</p>
                <p><strong>Atracciones:</strong> ${zone.attractionIds ? zone.attractionIds.length : 0}</p>
            </div>
            <button class="btn-primary" onclick="editZone(${zone.id})">Editar</button>
        `;
        grid.appendChild(card);
    });
}

// Cargar Operadores
async function loadOperators() {
    try {
        const response = await fetch(`${API_BASE_URL}/visitors`, {
            headers: getAuthHeaders()
        });
        
        if (response.ok) {
            const allUsers = await response.json();
            // En una aplicación real, habría un endpoint específico para operadores
            renderOperatorsTable();
        }
    } catch (error) {
        console.error('Error loading operators:', error);
    }
}

// Renderizar tabla de operadores
function renderOperatorsTable() {
    const tbody = document.getElementById('operatorsTableBody');
    tbody.innerHTML = '';
    
    // Datos de prueba
    const testOperators = [
        { username: 'operator', email: 'operator@techpark.com', zone: 'Aventura', attractions: 3, active: true },
        { username: 'operator2', email: 'operator2@techpark.com', zone: 'Infantil', attractions: 2, active: true }
    ];
    
    testOperators.forEach(op => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${op.username}</td>
            <td>${op.email}</td>
            <td>${op.zone}</td>
            <td>${op.attractions}</td>
            <td><span class="badge badge-success">${op.active ? 'Activo' : 'Inactivo'}</span></td>
        `;
        tbody.appendChild(row);
    });
}

// Generar reporte diario
async function generateDailyReport() {
    try {
        const today = new Date().toISOString().split('T')[0];
        const response = await fetch(`${API_BASE_URL}/reports/generate?date=${today}`, {
            method: 'POST',
            headers: getAuthHeaders()
        });
        
        if (response.ok) {
            const report = await response.json();
            displayReport(report);
        }
    } catch (error) {
        console.error('Error generating report:', error);
    }
}

// Mostrar reporte
function displayReport(report) {
    const container = document.getElementById('reportContainer');
    
    let html = `
        <div class="report-section">
            <h3>Resumen del Día - ${report.reportDate}</h3>
            <div class="report-stats">
                <div class="report-stat">
                    <div class="label">Visitantes Totales</div>
                    <div class="value">${report.totalVisitors}</div>
                </div>
                <div class="report-stat">
                    <div class="label">Ingresos Diarios</div>
                    <div class="value">$${report.dailyRevenue.toFixed(2)}</div>
                </div>
                <div class="report-stat">
                    <div class="label">Capacidad del Parque</div>
                    <div class="value">${report.capacityPercentage}%</div>
                </div>
            </div>
        </div>
    `;
    
    // Atracciones más visitadas
    if (report.mostVisitedAttractions && Object.keys(report.mostVisitedAttractions).length > 0) {
        html += `
            <div class="report-section">
                <h3>Atracciones Más Visitadas</h3>
                <ul>
        `;
        
        for (const [name, count] of Object.entries(report.mostVisitedAttractions)) {
            html += `<li>${name}: ${count} visitantes</li>`;
        }
        
        html += `</ul></div>`;
    }
    
    // Cierres por clima
    if (report.weatherClosures && report.weatherClosures.length > 0) {
        html += `
            <div class="report-section">
                <h3>Cierres por Clima</h3>
                <ul>
        `;
        
        report.weatherClosures.forEach(closure => {
            html += `<li>${closure}</li>`;
        });
        
        html += `</ul></div>`;
    }
    
    // Alertas de mantenimiento
    if (report.maintenanceAlerts && report.maintenanceAlerts.length > 0) {
        html += `
            <div class="report-section">
                <h3>Alertas de Mantenimiento</h3>
                <ul>
        `;
        
        report.maintenanceAlerts.forEach(alert => {
            html += `<li>${alert}</li>`;
        });
        
        html += `</ul></div>`;
    }
    
    container.innerHTML = html;
}

// Alertas de clima
async function triggerWeatherAlert() {
    try {
        const response = await fetch(`${API_BASE_URL}/attractions/close-by-weather`, {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify({ alert: 'TORMENTA_ELECTRICA' })
        });
        
        if (response.ok) {
            alert('Alerta de tormenta activada. Atracciones cerradas.');
            loadAttractions();
            showAlert('Atracciones cerradas por tormenta', 'danger');
        }
    } catch (error) {
        console.error('Error triggering weather alert:', error);
    }
}

// Revisar mantenimiento
async function triggerMaintenanceCheck() {
    try {
        const response = await fetch(`${API_BASE_URL}/attractions/check-maintenance`, {
            method: 'POST',
            headers: getAuthHeaders()
        });
        
        if (response.ok) {
            const needsMaintenance = await response.json();
            if (needsMaintenance.length > 0) {
                showAlert(`${needsMaintenance.length} atracción(es) requieren mantenimiento`, 'warning');
                loadAttracciones();
            } else {
                showAlert('Todas las atracciones están en buen estado', 'success');
            }
        }
    } catch (error) {
        console.error('Error checking maintenance:', error);
    }
}

// Mostrar alerta
function showAlert(message, type = 'warning') {
    const alertsList = document.getElementById('alertsList');
    const alertItem = document.createElement('div');
    alertItem.className = `alert-item ${type}`;
    alertItem.innerHTML = `
        <span>${message}</span>
        <button onclick="this.parentElement.remove()" class="btn-secondary">Cerrar</button>
    `;
    alertsList.insertBefore(alertItem, alertsList.firstChild);
    
    // Auto-remover después de 5 segundos
    setTimeout(() => {
        alertItem.remove();
    }, 5000);
}

// Dibujar mapa del parque
function drawParkMap() {
    const canvas = document.getElementById('parkMap');
    if (!canvas) return;
    
    const ctx = canvas.getContext('2d');
    
    // Limpiar canvas
    ctx.fillStyle = '#f0f9ff';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    
    // Dibujar zonas como círculos
    const zoneLocations = [
        { name: 'Aventura', x: 150, y: 150, color: '#667eea', size: 60 },
        { name: 'Infantil', x: 400, y: 150, color: '#764ba2', size: 50 },
        { name: 'Acuática', x: 650, y: 150, color: '#3b82f6', size: 70 },
        { name: 'Entretenimiento', x: 275, y: 400, color: '#10b981', size: 55 }
    ];
    
    zoneLocations.forEach(zone => {
        // Dibujar círculo
        ctx.fillStyle = zone.color;
        ctx.beginPath();
        ctx.arc(zone.x, zone.y, zone.size, 0, 2 * Math.PI);
        ctx.fill();
        
        // Dibujar texto
        ctx.fillStyle = 'white';
        ctx.font = 'bold 14px Arial';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(zone.name, zone.x, zone.y);
    });
    
    // Dibujar caminos entre zonas
    ctx.strokeStyle = '#bfdbfe';
    ctx.lineWidth = 3;
    
    ctx.beginPath();
    ctx.moveTo(150, 150);
    ctx.lineTo(400, 150);
    ctx.stroke();
    
    ctx.beginPath();
    ctx.moveTo(400, 150);
    ctx.lineTo(650, 150);
    ctx.stroke();
    
    ctx.beginPath();
    ctx.moveTo(150, 150);
    ctx.lineTo(275, 400);
    ctx.stroke();
}

// Funciones de Modal
function showAddAttractionForm() {
    document.getElementById('attractionModal').style.display = 'block';
}

function showAddZoneForm() {
    document.getElementById('zoneModal').style.display = 'block';
}

function closeModal(modalId) {
    document.getElementById(modalId).style.display = 'none';
}

// Guardar atracción
async function saveAttraction(event) {
    event.preventDefault();
    
    const attraction = {
        name: document.getElementById('attractionName').value,
        type: document.getElementById('attractionType').value,
        maxCapacityPerCycle: parseInt(document.getElementById('attractionCapacity').value),
        minHeight: parseFloat(document.getElementById('attractionMinHeight').value),
        minAge: parseInt(document.getElementById('attractionMinAge').value),
        additionalCost: parseFloat(document.getElementById('attractionCost').value),
        zoneId: 1 // Valor por defecto
    };
    
    // En una aplicación real, se haría un POST al servidor
    console.log('Atracción guardada:', attraction);
    
    closeModal('attractionModal');
    showAlert('Atracción creada exitosamente', 'success');
    loadAttractions();
}

// Guardar zona
async function saveZone(event) {
    event.preventDefault();
    
    const zone = {
        name: document.getElementById('zoneName').value,
        maxCapacity: parseInt(document.getElementById('zoneCapacity').value)
    };
    
    console.log('Zona guardada:', zone);
    
    closeModal('zoneModal');
    showAlert('Zona creada exitosamente', 'success');
    loadZones();
}

// Utilidades
function getStatusBadge(status) {
    switch(status) {
        case 'ACTIVA':
            return 'badge-success';
        case 'MANTENIMIENTO':
            return 'badge-warning';
        case 'CERRADA':
            return 'badge-danger';
        default:
            return 'badge-info';
    }
}

function getAuthHeaders() {
    return {
        'Content-Type': 'application/json',
        'Authorization': localStorage.getItem('token') || ''
    };
}

// Cerrar sesión
function logout() {
    localStorage.clear();
    window.location.href = '../index.html';
}

// Cerrar modal al hacer click fuera
window.onclick = function(event) {
    const modals = document.querySelectorAll('.modal');
    modals.forEach(modal => {
        if (event.target === modal) {
            modal.style.display = 'none';
        }
    });
}
