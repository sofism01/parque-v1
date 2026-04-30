const API_BASE_URL = 'http://localhost:8080/api';
const REFRESH_INTERVAL_MS = 3000;

let currentUser = null;
let attractions = [];
let zones = [];
let favorites = [];
let graphSnapshot = { nodes: [], edges: [], zones: [] };
let highlightedPath = [];
let selectedOriginId = null;
let selectedDestinationId = null;
let currentAttractionId = null;
let queueRefreshHandle = null;

document.addEventListener('DOMContentLoaded', async () => {
    console.log('[Visitor] DOM listo, iniciando panel');

    if (!localStorage.getItem('token')) {
        console.error('[Visitor] No hay token disponible, redirigiendo al login');
        window.location.href = '../index.html';
        return;
    }

    bindTabControls();
    bindMapControls();

    try {
        await initDashboard();
        await loadQueueStatus(false);
        renderHistory();
    } catch (error) {
        console.error('[Visitor] Error durante la inicializacion:', error);
        showAppAlert(error.message || 'No fue posible inicializar el panel del visitante.', 'error');
    }

    queueRefreshHandle = window.setInterval(async () => {
        await loadAttractions(false);
        await loadZones(false);
        await loadQueueStatus(false);
        await initMap(false);
    }, REFRESH_INTERVAL_MS);
});

async function initDashboard() {
    await Promise.all([
        loadProfile(),
        loadZones(),
        loadAttractions(),
        initMap()
    ]);
    renderAttractionsGrid();
    renderFavoritesGrid();
    drawMap();
}

async function apiFetch(path, options = {}) {
    const url = `${API_BASE_URL}${path}`;
    console.log(`[Visitor] Fetch -> ${url}`);

    const response = await fetch(url, {
        mode: 'cors',
        ...options,
        headers: {
            ...getAuthHeaders(),
            ...(options.headers || {})
        }
    });

    const contentType = response.headers.get('content-type') || '';
    const payload = contentType.includes('application/json') ? await response.json() : await response.text();

    if (!response.ok) {
        const message = typeof payload === 'string' ? payload : payload.message || 'No fue posible completar la solicitud';
        throw new Error(message);
    }

    return payload;
}

function bindTabControls() {
    switchTab('dashboard');
}

function bindMapControls() {
    const originNode = document.getElementById('originNode');
    const destinationNode = document.getElementById('destinationNode');

    if (originNode) {
        originNode.addEventListener('change', (event) => {
            selectedOriginId = parseOptionalNumber(event.target.value);
            highlightedPath = [];
            drawMap();
        });
    }

    if (destinationNode) {
        destinationNode.addEventListener('change', (event) => {
            selectedDestinationId = parseOptionalNumber(event.target.value);
        });
    }
}

function switchTab(tabId) {
    console.log(`[Visitor] Cambiando a tab ${tabId}`);
    document.querySelectorAll('.tab-content').forEach((tab) => tab.classList.remove('active'));
    document.querySelectorAll('.tab-btn').forEach((button) => button.classList.remove('active'));

    const tab = document.getElementById(tabId);
    const button = document.querySelector(`.tab-btn[data-tab="${tabId}"]`);

    if (tab) {
        tab.classList.add('active');
    }
    if (button) {
        button.classList.add('active');
    }

    if (tabId === 'fila') {
        loadQueueStatus(false);
    }
}

async function loadProfile(showErrors = true) {
    console.log('Cargando perfil...');
    const userId = localStorage.getItem('userId');

    try {
        currentUser = await apiFetch(`/visitor/profile?id=${encodeURIComponent(userId || '')}`);
        favorites = Array.isArray(currentUser.favoriteAttractions) ? currentUser.favoriteAttractions : [];
        renderProfile();
        renderQueueTicket();
        renderHistory();
        hideAppAlert();
    } catch (error) {
        if (showErrors) {
            showAppAlert(`No fue posible cargar el perfil: ${error.message}`, 'error');
        }
        throw error;
    }
}

async function loadAttractions(showErrors = true) {
    console.log('Cargando atracciones...');
    try {
        attractions = await apiFetch('/visitor/attractions');
        renderAttractionsGrid();
        renderFavoritesGrid();
    } catch (error) {
        console.error('[Visitor] Error cargando atracciones:', error);
        if (showErrors) {
            showAppAlert(`No fue posible cargar atracciones: ${error.message}`, 'error');
        }
    }
}

async function loadZones(showErrors = true) {
    console.log('Cargando zonas...');
    try {
        zones = await apiFetch('/visitor/zones');
    } catch (error) {
        console.error('[Visitor] Error cargando zonas:', error);
        if (showErrors) {
            showAppAlert(`No fue posible cargar zonas: ${error.message}`, 'error');
        }
    }
}

async function initMap(showErrors = true) {
    console.log('Cargando mapa...');

    try {
        graphSnapshot = await apiFetch('/visitor/graph');
        const graphZoneIds = new Set((Array.isArray(graphSnapshot.zones) ? graphSnapshot.zones : []).map((zone) => zone.id));
        zones = zones.filter((zone) => graphZoneIds.has(zone.id));
    } catch (error) {
        console.error('[Visitor] Error cargando el grafo:', error);
        if (showErrors) {
            showAppAlert(`No fue posible cargar el mapa: ${error.message}`, 'error');
        }
        return;
    }

    populateNodeSelectors();

    const canvas = document.getElementById('parkMap');
    if (!canvas) {
        console.error('[Visitor] No existe el canvas parkMap en el HTML');
        return;
    }

    const ctx = canvas.getContext('2d');
    drawMap(ctx, canvas);
}

function drawMap(ctx = null, canvas = null) {
    const mapCanvas = canvas || document.getElementById('parkMap');
    if (!mapCanvas) {
        console.error('[Visitor] No se encontro el canvas parkMap');
        return;
    }

    const context = ctx || mapCanvas.getContext('2d');
    const nodes = Array.isArray(graphSnapshot.nodes) ? graphSnapshot.nodes : [];
    const edges = Array.isArray(graphSnapshot.edges) ? graphSnapshot.edges : [];
    const positionedNodes = resolveGraphLayout(nodes, mapCanvas);
    const nodeMap = new Map(positionedNodes.map((node) => [node.id, node]));
    const graphZones = Array.isArray(graphSnapshot.zones) ? graphSnapshot.zones : [];
    const zoneMap = new Map(graphZones.map((zone) => [zone.id, zone]));

    context.clearRect(0, 0, mapCanvas.width, mapCanvas.height);
    const background = context.createLinearGradient(0, 0, mapCanvas.width, mapCanvas.height);
    background.addColorStop(0, '#f7fffb');
    background.addColorStop(1, '#ecfdf5');
    context.fillStyle = background;
    context.fillRect(0, 0, mapCanvas.width, mapCanvas.height);

    drawZoneGroups(context, positionedNodes, zoneMap);

    edges.forEach((edge) => {
        const source = nodeMap.get(edge.sourceId);
        const target = nodeMap.get(edge.targetId);
        if (!isRenderableNode(source) || !isRenderableNode(target)) {
            return;
        }

        const highlighted = isHighlightedEdge(edge.sourceId, edge.targetId);
        context.beginPath();
        context.moveTo(source.drawX, source.drawY);
        context.lineTo(target.drawX, target.drawY);
        context.strokeStyle = highlighted ? '#0f766e' : '#99f6e4';
        context.lineWidth = highlighted ? 5 : 2;
        context.stroke();

        context.fillStyle = '#334155';
        context.font = '12px Segoe UI';
        context.fillText(String(edge.weight), (source.drawX + target.drawX) / 2, (source.drawY + target.drawY) / 2 - 6);
    });

    positionedNodes.forEach((node) => {
        if (!isRenderableNode(node)) {
            drawConstructionNode(context, node);
            return;
        }

        context.beginPath();
        context.fillStyle = resolveNodeColor(node.status);
        context.arc(node.drawX, node.drawY, node.id === selectedDestinationId ? 24 : 20, 0, Math.PI * 2);
        context.fill();

        if (node.id === selectedOriginId) {
            context.lineWidth = 4;
            context.strokeStyle = '#0f172a';
            context.stroke();
        }

        context.fillStyle = '#ffffff';
        context.font = 'bold 12px Segoe UI';
        context.textAlign = 'center';
        context.fillText(node.name, node.drawX, node.drawY + 34);
    });

    mapCanvas.onclick = (event) => {
        const rect = mapCanvas.getBoundingClientRect();
        const x = event.clientX - rect.left;
        const y = event.clientY - rect.top;

        positionedNodes.forEach((node) => {
            if (!isRenderableNode(node)) {
                return;
            }
            const distance = Math.hypot(node.drawX - x, node.drawY - y);
            if (distance <= 24) {
                selectedDestinationId = node.id;
                const destinationNode = document.getElementById('destinationNode');
                if (destinationNode) {
                    destinationNode.value = String(node.id);
                }
                setText('mapStatus', `Destino seleccionado: ${node.name}`);
            }
        });
    };
}

async function calculateRoute(destinoId = null) {
    if (destinoId != null) {
        selectedDestinationId = Number(destinoId);
        const destinationNode = document.getElementById('destinationNode');
        if (destinationNode) {
            destinationNode.value = String(destinoId);
        }
    }

    if (!selectedOriginId || !selectedDestinationId) {
        showAppAlert('Selecciona un origen y un destino para calcular la ruta.', 'error');
        return;
    }

    try {
        const route = await apiFetch(`/visitor/path?origin=${selectedOriginId}&destination=${selectedDestinationId}`);
        highlightedPath = Array.isArray(route.path) ? route.path.map((node) => node.id) : [];
        drawMap();
        renderRouteInfo(route);
        hideAppAlert();
    } catch (error) {
        console.error('[Visitor] Error calculando ruta:', error);
        showAppAlert(`No fue posible calcular la ruta: ${error.message}`, 'error');
    }
}

async function joinQueue(attractionId = null) {
    const finalAttractionId = attractionId || currentAttractionId;
    if (!finalAttractionId) {
        showAppAlert('Selecciona una atraccion antes de unirte a la fila.', 'error');
        return;
    }

    try {
        const queueStatus = await apiFetch('/visitor/queue/join', {
            method: 'POST',
            body: JSON.stringify({
                attractionId: finalAttractionId,
                visitorId: Number(localStorage.getItem('userId'))
            })
        });
        closeModal('attractionModal');
        await loadProfile(false);
        renderQueueStatus(queueStatus);
        switchTab('fila');
        showAppAlert(queueStatus.message || 'Te has unido a la fila correctamente.', 'success');
    } catch (error) {
        console.error('[Visitor] Error uniendo a fila:', error);
        showAppAlert(`No fue posible unirte a la fila: ${error.message}`, 'error');
    }
}

async function loadQueueStatus(showErrors = true) {
    console.log('Cargando fila virtual...');
    const userId = localStorage.getItem('userId');

    try {
        const queueStatus = await apiFetch(`/visitor/queue/status?id=${encodeURIComponent(userId || '')}`);
        renderQueueStatus(queueStatus);
    } catch (error) {
        console.error('[Visitor] Error consultando fila:', error);
        if (showErrors) {
            showAppAlert(`No fue posible consultar la fila: ${error.message}`, 'error');
        }
    }
}

async function leaveQueue() {
    try {
        await apiFetch('/visitor/queue/leave', {
            method: 'POST',
            body: JSON.stringify({
                visitorId: Number(localStorage.getItem('userId'))
            })
        });
        await loadProfile(false);
        await loadQueueStatus(false);
        showAppAlert('Has salido de la fila correctamente.', 'success');
    } catch (error) {
        console.error('[Visitor] Error saliendo de fila:', error);
        showAppAlert(`No fue posible salir de la fila: ${error.message}`, 'error');
    }
}

function renderProfile() {
    if (!currentUser) {
        return;
    }

    setText('visitorName', currentUser.username || 'Visitante');
    setText('balanceDisplay', `Saldo: $${Number(currentUser.virtualBalance || 0).toFixed(2)}`);
    setText('profileMeta', `Ticket ${formatTicketType(currentUser.ticketType)} | ${Number(currentUser.height || 0).toFixed(2)} m | ${currentUser.age || 0} anos`);

    const profileSummary = document.getElementById('profileSummary');
    if (!profileSummary) {
        return;
    }

    profileSummary.innerHTML = `
        <div class="profile-item"><span>Documento</span><strong>${currentUser.document || 'Sin registrar'}</strong></div>
        <div class="profile-item"><span>Tipo de ticket</span><strong>${formatTicketType(currentUser.ticketType)}</strong></div>
        <div class="profile-item"><span>Favoritos</span><strong>${favorites.length}</strong></div>
        <div class="profile-item"><span>Historial</span><strong>${Array.isArray(currentUser.visitHistory) ? currentUser.visitHistory.length : 0}</strong></div>
    `;
}

function renderAttractionsGrid() {
    const attractionsGrid = document.getElementById('attractionsGrid');
    if (!attractionsGrid) {
        return;
    }

    const filtered = attractions.filter(isAllowedForVisitor);
    if (!filtered.length) {
        attractionsGrid.innerHTML = '<p class="empty-state">No hay atracciones disponibles para tu perfil.</p>';
        return;
    }

    attractionsGrid.innerHTML = filtered.map((attraction) => {
        const isFavorite = favorites.includes(attraction.id);
        const queueDisabled = attraction.status !== 'ACTIVA';

        return `
            <article class="attraction-card ${queueDisabled ? 'disabled' : ''}">
                <div class="attraction-card-header">
                    <div>
                        <h3>${attraction.name}</h3>
                        <div>${formatAttractionType(attraction.type)}</div>
                    </div>
                    ${renderStatusBadge(attraction.status)}
                </div>
                <div class="attraction-card-body">
                    <div class="attraction-meta"><strong>Zona:</strong> ${attraction.zoneId ?? '-'}</div>
                    <div class="attraction-meta"><strong>Coordenadas:</strong> ${formatCoordinates(attraction)}</div>
                    <div class="attraction-meta"><strong>Visitantes:</strong> ${attraction.accumulatedVisitors ?? 0}</div>
                    <div class="attraction-meta"><strong>Estado:</strong> ${attraction.status}</div>
                    <div class="attraction-meta"><span class="wait-badge">${attraction.estimatedWaitTime || 0} min de espera</span></div>
                    <div class="card-actions">
                        <button class="btn-queue" ${queueDisabled ? 'disabled' : ''} onclick="showAttractionModal(${attraction.id})">Unirse a la Fila</button>
                        <button class="btn-favorite" onclick="toggleFavorite(${attraction.id})">${isFavorite ? 'Quitar' : 'Favorito'}</button>
                    </div>
                </div>
            </article>
        `;
    }).join('');
}

function renderFavoritesGrid() {
    const favoritesGrid = document.getElementById('favoritesGrid');
    if (!favoritesGrid) {
        return;
    }

    const favoriteAttractions = attractions.filter((attraction) => favorites.includes(attraction.id));
    if (!favoriteAttractions.length) {
        favoritesGrid.innerHTML = '<p class="empty-state">No tienes atracciones favoritas guardadas.</p>';
        return;
    }

    favoritesGrid.innerHTML = favoriteAttractions.map((attraction) => `
        <article class="attraction-card">
            <div class="attraction-card-header">
                <div>
                    <h3>${attraction.name}</h3>
                    <div>${formatAttractionType(attraction.type)}</div>
                </div>
                ${renderStatusBadge(attraction.status)}
            </div>
            <div class="attraction-card-body">
                <div class="attraction-meta"><span class="wait-badge">${attraction.estimatedWaitTime || 0} min de espera</span></div>
                <div class="card-actions">
                    <button class="btn-queue" ${attraction.status !== 'ACTIVA' ? 'disabled' : ''} onclick="showAttractionModal(${attraction.id})">Unirse</button>
                    <button class="btn-favorite" onclick="toggleFavorite(${attraction.id})">Quitar</button>
                </div>
            </div>
        </article>
    `).join('');
}

async function toggleFavorite(attractionId) {
    const userId = localStorage.getItem('userId');
    const endpoint = favorites.includes(attractionId) ? 'remove-favorite' : 'add-favorite';

    try {
        await apiFetch(`/visitors/${userId}/${endpoint}`, {
            method: 'PUT',
            body: JSON.stringify({ attractionId })
        });
        await loadProfile(false);
        renderAttractionsGrid();
        renderFavoritesGrid();
    } catch (error) {
        console.error('[Visitor] Error actualizando favorito:', error);
        showAppAlert(`No fue posible actualizar favoritos: ${error.message}`, 'error');
    }
}

function renderQueueStatus(queueStatus) {
    const queueInfo = document.getElementById('queueInfo');
    if (!queueInfo) {
        return;
    }

    if (!queueStatus || !queueStatus.attractionId) {
        queueInfo.innerHTML = '<p class="empty-state">No tienes una fila activa.</p>';
        setText('ticketAttraction', 'Sin fila activa');
        setText('ticketWait', '--');
        setText('ticketPosition', '--');
        setText('ticketStatus', queueStatus?.message || 'Aun no tienes una fila activa.');
        renderQueueTicket();
        return;
    }

    queueInfo.innerHTML = `
        <div class="queue-card">
            <h3>${queueStatus.attractionName}</h3>
            <div class="queue-row"><span>Posicion actual</span><strong>${queueStatus.position ?? '--'}</strong></div>
            <div class="queue-row"><span>Personas en fila</span><strong>${queueStatus.totalInQueue ?? '--'}</strong></div>
            <div class="queue-row"><span>Espera estimada</span><strong>${queueStatus.estimatedWaitTime || 0} min</strong></div>
            <div class="queue-row"><span>Ticket</span><strong>${formatTicketType(queueStatus.ticketType)}</strong></div>
            <div class="card-actions"><button class="btn-secondary" onclick="leaveQueue()">Salir de la fila</button></div>
        </div>
    `;

    setText('ticketAttraction', queueStatus.attractionName || 'Fila activa');
    setText('ticketWait', `${queueStatus.estimatedWaitTime || 0} min`);
    setText('ticketPosition', String(queueStatus.position ?? '--'));
    setText('ticketStatus', queueStatus.message || 'Fila virtual activa.');
    renderQueueTicket();
}

function renderQueueTicket() {
    if (!currentUser) {
        return;
    }

    setText('ticketType', formatTicketType(currentUser.ticketType));
    setText('ticketBalance', `$${Number(currentUser.virtualBalance || 0).toFixed(2)}`);
}

function renderHistory() {
    const historyList = document.getElementById('historyList');
    if (!historyList || !currentUser) {
        return;
    }

    const history = Array.isArray(currentUser.visitHistory) ? currentUser.visitHistory : [];
    if (!history.length) {
        historyList.innerHTML = '<p class="empty-state">Todavia no hay visitas registradas.</p>';
        return;
    }

    historyList.innerHTML = history.map((attractionId, index) => {
        const attraction = attractions.find((item) => item.id === attractionId);
        return `
            <div class="history-item">
                <div><strong>${attraction ? attraction.name : `Atraccion #${attractionId}`}</strong></div>
                <span>Registro ${index + 1}</span>
            </div>
        `;
    }).join('');
}

function populateNodeSelectors() {
    const originNode = document.getElementById('originNode');
    const destinationNode = document.getElementById('destinationNode');
    const nodes = Array.isArray(graphSnapshot.nodes) ? graphSnapshot.nodes : [];
    const activeNodes = nodes.filter((node) => node.status === 'ACTIVA');

    if (!originNode || !destinationNode) {
        return;
    }

    originNode.innerHTML = '<option value="">Selecciona origen</option>';
    destinationNode.innerHTML = '<option value="">Selecciona destino</option>';

    nodes.forEach((node) => {
        const label = `${node.name} (${node.status})`;
        originNode.insertAdjacentHTML('beforeend', `<option value="${node.id}">${label}</option>`);
        destinationNode.insertAdjacentHTML('beforeend', `<option value="${node.id}">${label}</option>`);
    });

    if (!selectedOriginId && activeNodes.length > 0) {
        selectedOriginId = activeNodes[0].id;
    }
    if (!selectedDestinationId && activeNodes.length > 1) {
        selectedDestinationId = activeNodes[1].id;
    }

    originNode.value = selectedOriginId ?? '';
    destinationNode.value = selectedDestinationId ?? '';
}

function renderRouteInfo(route) {
    const routeInfo = document.getElementById('routeInfo');
    const routeList = document.getElementById('routeList');
    if (!routeInfo || !routeList) {
        return;
    }

    routeInfo.hidden = false;
    if (!route.reachable || !Array.isArray(route.pathNames) || !route.pathNames.length) {
        routeList.innerHTML = `<p class="empty-state">${route.message || 'No hay ruta disponible.'}</p>`;
        return;
    }

    routeList.innerHTML = `
        <div class="route-step"><strong>Distancia total:</strong> ${route.totalDistance}</div>
        ${route.pathNames.map((name, index) => `<div class="route-step">${index + 1}. ${name}</div>`).join('')}
    `;
}

function showAttractionModal(attractionId) {
    const attraction = attractions.find((item) => item.id === attractionId);
    if (!attraction) {
        return;
    }

    currentAttractionId = attractionId;
    const attractionDetails = document.getElementById('attractionDetails');
    if (!attractionDetails) {
        return;
    }

    attractionDetails.innerHTML = `
        <h2>${attraction.name}</h2>
        <div class="detail-row"><span class="detail-label">Tipo</span><span>${formatAttractionType(attraction.type)}</span></div>
        <div class="detail-row"><span class="detail-label">Estado</span><span>${renderStatusBadge(attraction.status)}</span></div>
        <div class="detail-row"><span class="detail-label">Espera estimada</span><span>${attraction.estimatedWaitTime || 0} min</span></div>
        <div class="detail-row"><span class="detail-label">Costo adicional</span><span>$${Number(attraction.additionalCost || 0).toFixed(2)}</span></div>
    `;

    document.getElementById('attractionModal').style.display = 'block';
}

function isAllowedForVisitor(attraction) {
    if (!currentUser) {
        return true;
    }
    return Number(currentUser.height || 0) >= Number(attraction.minHeight || 0)
        && Number(currentUser.age || 0) >= Number(attraction.minAge || 0);
}

function renderStatusBadge(status) {
    if (status === 'ACTIVA') {
        return '<span class="status-badge status-active">ACTIVA</span>';
    }
    if (status === 'MANTENIMIENTO') {
        return '<span class="status-badge status-warning">MANTENIMIENTO</span>';
    }
    return '<span class="status-badge status-danger">CERRADA</span>';
}

function resolveNodeColor(status) {
    if (status === 'ACTIVA') {
        return '#10b981';
    }
    if (status === 'MANTENIMIENTO') {
        return '#f59e0b';
    }
    return '#ef4444';
}

function resolveGraphLayout(nodes, canvas) {
    if (!nodes.length || !canvas) {
        return [];
    }

    const fallbackX = canvas.width / 2;
    const fallbackY = canvas.height / 2;

    return nodes.map((node) => {
        const hasValidCoordinates = node && node.posX !== null && node.posY !== null
            && Number.isFinite(Number(node.posX)) && Number.isFinite(Number(node.posY));
        return {
            ...node,
            drawX: hasValidCoordinates ? Number(node.posX) : fallbackX,
            drawY: hasValidCoordinates ? Number(node.posY) : fallbackY,
            hasSpatialData: hasValidCoordinates
        };
    });
}

function drawZoneGroups(context, positionedNodes, zoneMap) {
    const palette = ['rgba(67, 233, 123, 0.12)', 'rgba(56, 249, 215, 0.12)', 'rgba(45, 212, 191, 0.14)', 'rgba(16, 185, 129, 0.12)'];
    Array.from(zoneMap.values()).forEach((zone, index) => {
        if (!zone || zone.posX == null || zone.posY == null) {
            return;
        }
        const centerX = Number(zone.posX);
        const centerY = Number(zone.posY);
        const width = Number(zone.width || 220);
        const height = Number(zone.height || 160);
        const left = centerX - (width / 2);
        const top = centerY - (height / 2);

        context.beginPath();
        context.fillStyle = palette[index % palette.length];
        context.rect(left, top, width, height);
        context.fill();

        context.fillStyle = '#0f766e';
        context.font = 'bold 13px Segoe UI';
        context.textAlign = 'center';
        context.fillText(zone.name || `Zona ${zone.id}`, centerX, top + 18);
    });
}

function isHighlightedEdge(sourceId, targetId) {
    if (highlightedPath.length < 2) {
        return false;
    }

    for (let index = 0; index < highlightedPath.length - 1; index += 1) {
        const current = highlightedPath[index];
        const next = highlightedPath[index + 1];
        if ((current === sourceId && next === targetId) || (current === targetId && next === sourceId)) {
            return true;
        }
    }
    return false;
}

function isRenderableNode(node) {
    return Boolean(node && node.posX !== null && node.posY !== null
        && Number.isFinite(Number(node.drawX)) && Number.isFinite(Number(node.drawY)));
}

function drawConstructionNode(context, node) {
    if (!node || !Number.isFinite(Number(node.drawX)) || !Number.isFinite(Number(node.drawY))) {
        return;
    }

    context.beginPath();
    context.fillStyle = '#cbd5e1';
    context.arc(node.drawX, node.drawY, 18, 0, Math.PI * 2);
    context.fill();
    context.strokeStyle = '#64748b';
    context.lineWidth = 2;
    context.stroke();
    context.fillStyle = '#0f172a';
    context.font = 'bold 12px Segoe UI';
    context.textAlign = 'center';
    context.fillText('?', node.drawX, node.drawY + 4);
    context.fillText(node.name || 'En construccion', node.drawX, node.drawY + 34);
}

function formatCoordinates(attraction) {
    if (!attraction || attraction.posX == null || attraction.posY == null) {
        return 'En construccion';
    }
    return `(${Number(attraction.posX).toFixed(0)}, ${Number(attraction.posY).toFixed(0)})`;
}

function parseOptionalNumber(value) {
    return value === '' || value === null || value === undefined ? null : Number(value);
}

function formatAttractionType(type) {
    return String(type || '').replaceAll('_', ' ');
}

function formatTicketType(ticketType) {
    return String(ticketType || 'GENERAL').replaceAll('_', ' ');
}

function setText(elementId, value) {
    const element = document.getElementById(elementId);
    if (element) {
        element.textContent = value;
    }
}

function showAppAlert(message, type = 'error') {
    const appAlert = document.getElementById('appAlert');
    if (!appAlert) {
        alert(message);
        return;
    }

    appAlert.hidden = false;
    appAlert.className = `app-alert ${type}`;
    appAlert.textContent = message;
}

function hideAppAlert() {
    const appAlert = document.getElementById('appAlert');
    if (!appAlert) {
        return;
    }

    appAlert.hidden = true;
    appAlert.className = 'app-alert';
    appAlert.textContent = '';
}

function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.style.display = 'none';
    }
}

function getAuthHeaders() {
    return {
        'Content-Type': 'application/json',
        Authorization: localStorage.getItem('token') || ''
    };
}

function logout() {
    if (queueRefreshHandle) {
        window.clearInterval(queueRefreshHandle);
    }
    localStorage.clear();
    window.location.href = '../index.html';
}

window.onclick = function onWindowClick(event) {
    document.querySelectorAll('.modal').forEach((modal) => {
        if (event.target === modal) {
            modal.style.display = 'none';
        }
    });
};
