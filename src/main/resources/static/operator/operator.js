// Verificar autenticación
if (!localStorage.getItem('token')) {
    window.location.href = '../index.html';
}

const API_BASE_URL = 'http://localhost:8080/api';
let attractions = [];
let currentOperator = null;
let selectedAttractionId = null;

// Inicializar
document.addEventListener('DOMContentLoaded', function() {
    loadOperatorInfo();
    loadAttractions();
    initNavigation();
});

// Cargar información del operador
async function loadOperatorInfo() {
    const username = localStorage.getItem('username');
    document.getElementById('operatorName').textContent = username;
    document.getElementById('zoneInfo').textContent = 'Zona: Aventura'; // En una app real se traería del servidor
}

// Cargar atracciones
async function loadAttractions() {
    try {
        const response = await fetch(`${API_BASE_URL}/attractions`, {
            headers: getAuthHeaders()
        });
        
        if (response.ok) {
            attractions = await response.json();
            renderAttractionsDashboard();
            populateAttractionSelects();
        }
    } catch (error) {
        console.error('Error loading attractions:', error);
    }
}

// Renderizar dashboard de atracciones
function renderAttractionsDashboard() {
    const dashboard = document.getElementById('attractionsDashboard');
    dashboard.innerHTML = '';
    
    // Mostrar solo las atracciones de la zona del operador (zona 1 en este caso)
    const zoneAttractions = attractions.filter(a => a.zoneId === 1);
    
    zoneAttractions.forEach(attraction => {
        const panel = document.createElement('div');
        panel.className = 'attraction-panel';
        
        const statusClass = attraction.status === 'ACTIVA' ? 'badge-success' : 
                          attraction.status === 'MANTENIMIENTO' ? 'badge-warning' : 'badge-danger';
        
        panel.innerHTML = `
            <div class="attraction-panel-header">
                <h3>${attraction.name}</h3>
            </div>
            <div class="attraction-panel-body">
                <div class="panel-stat">
                    <span class="panel-stat-label">Estado:</span>
                    <span class="panel-stat-value badge ${statusClass}">${attraction.status}</span>
                </div>
                <div class="panel-stat">
                    <span class="panel-stat-label">Visitantes hoy:</span>
                    <span class="panel-stat-value">${attraction.accumulatedVisitors}</span>
                </div>
                <div class="panel-stat">
                    <span class="panel-stat-label">Capacidad/ciclo:</span>
                    <span class="panel-stat-value">${attraction.maxCapacityPerCycle}</span>
                </div>
                <div class="panel-stat">
                    <span class="panel-stat-label">Tiempo espera:</span>
                    <span class="panel-stat-value">${attraction.estimatedWaitTime} min</span>
                </div>
                <div class="attraction-actions">
                    <button class="btn-primary" onclick="showQueueForAttraction(${attraction.id})">Ver Fila</button>
                    <button class="btn-secondary" onclick="openRevisionForm(${attraction.id})">Revisar</button>
                </div>
            </div>
        `;
        
        dashboard.appendChild(panel);
    });
}

// Rellenar selectores de atracciones
function populateAttractionSelects() {
    const zoneAttractions = attractions.filter(a => a.zoneId === 1);
    
    [
        'attractionSelect',
        'attractionRevision',
        'attractionStatus'
    ].forEach(selectId => {
        const select = document.getElementById(selectId);
        select.innerHTML = '<option value="">-- Selecciona una atracción --</option>';
        
        zoneAttractions.forEach(attraction => {
            const option = document.createElement('option');
            option.value = attraction.id;
            option.textContent = attraction.name;
            select.appendChild(option);
        });
    });
}

// Inicializar navegación
function initNavigation() {
    const navLinks = document.querySelectorAll('.op-nav-link');
    navLinks.forEach(link => {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            
            const href = this.getAttribute('href');
            if (href && href.startsWith('#')) {
                // Ocultar todas las secciones
                document.querySelectorAll('.op-section').forEach(section => {
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

// Mostrar fila de una atracción
function showQueueForAttraction(attractionId) {
    selectedAttractionId = attractionId;
    
    // Cambiar a tab de cola
    document.querySelectorAll('.op-section').forEach(s => s.classList.remove('active'));
    document.getElementById('cola').classList.add('active');
    
    document.querySelectorAll('.op-nav-link').forEach(l => l.classList.remove('active'));
    document.querySelector('a[href="#cola"]').classList.add('active');
    
    // Cargar la fila
    loadQueueForAttraction();
}

// Cargar fila de una atracción
async function loadQueueForAttraction() {
    const attractionId = document.getElementById('attractionSelect').value || selectedAttractionId;
    
    if (!attractionId) {
        document.getElementById('queueDisplay').innerHTML = '<p>Selecciona una atracción.</p>';
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE_URL}/queue/full/${attractionId}`, {
            headers: getAuthHeaders()
        });
        
        if (response.ok) {
            const queue = await response.json();
            displayQueue(queue);
        }
    } catch (error) {
        console.error('Error loading queue:', error);
        document.getElementById('queueDisplay').innerHTML = '<p>Error al cargar la fila.</p>';
    }
}

// Mostrar cola en la pantalla
function displayQueue(queue) {
    const display = document.getElementById('queueDisplay');
    
    if (!queue || queue.length === 0) {
        display.innerHTML = '<p>La fila está vacía.</p>';
        return;
    }
    
    let html = `<p><strong>Total en fila: ${queue.length} personas</strong></p>`;
    
    queue.forEach((entry, index) => {
        const isFastPass = entry.ticketType === 'FAST_PASS';
        html += `
            <div class="queue-entry ${isFastPass ? 'fast-pass' : ''}">
                <div class="queue-entry-info">
                    <div class="queue-entry-position">#${index + 1}</div>
                    <div class="queue-entry-name">${entry.visitorName}</div>
                    <div class="queue-entry-type">${entry.ticketType}</div>
                </div>
                <button class="btn-secondary" onclick="removeFromQueue(${entry.visitorId})">Remover</button>
            </div>
        `;
    });
    
    display.innerHTML = html;
}

// Admitir siguiente visitante
async function admitNextVisitor() {
    const attractionId = document.getElementById('attractionSelect').value;
    
    if (!attractionId) {
        alert('Selecciona una atracción');
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE_URL}/queue/next/${attractionId}`, {
            headers: getAuthHeaders()
        });
        
        if (response.ok) {
            const nextVisitor = await response.json();
            alert(`Siguiente visitante: ${nextVisitor.visitorName}`);
            loadQueueForAttraction();
        } else {
            alert('La fila está vacía');
        }
    } catch (error) {
        console.error('Error admitting visitor:', error);
    }
}

// Remover de la fila
async function removeFromQueue(visitorId) {
    const attractionId = document.getElementById('attractionSelect').value;
    
    try {
        const response = await fetch(`${API_BASE_URL}/queue/remove-visitor/${attractionId}/${visitorId}`, {
            method: 'DELETE',
            headers: getAuthHeaders()
        });
        
        if (response.ok) {
            alert('Visitante removido de la fila');
            loadQueueForAttraction();
        }
    } catch (error) {
        console.error('Error removing visitor:', error);
    }
}

// Limpiar fila
function clearQueue() {
    if (confirm('¿Estás seguro de que quieres limpiar la fila?')) {
        const attractionId = document.getElementById('attractionSelect').value;
        // En una aplicación real, habría un endpoint para esto
        alert('Fila limpiada');
        loadQueueForAttraction();
    }
}

// Abrir formulario de revisión
function openRevisionForm(attractionId) {
    document.getElementById('attractionRevision').value = attractionId;
    
    document.querySelectorAll('.op-section').forEach(s => s.classList.remove('active'));
    document.getElementById('revision').classList.add('active');
    
    document.querySelectorAll('.op-nav-link').forEach(l => l.classList.remove('active'));
    document.querySelector('a[href="#revision"]').classList.add('active');
}

// Enviar revisión técnica
function submitRevision() {
    const attractionId = document.getElementById('attractionRevision').value;
    const result = document.getElementById('revisionResult').value;
    const comments = document.getElementById('revisionComments').value;
    
    if (!attractionId) {
        alert('Selecciona una atracción');
        return;
    }
    
    // En una aplicación real, se enviaría al servidor
    console.log('Revisión registrada:', { attractionId, result, comments });
    
    alert('Revisión técnica registrada exitosamente');
    
    // Limpiar formulario
    document.getElementById('revisionComments').value = '';
    
    // Agregar a historial
    addRevisionToHistory(attractionId, result, comments);
    
    // Recargar atracciones
    loadAttractions();
}

// Agregar a historial de revisiones
function addRevisionToHistory(attractionId, result, comments) {
    const now = new Date().toLocaleString();
    const revisionList = document.getElementById('revisionList');
    
    const item = document.createElement('div');
    item.className = 'revision-item';
    item.innerHTML = `
        <div class="revision-date">${now}</div>
        <div class="revision-result ${result === 'SATISFACTORIA' ? 'success' : 'fail'}">
            ${result === 'SATISFACTORIA' ? '✅' : '❌'} ${result}
        </div>
        <p>${comments || 'Sin comentarios'}</p>
    `;
    
    revisionList.insertBefore(item, revisionList.firstChild);
}

// Actualizar opciones de estado
function updateStatusOptions() {
    const attractionId = document.getElementById('attractionStatus').value;
    const attraction = attractions.find(a => a.id == attractionId);
    
    if (attraction) {
        document.getElementById('newStatus').value = attraction.status;
    }
}

// Cambiar estado de atracción
async function changeAttractionStatus() {
    const attractionId = document.getElementById('attractionStatus').value;
    const newStatus = document.getElementById('newStatus').value;
    const reason = document.getElementById('statusReason').value;
    
    if (!attractionId || !newStatus) {
        alert('Completa todos los campos');
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE_URL}/attractions/change-status/${attractionId}`, {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify({
                status: newStatus,
                reason: reason || 'Sin especificar'
            })
        });
        
        if (response.ok) {
            alert('Estado actualizado exitosamente');
            
            // Agregar a historial de cambios
            const now = new Date().toLocaleString();
            const statusChanges = document.getElementById('statusChanges');
            const item = document.createElement('div');
            item.className = `status-change-item ${newStatus === 'CERRADA' ? 'danger' : ''}`;
            item.innerHTML = `
                <div class="status-change-time">${now}</div>
                <div><strong>Nuevo estado:</strong> ${newStatus}</div>
                <div><strong>Motivo:</strong> ${reason || 'No especificado'}</div>
            `;
            statusChanges.insertBefore(item, statusChanges.firstChild);
            
            loadAttractions();
        }
    } catch (error) {
        console.error('Error changing status:', error);
        alert('Error al cambiar el estado');
    }
}

// Utilidades
function getAuthHeaders() {
    return {
        'Content-Type': 'application/json',
        'Authorization': localStorage.getItem('token') || ''
    };
}

function logout() {
    localStorage.clear();
    window.location.href = '../index.html';
}
