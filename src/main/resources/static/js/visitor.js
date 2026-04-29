// Verificar autenticación
if (!localStorage.getItem('token')) {
    window.location.href = '../index.html';
}

const API_BASE_URL = 'http://localhost:8080/api';
let currentUser = null;
let attractions = [];
let favorites = [];
let currentAttractionId = null;

// Inicializar
document.addEventListener('DOMContentLoaded', function() {
    loadVisitorInfo();
    loadAttractions();
    loadFavorites();
    drawVisitorMap();
});

// Cargar información del visitante
async function loadVisitorInfo() {
    const userId = localStorage.getItem('userId');
    const username = localStorage.getItem('username');
    
    document.getElementById('visitorName').textContent = username || 'Visitante';
    
    try {
        const response = await fetch(`${API_BASE_URL}/visitors/${userId}`, {
            headers: getAuthHeaders()
        });
        
        if (response.ok) {
            currentUser = await response.json();
            updateBalance();
        }
    } catch (error) {
        console.error('Error loading visitor info:', error);
    }
}

// Actualizar saldo
function updateBalance() {
    if (currentUser) {
        document.getElementById('balanceDisplay').textContent = 
            `Saldo: $${currentUser.virtualBalance.toFixed(2)}`;
    }
}

// Cargar atracciones
async function loadAttractions() {
    try {
        const response = await fetch(`${API_BASE_URL}/attractions`, {
            headers: getAuthHeaders()
        });
        
        if (response.ok) {
            attractions = await response.json();
            renderAttractionsGrid();
        }
    } catch (error) {
        console.error('Error loading attractions:', error);
    }
}

// Renderizar grid de atracciones
function renderAttractionsGrid() {
    const grid = document.getElementById('attractionsGrid');
    grid.innerHTML = '';
    
    attractions.forEach(attraction => {
        const canAccess = checkAccessRestrictions(attraction);
        const isFavorite = favorites.includes(attraction.id);
        
        const card = document.createElement('div');
        card.className = 'attraction-card';
        card.innerHTML = `
            <div class="attraction-header">
                <h3>${attraction.name}</h3>
                <span class="attraction-type">${attraction.type}</span>
            </div>
            <div class="attraction-body">
                <div class="attraction-info">
                    <strong>Zona ID:</strong> ${attraction.zoneId}
                </div>
                <div class="attraction-info">
                    <strong>Visitantes hoy:</strong> ${attraction.accumulatedVisitors}
                </div>
                <div class="attraction-wait">
                    <span class="queue-badge">⏱️ ${attraction.estimatedWaitTime} min</span>
                </div>
                <div class="attraction-info">
                    <strong>Estado:</strong> ${getStatusBadgeHTML(attraction.status)}
                </div>
                ${!canAccess ? '<div class="attraction-restrictions">❌ No cumples los requisitos</div>' : ''}
                ${attraction.additionalCost > 0 ? `<div class="attraction-info">💰 Costo adicional: $${attraction.additionalCost.toFixed(2)}</div>` : ''}
                <div class="attraction-actions">
                    <button class="btn-queue" ${!canAccess ? 'disabled' : ''} onclick="showAttractionModal(${attraction.id})">Unirse a Fila</button>
                    <button class="btn-favorite" onclick="toggleFavorite(${attraction.id})">
                        ${isFavorite ? '❤️' : '🤍'}
                    </button>
                </div>
            </div>
        `;
        grid.appendChild(card);
    });
}

// Cargar favoritos
async function loadFavorites() {
    const userId = localStorage.getItem('userId');
    
    try {
        const response = await fetch(`${API_BASE_URL}/visitors/${userId}`, {
            headers: getAuthHeaders()
        });
        
        if (response.ok) {
            const visitor = await response.json();
            if (visitor.favoriteAttractions) {
                favorites = visitor.favoriteAttractions.toList ? visitor.favoriteAttractions.toList() : [];
            }
            renderFavoritesGrid();
        }
    } catch (error) {
        console.error('Error loading favorites:', error);
    }
}

// Renderizar grid de favoritos
function renderFavoritesGrid() {
    const grid = document.getElementById('favoritesGrid');
    grid.innerHTML = '';
    
    if (favorites.length === 0) {
        grid.innerHTML = '<p>No tienes atracciones favoritas aún.</p>';
        return;
    }
    
    const favoriteAttractions = attractions.filter(a => favorites.includes(a.id));
    
    favoriteAttractions.forEach(attraction => {
        const card = document.createElement('div');
        card.className = 'attraction-card';
        card.innerHTML = `
            <div class="attraction-header">
                <h3>${attraction.name}</h3>
                <span class="attraction-type">${attraction.type}</span>
            </div>
            <div class="attraction-body">
                <div class="attraction-wait">
                    <span class="queue-badge">⏱️ ${attraction.estimatedWaitTime} min</span>
                </div>
                <div class="attraction-actions">
                    <button class="btn-queue" onclick="showAttractionModal(${attraction.id})">Unirse a Fila</button>
                    <button class="btn-favorite" onclick="toggleFavorite(${attraction.id})">❤️</button>
                </div>
            </div>
        `;
        grid.appendChild(card);
    });
}

// Mostrar modal de atracción
function showAttractionModal(attractionId) {
    const attraction = attractions.find(a => a.id === attractionId);
    if (!attraction) return;
    
    currentAttractionId = attractionId;
    
    const details = `
        <h2>${attraction.name}</h2>
        <div class="detail-row">
            <span class="detail-label">Tipo:</span>
            <span>${attraction.type}</span>
        </div>
        <div class="detail-row">
            <span class="detail-label">Capacidad por ciclo:</span>
            <span>${attraction.maxCapacityPerCycle} personas</span>
        </div>
        <div class="detail-row">
            <span class="detail-label">Tiempo de espera:</span>
            <span>${attraction.estimatedWaitTime} minutos</span>
        </div>
        <div class="detail-row">
            <span class="detail-label">Visitantes hoy:</span>
            <span>${attraction.accumulatedVisitors}</span>
        </div>
        <div class="detail-row">
            <span class="detail-label">Altura mínima:</span>
            <span>${attraction.minHeight}m</span>
        </div>
        <div class="detail-row">
            <span class="detail-label">Edad mínima:</span>
            <span>${attraction.minAge} años</span>
        </div>
        ${attraction.additionalCost > 0 ? `
        <div class="detail-row">
            <span class="detail-label">Costo adicional:</span>
            <span>$${attraction.additionalCost.toFixed(2)}</span>
        </div>
        ` : ''}
        <div class="detail-row">
            <span class="detail-label">Estado:</span>
            <span>${getStatusBadgeHTML(attraction.status)}</span>
        </div>
    `;
    
    document.getElementById('attractionDetails').innerHTML = details;
    document.getElementById('attractionModal').style.display = 'block';
}

// Unirse a la fila
async function joinQueue() {
    const attractionId = currentAttractionId;
    const userId = localStorage.getItem('userId');
    const username = localStorage.getItem('username');
    
    const attraction = attractions.find(a => a.id === attractionId);
    const ticketType = 'GENERAL'; // Por defecto
    
    try {
        const response = await fetch(`${API_BASE_URL}/queue/add-visitor`, {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify({
                attractionId: attractionId,
                visitorId: userId,
                visitorName: username,
                ticketType: ticketType
            })
        });
        
        if (response.ok) {
            const result = await response.json();
            closeModal('attractionModal');
            alert(`Te has unido a la fila de ${attraction.name}. Tu posición: ${result.position}`);
            switchTab('fila');
            loadQueueInfo();
        }
    } catch (error) {
        console.error('Error joining queue:', error);
        alert('Error al unirse a la fila');
    }
}

// Obtener información de la fila
async function loadQueueInfo() {
    const userId = localStorage.getItem('userId');
    const container = document.getElementById('queueInfo');
    
    try {
        const stats = await fetch(`${API_BASE_URL}/queue/stats`, {
            headers: getAuthHeaders()
        }).then(r => r.json());
        
        if (Object.keys(stats).length === 0) {
            container.innerHTML = '<p>No estás en ninguna fila actualmente.</p>';
            return;
        }
        
        let html = '<div class="queue-info">';
        
        for (const [attractionId, queueSize] of Object.entries(stats)) {
            const attraction = attractions.find(a => a.id == attractionId);
            if (attraction) {
                html += `
                    <div class="queue-details">
                        <h3>${attraction.name}</h3>
                        <div class="queue-position">Tu posición: ${queueSize}</div>
                        <p>Personas en fila: ${queueSize}</p>
                        <p>Tiempo estimado: ${attraction.estimatedWaitTime * queueSize / attraction.maxCapacityPerCycle} minutos</p>
                    </div>
                `;
            }
        }
        
        html += '</div>';
        container.innerHTML = html;
    } catch (error) {
        console.error('Error loading queue info:', error);
    }
}

// Agregar a favoritos
async function toggleFavorite(attractionId) {
    const userId = localStorage.getItem('userId');
    const isFavorite = favorites.includes(attractionId);
    
    try {
        const endpoint = isFavorite ? 'remove-favorite' : 'add-favorite';
        const response = await fetch(`${API_BASE_URL}/visitors/${userId}/${endpoint}`, {
            method: 'PUT',
            headers: getAuthHeaders(),
            body: JSON.stringify({ attractionId: attractionId })
        });
        
        if (response.ok) {
            if (isFavorite) {
                favorites = favorites.filter(id => id !== attractionId);
            } else {
                favorites.push(attractionId);
            }
            renderAttractionsGrid();
            renderFavoritesGrid();
        }
    } catch (error) {
        console.error('Error toggling favorite:', error);
    }
}

// Dibujar mapa del parque
function drawVisitorMap() {
    const canvas = document.getElementById('visitorMap');
    if (!canvas) return;
    
    const ctx = canvas.getContext('2d');
    
    // Limpiar canvas
    ctx.fillStyle = '#f0f9ff';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    
    // Dibujar zonas
    const zones = [
        { name: 'Aventura', x: 150, y: 150, color: '#667eea', size: 60 },
        { name: 'Infantil', x: 400, y: 150, color: '#764ba2', size: 50 },
        { name: 'Acuática', x: 650, y: 150, color: '#3b82f6', size: 70 }
    ];
    
    zones.forEach(zone => {
        ctx.fillStyle = zone.color;
        ctx.beginPath();
        ctx.arc(zone.x, zone.y, zone.size, 0, 2 * Math.PI);
        ctx.fill();
        
        ctx.fillStyle = 'white';
        ctx.font = 'bold 14px Arial';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(zone.name, zone.x, zone.y);
    });
    
    // Hacer clickeable
    canvas.addEventListener('click', function(e) {
        const rect = canvas.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;
        
        zones.forEach(zone => {
            const distance = Math.sqrt((x - zone.x) ** 2 + (y - zone.y) ** 2);
            if (distance < zone.size) {
                showZoneAttractions(zone.name);
            }
        });
    });
}

// Mostrar atracciones de una zona
function showZoneAttractions(zoneName) {
    alert(`Atracciones en ${zoneName}`);
}

// Cambiar tab
function switchTab(tabName) {
    // Ocultar todos los tabs
    document.querySelectorAll('.tab-content').forEach(tab => {
        tab.classList.remove('active');
    });
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.remove('active');
    });
    
    // Mostrar el seleccionado
    document.getElementById(tabName).classList.add('active');
    event.target.classList.add('active');
    
    if (tabName === 'fila') {
        loadQueueInfo();
    }
}

// Utilidades
function checkAccessRestrictions(attraction) {
    if (!currentUser) return true;
    
    if (currentUser.height < attraction.minHeight) return false;
    if (currentUser.age < attraction.minAge) return false;
    
    return true;
}

function getStatusBadgeHTML(status) {
    let color = '#667eea';
    if (status === 'CERRADA') color = '#ef4444';
    if (status === 'MANTENIMIENTO') color = '#f59e0b';
    
    return `<span style="color: ${color}; font-weight: 600;">${status}</span>`;
}

function closeModal(modalId) {
    document.getElementById(modalId).style.display = 'none';
}

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

window.onclick = function(event) {
    const modals = document.querySelectorAll('.modal');
    modals.forEach(modal => {
        if (event.target === modal) {
            modal.style.display = 'none';
        }
    });
}
