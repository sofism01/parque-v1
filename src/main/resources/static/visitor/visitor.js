const API_BASE_URL = 'http://localhost:8080/api';
const REFRESH_INTERVAL_MS = 5000;
const POPULARITY_THRESHOLD = 20;
const CLUSTER_DISTANCE_THRESHOLD = 170;
const HOT_CLUSTER_MIN_SIZE = 3;
const CALM_CLUSTER_MAX_AVERAGE = 8;

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
let alertTimeoutHandle = null;
let parkAlertInFlight = false;

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
        await pollVisitorAlert(false);
        renderHistory();
    } catch (error) {
        console.error('[Visitor] Error durante la inicializacion:', error);
        showAppAlert(error.message || 'No fue posible inicializar el panel del visitante.', 'error');
    }

    queueRefreshHandle = window.setInterval(async () => {
        await fetchAtracciones(false);
        await loadQueueStatus(false);
        await pollVisitorAlert(false);
    }, REFRESH_INTERVAL_MS);

    window.addEventListener('resize', () => {
        drawMap();
    });
});

async function initDashboard() {
    await Promise.all([
        loadProfile(),
        loadZones(),
        loadAttractions(),
        initMap()
    ]);
    renderProfile();
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

function toggleSidebar() {
    document.body.classList.toggle('sidebar-open');
}

function closeSidebar() {
    document.body.classList.remove('sidebar-open');
}

function bindMapControls() {
    const originNode = document.getElementById('originNode');
    const destinationNode = document.getElementById('destinationNode');

    if (originNode) {
        originNode.addEventListener('change', (event) => {
            selectedOriginId = parseOptionalNumber(event.target.value);
            setCurrentLocationBadge();
            drawMap();
        });
    }

    if (destinationNode) {
        destinationNode.addEventListener('change', (event) => {
            selectedDestinationId = parseOptionalNumber(event.target.value);
            drawMap();
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

    closeSidebar();

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
        storeVisitorSession(currentUser);
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
        if (currentUser) {
            renderProfile();
        }
        renderAttractionsGrid();
        renderFavoritesGrid();
    } catch (error) {
        console.error('[Visitor] Error cargando atracciones:', error);
        if (showErrors) {
            showAppAlert(`No fue posible cargar atracciones: ${error.message}`, 'error');
        }
    }
}

async function fetchAtracciones(showErrors = true) {
    await loadAttractions(showErrors);
    await loadZones(showErrors);
    await initMap(showErrors);
    syncOpenAttractionModal();
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

    const metrics = ensureMapCanvasSize(mapCanvas);
    const context = ctx || mapCanvas.getContext('2d');
    const nodes = Array.isArray(graphSnapshot.nodes) ? graphSnapshot.nodes : [];
    const edges = Array.isArray(graphSnapshot.edges) ? graphSnapshot.edges : [];
    const positionedNodes = resolveGraphLayout(nodes, mapCanvas);
    const nodeMap = new Map(positionedNodes.map((node) => [node.id, node]));

    context.setTransform(1, 0, 0, 1, 0, 0);
    context.clearRect(0, 0, mapCanvas.width, mapCanvas.height);
    context.setTransform(metrics.ratio, 0, 0, metrics.ratio, 0, 0);
    context.fillStyle = '#f8f9fa';
    context.fillRect(0, 0, metrics.displayWidth, metrics.displayHeight);

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
        context.strokeStyle = highlighted ? '#1f8a70' : '#c7ced6';
        context.lineWidth = highlighted ? 6 : 3;
        context.stroke();

        context.fillStyle = '#374151';
        context.font = '600 13px Poppins, sans-serif';
        context.textAlign = 'center';
        context.textBaseline = 'middle';
        context.fillText(String(edge.weight), (source.drawX + target.drawX) / 2, (source.drawY + target.drawY) / 2 - 6);
    });

    positionedNodes.forEach((node) => {
        if (!isRenderableNode(node)) {
            drawConstructionNode(context, node);
            return;
        }

        context.beginPath();
        context.fillStyle = resolveNodeColor(node.status);
        context.arc(node.drawX, node.drawY, node.id === selectedDestinationId ? 30 : 24, 0, Math.PI * 2);
        context.fill();

        if (node.id === selectedOriginId) {
            context.lineWidth = 5;
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
            if (distance <= 30) {
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
    const originNode = document.getElementById('originNode');

    if (originNode) {
        selectedOriginId = parseOptionalNumber(originNode.value);
    }

    if (destinoId != null) {
        selectedDestinationId = Number(destinoId);
        const destinationNode = document.getElementById('destinationNode');
        if (destinationNode) {
            destinationNode.value = String(destinoId);
        }
    }

    if (!selectedOriginId) {
        showAppAlert('Selecciona tu ubicacion actual para calcular la ruta.', 'error');
        return;
    }

    if (!selectedDestinationId) {
        showAppAlert('Selecciona un destino para calcular la ruta.', 'error');
        return;
    }

    try {
        const route = await apiFetch(`/visitor/path?id=${encodeURIComponent(localStorage.getItem('userId') || '')}&origin=${selectedOriginId}&destination=${selectedDestinationId}`);
        highlightedPath = Array.isArray(route.path) ? route.path.map((node) => node.id) : [];
        setCurrentLocationBadge();
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

    const attraction = attractions.find((item) => item.id === finalAttractionId);
    if (attraction && attraction.status === 'MANTENIMIENTO') {
        showAppAlert('⚠️ Esta atracción se encuentra cerrada por mantenimiento técnico. Disculpe las molestias.', 'error');
        return;
    }
    if (attraction && attraction.status === 'CLIMA') {
        showAppAlert('⛈️ Atracción temporalmente cerrada debido a condiciones climáticas adversas por seguridad.', 'error');
        return;
    }

    try {
        await apiFetch('/fila/unirse', {
            method: 'POST',
            body: JSON.stringify({
                attractionId: finalAttractionId,
                visitorId: Number(localStorage.getItem('userId'))
            })
        });
        closeModal('attractionModal');
        await loadProfile(false);
        await loadQueueStatus(false);
        switchTab('fila');
        showAppAlert('Te has unido a la fila correctamente.', 'success');
    } catch (error) {
        console.error('[Visitor] Error uniendo a fila:', error);
        if (String(error.message || '').includes('mantenimiento tecnico')) {
            showAppAlert('⚠️ Esta atracción se encuentra cerrada por mantenimiento técnico. Disculpe las molestias.', 'error');
            return;
        }
        if (String(error.message || '').includes('condiciones climaticas adversas')) {
            showAppAlert('⛈️ Atracción temporalmente cerrada debido a condiciones climáticas adversas por seguridad.', 'error');
            return;
        }
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

async function pollVisitorAlert(showErrors = true) {
    if (parkAlertInFlight) {
        return;
    }

    const userId = localStorage.getItem('userId');
    if (!userId) {
        return;
    }

    parkAlertInFlight = true;
    try {
        const alertPayload = await apiFetch(`/visitor/alert?id=${encodeURIComponent(userId)}`);
        const message = typeof alertPayload?.message === 'string' ? alertPayload.message.trim() : '';
        if (!message) {
            return;
        }

        window.alert(`⚠️ AVISO DEL PARQUE: ${message}`);
        await apiFetch('/visitor/alert/clear', {
            method: 'POST',
            body: JSON.stringify({
                visitorId: Number(userId)
            })
        });

        if (currentUser) {
            currentUser.mensajeAlerta = null;
        }
    } catch (error) {
        console.error('[Visitor] Error consultando alerta:', error);
        if (showErrors) {
            showAppAlert(`No fue posible consultar alertas: ${error.message}`, 'error');
        }
    } finally {
        parkAlertInFlight = false;
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
    setText('dashboardTicketTitle', resolveAttractionName(currentUser.currentQueueAttractionId) || 'Acceso al parque');
    setCurrentLocationBadge();

    const profileSummary = document.getElementById('profileSummary');
    if (!profileSummary) {
        return;
    }

    profileSummary.innerHTML = `
        <div class="profile-item"><span>Documento</span><strong>${currentUser.document || 'Sin registrar'}</strong></div>
        <div class="profile-item"><span>Edad</span><strong>${currentUser.age || 0} anos</strong></div>
        <div class="profile-item"><span>Estatura</span><strong>${Number(currentUser.height || 0).toFixed(2)} m</strong></div>
        <div class="profile-item"><span>Tipo de ticket</span><strong>${formatTicketType(currentUser.ticketType)}</strong></div>
        <div class="profile-item"><span>Ubicacion actual</span><strong>${resolveAttractionName(currentUser.currentLocationAttractionId)}</strong></div>
        <div class="profile-item"><span>Favoritos</span><strong>${favorites.length}</strong></div>
        <div class="profile-item"><span>Historial</span><strong>${Array.isArray(currentUser.historialAtracciones) ? currentUser.historialAtracciones.length : 0}</strong></div>
        <div class="profile-item"><span>Fila activa</span><strong>${resolveAttractionName(currentUser.currentQueueAttractionId)}</strong></div>
    `;
}

function renderAttractionsGrid() {
    const attractionsGrid = document.getElementById('attractionsGrid');
    if (!attractionsGrid) {
        return;
    }

    if (!attractions.length) {
        attractionsGrid.innerHTML = '<p class="empty-state">No hay atracciones disponibles para tu perfil.</p>';
        return;
    }

    attractionsGrid.innerHTML = attractions.map((attraction) => {
        const isFavorite = favorites.includes(attraction.id);
        const queueDisabled = attraction.status !== 'ABIERTA';
        const queueLabel = queueDisabled ? 'CERRADA' : 'Unirse a la Fila';

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
                    <div class="attraction-meta"><strong>Zona:</strong> ${attraction.nombreZona || attraction.zoneName || attraction.zoneId || '-'}</div>
                    <div class="attraction-meta"><strong>Coordenadas:</strong> ${formatCoordinates(attraction)}</div>
                    <div class="attraction-meta"><strong>Personas en espera:</strong> ${attraction.peopleWaiting ?? 0}</div>
                    <div class="attraction-meta"><strong>Visitantes acumulados:</strong> ${attraction.accumulatedVisitors ?? 0}</div>
                    <div class="attraction-meta"><strong>Estado:</strong> ${attraction.status}</div>
                    <div class="attraction-meta"><span class="wait-badge">${formatWaitTime(attraction)} de espera</span></div>
                    <div class="card-actions">
                        <button class="btn-queue" ${queueDisabled ? 'disabled' : ''} style="${queueDisabled ? 'background-color:#94a3b8;cursor:not-allowed;' : ''}" onclick="showAttractionModal(${attraction.id})">${queueLabel}</button>
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
                <div class="attraction-meta"><span class="wait-badge">${formatWaitTime(attraction)} de espera</span></div>
                <div class="card-actions">
                    <button class="btn-queue" ${attraction.status !== 'ABIERTA' ? 'disabled' : ''} style="${attraction.status !== 'ABIERTA' ? 'background-color:#94a3b8;cursor:not-allowed;' : ''}" onclick="showAttractionModal(${attraction.id})">${attraction.status !== 'ABIERTA' ? 'CERRADA' : 'Unirse'}</button>
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

    const totalInQueue = Number(queueStatus.totalInQueue ?? queueStatus.peopleWaiting ?? 0);
    const position = Number(queueStatus.position ?? 0);
    const progress = totalInQueue > 0 && position > 0
        ? Math.max(8, Math.min(100, ((totalInQueue - position + 1) / totalInQueue) * 100))
        : 100;

    queueInfo.innerHTML = `
        <div class="queue-card">
            <h3>${queueStatus.attractionName}</h3>
            <div class="queue-progress">
                <div class="queue-progress-head">
                    <span>Progreso hacia tu turno</span>
                    <strong>${Math.round(progress)}%</strong>
                </div>
                <div class="progress-track">
                    <div class="progress-bar" style="width: ${progress}%"></div>
                </div>
            </div>
            <div class="queue-grid">
                <div class="queue-row"><span>Posicion actual</span><strong>${queueStatus.position ?? '--'}</strong></div>
                <div class="queue-row"><span>Personas en espera</span><strong>${queueStatus.peopleWaiting ?? queueStatus.totalInQueue ?? '--'}</strong></div>
                <div class="queue-row"><span>Espera estimada</span><strong>${formatWaitTime(queueStatus)}</strong></div>
                <div class="queue-row"><span>Ticket</span><strong>${formatTicketType(queueStatus.ticketType)}</strong></div>
            </div>
            <div class="card-actions"><button class="btn-secondary" onclick="leaveQueue()">Salir de la fila</button></div>
        </div>
    `;

    setText('ticketAttraction', queueStatus.attractionName || 'Fila activa');
    setText('ticketWait', formatWaitTime(queueStatus));
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

    const history = Array.isArray(currentUser.historialAtracciones) ? currentUser.historialAtracciones : [];
    if (!history.length) {
        historyList.innerHTML = '<p class="empty-state">Aun no has visitado ninguna atraccion. Empieza la diversion!</p>';
        return;
    }

    const aggregatedHistory = new Map();
    history.forEach((attractionName) => {
        const normalizedName = String(attractionName || '').trim();
        if (!normalizedName) {
            return;
        }
        aggregatedHistory.set(normalizedName, (aggregatedHistory.get(normalizedName) || 0) + 1);
    });

    historyList.innerHTML = Array.from(aggregatedHistory.entries()).map(([attractionName, count], index) => {
        return `
            <div class="history-item">
                <div><strong>${attractionName}${count > 1 ? ` x${count}` : ''}</strong></div>
                <span>Visita ${index + 1}</span>
            </div>
        `;
    }).join('');
}

function populateNodeSelectors() {
    const originNode = document.getElementById('originNode');
    const destinationNode = document.getElementById('destinationNode');
    const nodes = Array.isArray(graphSnapshot.nodes) ? graphSnapshot.nodes : [];
    const availableNodeIds = new Set(nodes.map((node) => node.id));

    if (!originNode || !destinationNode) {
        return;
    }

    originNode.innerHTML = '<option value="">Selecciona tu ubicacion actual</option>';
    destinationNode.innerHTML = '<option value="">¿A donde quieres ir?</option>';

    nodes.forEach((node) => {
        const optionMarkup = `<option value="${node.id}">${node.name} (${node.status})</option>`;
        originNode.insertAdjacentHTML('beforeend', optionMarkup);
        destinationNode.insertAdjacentHTML('beforeend', optionMarkup);
    });

    if (selectedOriginId != null && !availableNodeIds.has(selectedOriginId)) {
        selectedOriginId = null;
    }

    if (selectedDestinationId != null && !availableNodeIds.has(selectedDestinationId)) {
        selectedDestinationId = null;
    }

    originNode.value = selectedOriginId ?? '';
    destinationNode.value = selectedDestinationId ?? '';
    setCurrentLocationBadge();
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
    const warningElement = document.getElementById('attractionWarning');
    const joinQueueButton = document.getElementById('joinQueueButton');
    if (!attractionDetails) {
        return;
    }

    const requirementCheck = validateAttractionRequirements(attraction);

    attractionDetails.innerHTML = `
        <h2>${attraction.name}</h2>
        <div class="detail-row"><span class="detail-label">Tipo</span><span>${formatAttractionType(attraction.type)}</span></div>
        <div class="detail-row"><span class="detail-label">Estado</span><span>${renderStatusBadge(attraction.status)}</span></div>
        <div class="detail-row"><span class="detail-label">Personas en espera</span><span>${attraction.peopleWaiting ?? 0}</span></div>
        <div class="detail-row"><span class="detail-label">Espera estimada</span><span>${formatWaitTime(attraction)}</span></div>
        <div class="detail-row"><span class="detail-label">Estatura minima</span><span>${Number(resolveMinHeight(attraction)).toFixed(2)} m</span></div>
        <div class="detail-row"><span class="detail-label">Edad minima</span><span>${resolveMinAge(attraction)} anos</span></div>
        <div class="detail-row"><span class="detail-label">Costo adicional</span><span>$${Number(attraction.additionalCost || 0).toFixed(2)}</span></div>
    `;

    if (warningElement) {
        const closedMessage = resolveClosedAttractionMessage(attraction.status);
        if (closedMessage) {
            warningElement.style.display = 'block';
            warningElement.textContent = closedMessage;
        } else if (requirementCheck.allowed) {
            warningElement.style.display = 'none';
            warningElement.textContent = '';
        } else {
            warningElement.style.display = 'block';
            warningElement.textContent = `No cumples con los requisitos: Estatura minima ${Number(resolveMinHeight(attraction)).toFixed(2)}m / Edad minima ${resolveMinAge(attraction)} anos`;
        }
    }

    if (joinQueueButton) {
        const disabled = attraction.status !== 'ABIERTA' || !requirementCheck.allowed;
        joinQueueButton.disabled = disabled;
        joinQueueButton.style.backgroundColor = disabled ? '#94a3b8' : '';
        joinQueueButton.style.cursor = disabled ? 'not-allowed' : '';
        joinQueueButton.textContent = attraction.status !== 'ABIERTA' ? 'CERRADA' : 'Unirse a la Fila';
    }

    document.getElementById('attractionModal').style.display = 'block';
}

function syncOpenAttractionModal() {
    const modal = document.getElementById('attractionModal');
    if (!modal || modal.style.display !== 'block' || !currentAttractionId) {
        return;
    }

    const attraction = attractions.find((item) => item.id === currentAttractionId);
    if (!attraction) {
        closeModal('attractionModal');
        return;
    }

    showAttractionModal(currentAttractionId);
}

function isAllowedForVisitor(attraction) {
    if (!currentUser) {
        return true;
    }
    return Number(currentUser.height || 0) >= resolveMinHeight(attraction)
        && Number(currentUser.age || 0) >= resolveMinAge(attraction);
}

function renderStatusBadge(status) {
    if (status === 'ABIERTA') {
        return '<span class="status-badge status-active">ABIERTA</span>';
    }
    if (status === 'MANTENIMIENTO') {
        return '<span class="status-badge status-warning">MANTENIMIENTO</span>';
    }
    return '<span class="status-badge status-danger">CLIMA</span>';
}

function resolveNodeColor(status) {
    if (status === 'ABIERTA') {
        return '#10b981';
    }
    return '#ef4444';
}

function resolveGraphLayout(nodes, canvas) {
    if (!nodes.length || !canvas) {
        return [];
    }

    const ratio = Math.max(window.devicePixelRatio || 1, 1);
    const displayWidth = Math.max(canvas.clientWidth || Math.round(canvas.width / ratio) || 800, 320);
    const displayHeight = Math.max(canvas.clientHeight || Math.round(canvas.height / ratio) || 600, 320);
    const fallbackX = displayWidth / 2;
    const fallbackY = displayHeight / 2;
    const validNodes = nodes.filter((node) =>
        node && node.posX !== null && node.posY !== null
        && Number.isFinite(Number(node.posX)) && Number.isFinite(Number(node.posY))
    );

    const paddingX = Math.max(displayWidth * 0.08, 90);
    const paddingY = Math.max(displayHeight * 0.1, 90);
    const minX = validNodes.length ? Math.min(...validNodes.map((node) => Number(node.posX))) : 0;
    const maxX = validNodes.length ? Math.max(...validNodes.map((node) => Number(node.posX))) : displayWidth;
    const minY = validNodes.length ? Math.min(...validNodes.map((node) => Number(node.posY))) : 0;
    const maxY = validNodes.length ? Math.max(...validNodes.map((node) => Number(node.posY))) : displayHeight;
    const sourceWidth = Math.max(maxX - minX, 1);
    const sourceHeight = Math.max(maxY - minY, 1);
    const targetWidth = Math.max(displayWidth - (paddingX * 2), 1);
    const targetHeight = Math.max(displayHeight - (paddingY * 2), 1);
    const scaleX = targetWidth / sourceWidth;
    const scaleY = targetHeight / sourceHeight;
    const offsetX = (displayWidth - (sourceWidth * scaleX)) / 2;
    const offsetY = (displayHeight - (sourceHeight * scaleY)) / 2;

    return nodes.map((node) => {
        const hasValidCoordinates = node && node.posX !== null && node.posY !== null
            && Number.isFinite(Number(node.posX)) && Number.isFinite(Number(node.posY));
        return {
            ...node,
            drawX: hasValidCoordinates ? ((Number(node.posX) - minX) * scaleX) + offsetX : fallbackX,
            drawY: hasValidCoordinates ? ((Number(node.posY) - minY) * scaleY) + offsetY : fallbackY,
            hasSpatialData: hasValidCoordinates
        };
    });
}

function drawZoneGroups(context, positionedNodes, zoneMap) {
    return;
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
    context.arc(node.drawX, node.drawY, 22, 0, Math.PI * 2);
    context.fill();
    context.strokeStyle = '#64748b';
    context.lineWidth = 2;
    context.stroke();
    context.fillStyle = '#0f172a';
    context.font = '700 14px Poppins, sans-serif';
    context.textAlign = 'center';
    context.textBaseline = 'middle';
    context.fillText('?', node.drawX, node.drawY + 4);
    context.textBaseline = 'top';
    context.fillText(node.name || 'En construccion', node.drawX, node.drawY + 32);
}

function ensureMapCanvasSize(canvas) {
    if (!canvas) {
        return { displayWidth: 0, displayHeight: 0, ratio: 1 };
    }

    const ratio = Math.max(window.devicePixelRatio || 1, 1);
    const computedHeight = window.innerWidth <= 720 ? 480 : 600;
    const displayWidth = Math.max(Math.floor(canvas.clientWidth || canvas.parentElement?.clientWidth || 800), 320);
    const internalWidth = Math.floor(displayWidth * ratio);
    const internalHeight = Math.floor(computedHeight * ratio);
    canvas.style.width = `${displayWidth}px`;
    canvas.style.height = `${computedHeight}px`;
    if (canvas.width !== internalWidth) {
        canvas.width = internalWidth;
    }
    if (canvas.height !== internalHeight) {
        canvas.height = internalHeight;
    }
    return { displayWidth, displayHeight: computedHeight, ratio };
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

function resolveAttractionName(attractionId) {
    if (!attractionId) {
        return 'Sin registro';
    }
    const attraction = attractions.find((item) => item.id === attractionId);
    return attraction ? attraction.name : `Atraccion #${attractionId}`;
}

function setCurrentLocationBadge() {
    setText('currentLocationBadge', `Inicio: ${selectedOriginId ? resolveAttractionName(selectedOriginId) : '--'}`);
}

function storeVisitorSession(visitor) {
    if (!visitor) {
        return;
    }
    sessionStorage.setItem('visitorProfile', JSON.stringify({
        age: Number(visitor.age || 0),
        height: Number(visitor.height || 0),
        document: visitor.document || '',
        username: visitor.username || ''
    }));
}

function getVisitorSession() {
    const rawProfile = sessionStorage.getItem('visitorProfile');
    if (!rawProfile) {
        return null;
    }

    try {
        return JSON.parse(rawProfile);
    } catch (error) {
        return null;
    }
}

function validateAttractionRequirements(attraction) {
    const visitorProfile = getVisitorSession();
    if (!visitorProfile || !attraction) {
        return { allowed: true };
    }

    return {
        allowed: Number(visitorProfile.height || 0) >= resolveMinHeight(attraction)
            && Number(visitorProfile.age || 0) >= resolveMinAge(attraction)
    };
}

function resolveMinHeight(attraction) {
    return Number(attraction?.estaturaMinima ?? attraction?.minHeight ?? 0);
}

function resolveMinAge(attraction) {
    return Number(attraction?.edadMinima ?? attraction?.minAge ?? 0);
}

function resolveClosedAttractionMessage(status) {
    if (status === 'MANTENIMIENTO') {
        return '⚠️ Esta atracción se encuentra cerrada por mantenimiento técnico. Disculpe las molestias.';
    }
    if (status === 'CLIMA') {
        return '⛈️ Atracción temporalmente cerrada debido a condiciones climáticas adversas por seguridad.';
    }
    return null;
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

    if (alertTimeoutHandle) {
        window.clearTimeout(alertTimeoutHandle);
        alertTimeoutHandle = null;
    }

    appAlert.hidden = false;
    appAlert.className = `app-alert ${type}`;
    appAlert.innerHTML = `${type === 'error' ? '<i class="fas fa-triangle-exclamation"></i>' : '<i class="fas fa-circle-check"></i>'}<span>${message}</span>`;
    alertTimeoutHandle = window.setTimeout(() => {
        hideAppAlert();
    }, 4200);
}

function hideAppAlert() {
    const appAlert = document.getElementById('appAlert');
    if (!appAlert) {
        return;
    }

    if (alertTimeoutHandle) {
        window.clearTimeout(alertTimeoutHandle);
        alertTimeoutHandle = null;
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
    sessionStorage.clear();
    localStorage.clear();
    window.location.href = '../index.html';
}

function normalizeAttraction(attraction) {
    const estado = resolveAttractionState(attraction);
    return {
        ...attraction,
        status: estado,
        estado,
        formattedWaitTime: formatWaitTime(attraction)
    };
}

function formatWaitTime(source) {
    if (source?.formattedWaitTime) {
        return source.formattedWaitTime;
    }

    const waitTimeSeconds = Number(source?.estimatedWaitTime ?? 0);
    return `${waitTimeSeconds} segundos`;
}

function normalizeGraphSnapshot(snapshot) {
    const normalizedNodes = (Array.isArray(snapshot?.nodes) ? snapshot.nodes : []).map((node) => {
        const attraction = resolveAttractionById(node.id);
        const estado = resolveAttractionState(attraction || node);
        return {
            ...node,
            status: estado,
            estado,
            blocked: estado !== 'ABIERTA'
        };
    });

    return {
        ...snapshot,
        nodes: normalizedNodes
    };
}

function resolveAttractionById(attractionId) {
    return attractions.find((item) => item.id === attractionId) || null;
}

function resolveGraphNodeById(attractionId) {
    return (Array.isArray(graphSnapshot.nodes) ? graphSnapshot.nodes : []).find((item) => item.id === attractionId) || null;
}

function resolveAttractionState(attraction) {
    const rawState = attraction?.estado ?? attraction?.status ?? null;
    if (!rawState) {
        return 'CLIMA';
    }
    return String(rawState).trim().toUpperCase();
}

function isAttractionOpen(attraction) {
    return resolveAttractionState(attraction) === 'ABIERTA';
}

async function loadAttractions(showErrors = true) {
    console.log('Cargando atracciones...');
    try {
        attractions = (await apiFetch('/visitor/attractions')).map(normalizeAttraction);
        if (currentUser) {
            renderProfile();
        }
        renderAttractionsGrid();
        renderFavoritesGrid();
    } catch (error) {
        console.error('[Visitor] Error cargando atracciones:', error);
        if (showErrors) {
            showAppAlert(`No fue posible cargar atracciones: ${error.message}`, 'error');
        }
    }
}

async function initDashboard() {
    await loadProfile();
    await loadZones();
    await loadAttractions();
    await initMap();
    renderProfile();
    renderAttractionsGrid();
    renderFavoritesGrid();
    drawMap();
}

async function initMap(showErrors = true) {
    console.log('Cargando mapa...');

    try {
        graphSnapshot = normalizeGraphSnapshot(await apiFetch('/visitor/graph'));
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

    ensureMapCanvasSize(mapCanvas);
    const metrics = ensureMapCanvasSize(mapCanvas);
    const context = ctx || mapCanvas.getContext('2d');
    const nodes = Array.isArray(graphSnapshot.nodes) ? graphSnapshot.nodes : [];
    const edges = Array.isArray(graphSnapshot.edges) ? graphSnapshot.edges : [];
    const positionedNodes = resolveGraphLayout(nodes, mapCanvas);
    const nodeMap = new Map(positionedNodes.map((node) => [node.id, node]));

    context.setTransform(1, 0, 0, 1, 0, 0);
    context.clearRect(0, 0, mapCanvas.width, mapCanvas.height);
    context.setTransform(metrics.ratio, 0, 0, metrics.ratio, 0, 0);
    context.fillStyle = '#f8f9fa';
    context.fillRect(0, 0, metrics.displayWidth, metrics.displayHeight);

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
        context.strokeStyle = highlighted ? '#1f8a70' : '#c7ced6';
        context.lineWidth = highlighted ? 6 : 3;
        context.stroke();

        context.fillStyle = '#374151';
        context.font = '600 13px Poppins, sans-serif';
        context.textAlign = 'center';
        context.textBaseline = 'middle';
        context.fillText(String(edge.weight), (source.drawX + target.drawX) / 2, (source.drawY + target.drawY) / 2 - 6);
    });

    positionedNodes.forEach((node) => {
        if (!isRenderableNode(node)) {
            drawConstructionNode(context, node);
            return;
        }

        context.beginPath();
        context.fillStyle = resolveNodeColor(node.estado);
        context.arc(node.drawX, node.drawY, node.id === selectedDestinationId ? 30 : 24, 0, Math.PI * 2);
        context.fill();
        context.lineWidth = 2;
        context.strokeStyle = '#1f2937';
        context.stroke();

        if (node.id === selectedOriginId) {
            context.lineWidth = 5;
            context.strokeStyle = '#0f172a';
            context.stroke();
        }

        context.fillStyle = '#0f172a';
        context.font = '700 14px Poppins, sans-serif';
        context.textAlign = 'center';
        context.textBaseline = 'top';
        context.fillText(node.name, node.drawX, node.drawY + (node.id === selectedDestinationId ? 40 : 34));
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
            if (distance <= 30) {
                selectedDestinationId = node.id;
                const destinationNode = document.getElementById('destinationNode');
                if (destinationNode) {
                    destinationNode.value = String(node.id);
                }
                const statusLabel = node.blocked ? `cerrada (${node.estado})` : `abierta (${node.estado})`;
                setText('mapStatus', `Destino seleccionado: ${node.name}. Estado actual: ${statusLabel}.`);
                showAttractionModal(node.id);
            }
        });
    };
}

async function calculateRoute(destinoId = null) {
    const originNode = document.getElementById('originNode');

    if (originNode) {
        selectedOriginId = parseOptionalNumber(originNode.value);
    }

    if (destinoId != null) {
        selectedDestinationId = Number(destinoId);
        const destinationNode = document.getElementById('destinationNode');
        if (destinationNode) {
            destinationNode.value = String(destinoId);
        }
    }

    if (!selectedOriginId) {
        showAppAlert('Selecciona tu ubicacion actual para calcular la ruta.', 'error');
        return;
    }

    if (!selectedDestinationId) {
        showAppAlert('Selecciona un destino para calcular la ruta.', 'error');
        return;
    }

    const destination = resolveAttractionById(selectedDestinationId) || resolveGraphNodeById(selectedDestinationId);
    if (destination && !isAttractionOpen(destination)) {
        setText('mapStatus', `Destino no disponible: ${destination.name}.`);
        showAppAlert(
            resolveClosedAttractionMessage(resolveAttractionState(destination))
            || 'La atraccion seleccionada no se encuentra disponible en este momento.',
            'error'
        );
        return;
    }

    try {
        const route = await apiFetch(`/visitor/path?id=${encodeURIComponent(localStorage.getItem('userId') || '')}&origin=${selectedOriginId}&destination=${selectedDestinationId}`);
        highlightedPath = Array.isArray(route.path) ? route.path.map((node) => node.id) : [];
        setCurrentLocationBadge();
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

    const attraction = resolveAttractionById(finalAttractionId);
    if (attraction && !isAttractionOpen(attraction)) {
        showAppAlert(
            resolveClosedAttractionMessage(resolveAttractionState(attraction))
            || 'La atraccion no se encuentra disponible en este momento.',
            'error'
        );
        return;
    }

    try {
        await apiFetch('/fila/unirse', {
            method: 'POST',
            body: JSON.stringify({
                attractionId: finalAttractionId,
                visitorId: Number(localStorage.getItem('userId'))
            })
        });
        closeModal('attractionModal');
        await loadProfile(false);
        await loadQueueStatus(false);
        switchTab('fila');
        showAppAlert('Te has unido a la fila correctamente.', 'success');
    } catch (error) {
        console.error('[Visitor] Error uniendo a fila:', error);
        if (String(error.message || '').includes('mantenimiento tecnico')) {
            showAppAlert(resolveClosedAttractionMessage('MANTENIMIENTO'), 'error');
            return;
        }
        if (String(error.message || '').includes('condiciones climaticas adversas')) {
            showAppAlert(resolveClosedAttractionMessage('CLIMA'), 'error');
            return;
        }
        showAppAlert(`No fue posible unirte a la fila: ${error.message}`, 'error');
    }
}

function renderAttractionsGrid() {
    const attractionsGrid = document.getElementById('attractionsGrid');
    if (!attractionsGrid) {
        return;
    }

    if (!attractions.length) {
        attractionsGrid.innerHTML = '<p class="empty-state">No hay atracciones disponibles para tu perfil.</p>';
        return;
    }

    attractionsGrid.innerHTML = attractions.map((attraction) => {
        const isFavorite = favorites.includes(attraction.id);
        const attractionState = resolveAttractionState(attraction);
        const queueDisabled = !isAttractionOpen(attraction);
        const queueLabel = queueDisabled ? 'CERRADA' : 'Unirse a la Fila';

        return `
            <article class="attraction-card ${queueDisabled ? 'disabled' : ''}">
                <div class="attraction-card-header">
                    <div>
                        <h3>${attraction.name}</h3>
                        <div>${formatAttractionType(attraction.type)}</div>
                    </div>
                    ${renderStatusBadge(attractionState)}
                </div>
                <div class="attraction-card-body">
                    <div class="attraction-meta"><strong>Zona:</strong> ${attraction.nombreZona || attraction.zoneName || attraction.zoneId || '-'}</div>
                    <div class="attraction-meta"><strong>Coordenadas:</strong> ${formatCoordinates(attraction)}</div>
                    <div class="attraction-meta"><strong>Personas en espera:</strong> ${attraction.peopleWaiting ?? 0}</div>
                    <div class="attraction-meta"><strong>Visitantes acumulados:</strong> ${attraction.accumulatedVisitors ?? 0}</div>
                    <div class="attraction-meta"><strong>Estado:</strong> ${attractionState}</div>
                    <div class="attraction-meta"><span class="wait-badge">${formatWaitTime(attraction)} de espera</span></div>
                    <div class="card-actions">
                        <button class="btn-queue" ${queueDisabled ? 'disabled' : ''} style="${queueDisabled ? 'background-color:#94a3b8;cursor:not-allowed;' : ''}" onclick="showAttractionModal(${attraction.id})">${queueLabel}</button>
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
                ${renderStatusBadge(resolveAttractionState(attraction))}
            </div>
            <div class="attraction-card-body">
                <div class="attraction-meta"><span class="wait-badge">${formatWaitTime(attraction)} de espera</span></div>
                <div class="card-actions">
                    <button class="btn-queue" ${!isAttractionOpen(attraction) ? 'disabled' : ''} style="${!isAttractionOpen(attraction) ? 'background-color:#94a3b8;cursor:not-allowed;' : ''}" onclick="showAttractionModal(${attraction.id})">${!isAttractionOpen(attraction) ? 'CERRADA' : 'Unirse'}</button>
                    <button class="btn-favorite" onclick="toggleFavorite(${attraction.id})">Quitar</button>
                </div>
            </div>
        </article>
    `).join('');
}

function populateNodeSelectors() {
    const originNode = document.getElementById('originNode');
    const destinationNode = document.getElementById('destinationNode');
    const nodes = Array.isArray(graphSnapshot.nodes) ? graphSnapshot.nodes : [];
    const availableNodeIds = new Set(nodes.map((node) => node.id));

    if (!originNode || !destinationNode) {
        return;
    }

    originNode.innerHTML = '<option value="">Selecciona tu ubicacion actual</option>';
    destinationNode.innerHTML = '<option value="">¿A donde quieres ir?</option>';

    nodes.forEach((node) => {
        const optionMarkup = `<option value="${node.id}">${node.name} (${node.estado})${node.blocked ? ' [BLOQUEADA]' : ''}</option>`;
        originNode.insertAdjacentHTML('beforeend', optionMarkup);
        destinationNode.insertAdjacentHTML('beforeend', optionMarkup);
    });

    if (selectedOriginId != null && !availableNodeIds.has(selectedOriginId)) {
        selectedOriginId = null;
    }

    if (selectedDestinationId != null && !availableNodeIds.has(selectedDestinationId)) {
        selectedDestinationId = null;
    }

    originNode.value = selectedOriginId ?? '';
    destinationNode.value = selectedDestinationId ?? '';
    setCurrentLocationBadge();
}

function showAttractionModal(attractionId) {
    const attraction = resolveAttractionById(attractionId);
    if (!attraction) {
        return;
    }

    currentAttractionId = attractionId;
    const attractionDetails = document.getElementById('attractionDetails');
    const warningElement = document.getElementById('attractionWarning');
    const joinQueueButton = document.getElementById('joinQueueButton');
    if (!attractionDetails) {
        return;
    }

    const requirementCheck = validateAttractionRequirements(attraction);

    attractionDetails.innerHTML = `
        <h2>${attraction.name}</h2>
        <div class="detail-row"><span class="detail-label">Tipo</span><span>${formatAttractionType(attraction.type)}</span></div>
        <div class="detail-row"><span class="detail-label">Estado</span><span>${renderStatusBadge(resolveAttractionState(attraction))}</span></div>
        <div class="detail-row"><span class="detail-label">Personas en espera</span><span>${attraction.peopleWaiting ?? 0}</span></div>
        <div class="detail-row"><span class="detail-label">Espera estimada</span><span>${formatWaitTime(attraction)}</span></div>
        <div class="detail-row"><span class="detail-label">Estatura minima</span><span>${Number(resolveMinHeight(attraction)).toFixed(2)} m</span></div>
        <div class="detail-row"><span class="detail-label">Edad minima</span><span>${resolveMinAge(attraction)} anos</span></div>
        <div class="detail-row"><span class="detail-label">Costo adicional</span><span>$${Number(attraction.additionalCost || 0).toFixed(2)}</span></div>
    `;

    if (warningElement) {
        const closedMessage = resolveClosedAttractionMessage(resolveAttractionState(attraction));
        if (closedMessage) {
            warningElement.style.display = 'block';
            warningElement.textContent = closedMessage;
        } else if (requirementCheck.allowed) {
            warningElement.style.display = 'none';
            warningElement.textContent = '';
        } else {
            warningElement.style.display = 'block';
            warningElement.textContent = `No cumples con los requisitos: Estatura minima ${Number(resolveMinHeight(attraction)).toFixed(2)}m / Edad minima ${resolveMinAge(attraction)} anos`;
        }
    }

    if (joinQueueButton) {
        const disabled = !isAttractionOpen(attraction) || !requirementCheck.allowed;
        joinQueueButton.disabled = disabled;
        joinQueueButton.style.backgroundColor = disabled ? '#94a3b8' : '';
        joinQueueButton.style.cursor = disabled ? 'not-allowed' : '';
        joinQueueButton.textContent = !isAttractionOpen(attraction) ? 'CERRADA' : 'Unirse a la Fila';
    }

    document.getElementById('attractionModal').style.display = 'block';
}

function resolveNodeColor(status) {
    if (status === 'ABIERTA') {
        return '#10b981';
    }
    return '#ef4444';
}

function resolveClosedAttractionMessage(status) {
    if (status === 'MANTENIMIENTO') {
        return 'Esta atraccion se encuentra cerrada por mantenimiento tecnico. Disculpe las molestias.';
    }
    if (status === 'CLIMA') {
        return 'Atraccion temporalmente cerrada debido a condiciones climaticas adversas por seguridad.';
    }
    return null;
}

window.onclick = function onWindowClick(event) {
    document.querySelectorAll('.modal').forEach((modal) => {
        if (event.target === modal) {
            modal.style.display = 'none';
        }
    });
};

const visitorMapState = window.__visitorMapState || {
    positionedNodes: [],
    nodeMap: new Map(),
    hoveredNodeId: null,
    selectionMode: 'origin',
    tooltipPinned: false,
    crowdClusters: { hotZones: [], calmZones: [], popularNodeIds: new Set() },
    dashOffset: 0,
    animationFrameId: null,
    interactionsBound: false
};
window.__visitorMapState = visitorMapState;

function bindMapControls() {
    const originNode = document.getElementById('originNode');
    const destinationNode = document.getElementById('destinationNode');
    const originPickerBtn = document.getElementById('originPickerBtn');
    const destinationPickerBtn = document.getElementById('destinationPickerBtn');

    if (originNode) {
        originNode.addEventListener('change', (event) => {
            selectedOriginId = parseOptionalNumber(event.target.value);
            if (selectedOriginId) {
                visitorMapState.selectionMode = 'destination';
            }
            syncMapSelectionModeUi();
            setCurrentLocationBadge();
            updateMapSelectionHint();
            drawMap();
        });
    }

    if (destinationNode) {
        destinationNode.addEventListener('change', (event) => {
            selectedDestinationId = parseOptionalNumber(event.target.value);
            syncMapSelectionModeUi();
            updateMapSelectionHint();
            drawMap();
        });
    }

    if (originPickerBtn) {
        originPickerBtn.addEventListener('click', () => {
            visitorMapState.selectionMode = 'origin';
            syncMapSelectionModeUi();
            updateMapSelectionHint();
        });
    }

    if (destinationPickerBtn) {
        destinationPickerBtn.addEventListener('click', () => {
            visitorMapState.selectionMode = 'destination';
            syncMapSelectionModeUi();
            updateMapSelectionHint();
        });
    }
}

async function initMap(showErrors = true) {
    console.log('Cargando mapa...');

    try {
        graphSnapshot = normalizeGraphSnapshot(await apiFetch('/visitor/graph'));
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
    setupMapCanvasInteractions();
    drawMap();
}

function setupMapCanvasInteractions() {
    if (visitorMapState.interactionsBound) {
        return;
    }

    const canvas = document.getElementById('parkMap');
    if (!canvas) {
        return;
    }

    canvas.addEventListener('mousemove', (event) => {
        const node = resolveCanvasNodeAtEvent(event, canvas);
        visitorMapState.hoveredNodeId = node ? node.id : null;
        if (node && !visitorMapState.tooltipPinned) {
            showMapTooltip(node, event.clientX, event.clientY);
        } else if (!node && !visitorMapState.tooltipPinned) {
            hideMapTooltip();
        }
        if (!visitorMapState.animationFrameId) {
            drawMap();
        }
    });

    canvas.addEventListener('mouseleave', () => {
        visitorMapState.hoveredNodeId = null;
        if (!visitorMapState.tooltipPinned) {
            hideMapTooltip();
        }
        if (!visitorMapState.animationFrameId) {
            drawMap();
        }
    });

    canvas.addEventListener('click', (event) => {
        const node = resolveCanvasNodeAtEvent(event, canvas);
        if (!node) {
            hideMapTooltip(true);
            return;
        }

        showMapTooltip(node, event.clientX, event.clientY, true);
        handleMapNodeSelection(node);
    });

    canvas.addEventListener('touchstart', (event) => {
        const touch = event.touches[0];
        if (!touch) {
            return;
        }
        const node = resolveCanvasNodeAtTouch(touch, canvas);
        if (!node) {
            hideMapTooltip(true);
            return;
        }
        showMapTooltip(node, touch.clientX, touch.clientY, true);
        handleMapNodeSelection(node);
    }, { passive: true });

    visitorMapState.interactionsBound = true;
}

function drawMap(ctx = null, canvas = null) {
    const mapCanvas = canvas || document.getElementById('parkMap');
    if (!mapCanvas) {
        console.error('[Visitor] No se encontro el canvas parkMap');
        return;
    }

    const metrics = ensureInteractiveMapCanvasSize(mapCanvas);
    const context = ctx || mapCanvas.getContext('2d');
    const nodes = Array.isArray(graphSnapshot.nodes) ? graphSnapshot.nodes : [];
    const edges = Array.isArray(graphSnapshot.edges) ? graphSnapshot.edges : [];
    const positionedNodes = resolveGraphLayout(nodes, mapCanvas);
    const nodeMap = new Map(positionedNodes.map((node) => [node.id, node]));
    visitorMapState.positionedNodes = positionedNodes;
    visitorMapState.nodeMap = nodeMap;
    visitorMapState.crowdClusters = resolveCrowdClusters(positionedNodes);

    context.setTransform(1, 0, 0, 1, 0, 0);
    context.clearRect(0, 0, mapCanvas.width, mapCanvas.height);
    context.setTransform(metrics.ratio, 0, 0, metrics.ratio, 0, 0);

    drawParkBackground(context, metrics.displayWidth, metrics.displayHeight);
    drawParkZones(context, positionedNodes);
    drawCrowdClusters(context, visitorMapState.crowdClusters);
    drawParkEdges(context, edges, nodeMap);
    drawAnimatedRoute(context, nodeMap);
    drawParkNodes(context, positionedNodes);
    drawSelectionMarkers(context, nodeMap);
}

async function calculateRoute(destinoId = null) {
    const originNode = document.getElementById('originNode');

    if (originNode) {
        selectedOriginId = parseOptionalNumber(originNode.value);
    }

    if (destinoId != null) {
        selectedDestinationId = Number(destinoId);
        const destinationNode = document.getElementById('destinationNode');
        if (destinationNode) {
            destinationNode.value = String(destinoId);
        }
    }

    if (!selectedOriginId) {
        showAppAlert('Selecciona tu ubicacion actual para calcular la ruta.', 'error');
        return;
    }

    if (!selectedDestinationId) {
        showAppAlert('Selecciona un destino para calcular la ruta.', 'error');
        return;
    }

    const destination = resolveAttractionById(selectedDestinationId) || resolveGraphNodeById(selectedDestinationId);
    if (destination && !isAttractionOpen(destination)) {
        setText('mapStatus', `Destino no disponible: ${destination.name}.`);
        showAppAlert(
            resolveClosedAttractionMessage(resolveAttractionState(destination))
            || 'La atraccion seleccionada no se encuentra disponible en este momento.',
            'error'
        );
        stopRouteAnimation();
        return;
    }

    try {
        const route = await apiFetch(`/visitor/path?id=${encodeURIComponent(localStorage.getItem('userId') || '')}&origin=${selectedOriginId}&destination=${selectedDestinationId}`);
        highlightedPath = Array.isArray(route.path) ? route.path.map((node) => node.id) : [];
        setCurrentLocationBadge();
        renderRouteInfo(route);
        updateMapSelectionHint();
        drawMap();
        if (highlightedPath.length >= 2) {
            startRouteAnimation();
        } else {
            stopRouteAnimation();
        }
        hideAppAlert();
    } catch (error) {
        console.error('[Visitor] Error calculando ruta:', error);
        stopRouteAnimation();
        showAppAlert(`No fue posible calcular la ruta: ${error.message}`, 'error');
    }
}

function populateNodeSelectors() {
    const originNode = document.getElementById('originNode');
    const destinationNode = document.getElementById('destinationNode');
    const nodes = Array.isArray(graphSnapshot.nodes) ? graphSnapshot.nodes : [];
    const availableNodeIds = new Set(nodes.map((node) => node.id));

    if (!originNode || !destinationNode) {
        return;
    }

    originNode.innerHTML = '<option value="">Selecciona tu ubicacion actual</option>';
    destinationNode.innerHTML = '<option value="">Selecciona un destino</option>';

    nodes.forEach((node) => {
        const statusText = node.blocked ? ' [NO DISPONIBLE]' : '';
        const optionMarkup = `<option value="${node.id}">${escapeMapHtml(node.name)} (${escapeMapHtml(node.estado)})${statusText}</option>`;
        originNode.insertAdjacentHTML('beforeend', optionMarkup);
        destinationNode.insertAdjacentHTML('beforeend', optionMarkup);
    });

    if (selectedOriginId != null && !availableNodeIds.has(selectedOriginId)) {
        selectedOriginId = null;
    }

    if (selectedDestinationId != null && !availableNodeIds.has(selectedDestinationId)) {
        selectedDestinationId = null;
    }

    originNode.value = selectedOriginId ?? '';
    destinationNode.value = selectedDestinationId ?? '';
    syncMapSelectionModeUi();
    setCurrentLocationBadge();
    updateMapSelectionHint();
}

function setCurrentLocationBadge() {
    setText('currentLocationBadge', `Inicio: ${selectedOriginId ? resolveAttractionName(selectedOriginId) : '--'}`);
}

function renderRouteInfo(route) {
    const routeInfo = document.getElementById('routeInfo');
    const routeList = document.getElementById('routeList');
    if (!routeInfo || !routeList) {
        return;
    }

    routeInfo.hidden = false;
    if (!route.reachable || !Array.isArray(route.pathNames) || !route.pathNames.length) {
        routeList.innerHTML = `<p class="empty-state">${escapeMapHtml(route.message || 'No hay ruta disponible.')}</p>`;
        return;
    }

    routeList.innerHTML = `
        <div class="route-metric">
            <strong>${escapeMapHtml(String(route.totalDistance ?? '--'))}</strong>
            <span>Distancia total estimada</span>
        </div>
        ${route.pathNames.map((name, index) => `<div class="route-step">${index + 1}. ${escapeMapHtml(name)}</div>`).join('')}
    `;
}

function handleMapNodeSelection(node) {
    const isOriginSelection = visitorMapState.selectionMode === 'origin' || !selectedOriginId;

    if (isOriginSelection) {
        selectedOriginId = node.id;
        const originNode = document.getElementById('originNode');
        if (originNode) {
            originNode.value = String(node.id);
        }
        visitorMapState.selectionMode = 'destination';
        setCurrentLocationBadge();
        setText('mapStatus', `Inicio seleccionado: ${node.name}. Ahora elige un destino.`);
    } else {
        selectedDestinationId = node.id;
        const destinationNode = document.getElementById('destinationNode');
        if (destinationNode) {
            destinationNode.value = String(node.id);
        }
        setText('mapStatus', `Destino seleccionado: ${node.name}. Puedes calcular la ruta.`);
    }

    syncMapSelectionModeUi();
    updateMapSelectionHint();
    drawMap();
}

function updateMapSelectionHint() {
    const hint = document.getElementById('mapSelectionHint');
    if (!hint) {
        return;
    }

    if (!selectedOriginId) {
        hint.textContent = 'Primero selecciona un punto de inicio en el mapa o en el selector manual.';
        return;
    }

    if (!selectedDestinationId) {
        hint.textContent = 'Ahora selecciona el destino. Puedes hacerlo tocando un icono del mapa.';
        return;
    }

    hint.textContent = `Ruta lista entre ${resolveAttractionName(selectedOriginId)} y ${resolveAttractionName(selectedDestinationId)}.`;
}

function syncMapSelectionModeUi() {
    const originPickerBtn = document.getElementById('originPickerBtn');
    const destinationPickerBtn = document.getElementById('destinationPickerBtn');

    if (originPickerBtn) {
        originPickerBtn.classList.toggle('active', visitorMapState.selectionMode === 'origin');
    }
    if (destinationPickerBtn) {
        destinationPickerBtn.classList.toggle('active', visitorMapState.selectionMode === 'destination');
    }
}

function ensureInteractiveMapCanvasSize(canvas) {
    if (!canvas) {
        return { displayWidth: 0, displayHeight: 0, ratio: 1 };
    }

    const ratio = Math.max(window.devicePixelRatio || 1, 1);
    const computedHeight = window.innerWidth <= 720 ? Math.max(Math.floor(window.innerHeight * 0.58), 480) : Math.max(Math.floor(window.innerHeight * 0.72), 620);
    const displayWidth = Math.max(Math.floor(canvas.clientWidth || canvas.parentElement?.clientWidth || 800), 320);
    const internalWidth = Math.floor(displayWidth * ratio);
    const internalHeight = Math.floor(computedHeight * ratio);

    canvas.style.width = `${displayWidth}px`;
    canvas.style.height = `${computedHeight}px`;
    if (canvas.width !== internalWidth) {
        canvas.width = internalWidth;
    }
    if (canvas.height !== internalHeight) {
        canvas.height = internalHeight;
    }

    return { displayWidth, displayHeight: computedHeight, ratio };
}

function drawParkBackground(context, width, height) {
    const skyGradient = context.createLinearGradient(0, 0, 0, height);
    skyGradient.addColorStop(0, '#d7f7df');
    skyGradient.addColorStop(0.5, '#a7e0b4');
    skyGradient.addColorStop(1, '#7ac98d');
    context.fillStyle = skyGradient;
    context.fillRect(0, 0, width, height);

    context.fillStyle = 'rgba(255, 255, 255, 0.22)';
    drawBlob(context, width * 0.14, height * 0.18, 110, 70);
    drawBlob(context, width * 0.88, height * 0.22, 96, 58);

    context.fillStyle = 'rgba(56, 189, 248, 0.24)';
    drawBlob(context, width * 0.82, height * 0.78, 128, 84);

    context.strokeStyle = 'rgba(245, 235, 190, 0.9)';
    context.lineWidth = 34;
    context.lineCap = 'round';
    context.beginPath();
    context.moveTo(width * 0.1, height * 0.78);
    context.bezierCurveTo(width * 0.25, height * 0.68, width * 0.34, height * 0.44, width * 0.5, height * 0.48);
    context.bezierCurveTo(width * 0.64, height * 0.52, width * 0.72, height * 0.36, width * 0.88, height * 0.24);
    context.stroke();

    context.strokeStyle = 'rgba(227, 214, 167, 0.92)';
    context.lineWidth = 12;
    context.beginPath();
    context.moveTo(width * 0.18, height * 0.16);
    context.bezierCurveTo(width * 0.24, height * 0.34, width * 0.38, height * 0.36, width * 0.46, height * 0.54);
    context.stroke();
}

function drawParkZones(context, positionedNodes) {
    const groups = new Map();
    positionedNodes.forEach((node) => {
        if (!isRenderableNode(node)) {
            return;
        }
        const key = node.zoneId ?? node.zoneName ?? 'general';
        if (!groups.has(key)) {
            groups.set(key, []);
        }
        groups.get(key).push(node);
    });

    groups.forEach((nodes, key) => {
        if (nodes.length < 2 || key === 'general') {
            return;
        }

        const centerX = nodes.reduce((sum, node) => sum + node.drawX, 0) / nodes.length;
        const centerY = nodes.reduce((sum, node) => sum + node.drawY, 0) / nodes.length;
        const radiusX = Math.max(...nodes.map((node) => Math.abs(node.drawX - centerX))) + 52;
        const radiusY = Math.max(...nodes.map((node) => Math.abs(node.drawY - centerY))) + 44;

        context.save();
        context.globalAlpha = 0.12;
        context.fillStyle = '#ffffff';
        context.beginPath();
        context.ellipse(centerX, centerY, radiusX, radiusY, 0, 0, Math.PI * 2);
        context.fill();
        context.restore();

        context.fillStyle = 'rgba(15, 23, 42, 0.42)';
        context.font = '600 12px Poppins, sans-serif';
        context.textAlign = 'center';
        context.fillText(String(key), centerX, centerY - radiusY - 10);
    });
}

function drawParkEdges(context, edges, nodeMap) {
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
        context.strokeStyle = highlighted ? 'rgba(250, 204, 21, 0.5)' : 'rgba(87, 114, 128, 0.44)';
        context.lineWidth = highlighted ? 12 : 6;
        context.lineCap = 'round';
        context.stroke();

        context.beginPath();
        context.moveTo(source.drawX, source.drawY);
        context.lineTo(target.drawX, target.drawY);
        context.strokeStyle = highlighted ? '#fff59d' : 'rgba(255, 255, 255, 0.46)';
        context.lineWidth = highlighted ? 5 : 2;
        context.stroke();
    });
}

function drawAnimatedRoute(context, nodeMap) {
    if (highlightedPath.length < 2) {
        return;
    }

    context.save();
    context.lineCap = 'round';
    context.lineJoin = 'round';
    context.shadowColor = 'rgba(253, 224, 71, 0.7)';
    context.shadowBlur = 18;

    context.beginPath();
    highlightedPath.forEach((nodeId, index) => {
        const node = nodeMap.get(nodeId);
        if (!node) {
            return;
        }
        if (index === 0) {
            context.moveTo(node.drawX, node.drawY);
        } else {
            context.lineTo(node.drawX, node.drawY);
        }
    });
    context.strokeStyle = 'rgba(249, 115, 22, 0.55)';
    context.lineWidth = 14;
    context.stroke();

    context.beginPath();
    highlightedPath.forEach((nodeId, index) => {
        const node = nodeMap.get(nodeId);
        if (!node) {
            return;
        }
        if (index === 0) {
            context.moveTo(node.drawX, node.drawY);
        } else {
            context.lineTo(node.drawX, node.drawY);
        }
    });
    context.setLineDash([12, 10]);
    context.lineDashOffset = -visitorMapState.dashOffset;
    context.strokeStyle = '#fff9c4';
    context.lineWidth = 6;
    context.stroke();
    context.restore();
}

function drawParkNodes(context, positionedNodes) {
    positionedNodes.forEach((node) => {
        if (!isRenderableNode(node)) {
            drawConstructionNode(context, node);
            return;
        }

        const attraction = resolveAttractionById(node.id) || node;
        const isHovered = visitorMapState.hoveredNodeId === node.id;
        const isOrigin = node.id === selectedOriginId;
        const isDestination = node.id === selectedDestinationId;
        const isPopular = visitorMapState.crowdClusters?.popularNodeIds?.has(node.id);
        const isCalm = isNodeInsideCalmZone(node.id);
        const baseColor = resolveNodeColor(node.estado);
        const icon = resolveNodeIcon(attraction);
        const radius = isOrigin || isDestination ? 30 : (isHovered ? 28 : 24);
        const pulse = 0.5 + (0.5 * Math.sin(Date.now() / 260));

        context.save();
        if (isPopular) {
            context.shadowColor = `rgba(249, 115, 22, ${0.45 + (pulse * 0.35)})`;
            context.shadowBlur = 16 + (pulse * 18);
            context.fillStyle = `rgba(251, 146, 60, ${0.18 + (pulse * 0.18)})`;
            context.beginPath();
            context.arc(node.drawX, node.drawY, radius + 16 + (pulse * 6), 0, Math.PI * 2);
            context.fill();
        } else if (isCalm) {
            context.shadowColor = 'rgba(34, 197, 94, 0.28)';
            context.shadowBlur = 14;
            context.fillStyle = 'rgba(134, 239, 172, 0.14)';
            context.beginPath();
            context.arc(node.drawX, node.drawY, radius + 12, 0, Math.PI * 2);
            context.fill();
        } else {
            context.shadowColor = isHovered ? 'rgba(15, 23, 42, 0.28)' : 'rgba(15, 23, 42, 0.16)';
            context.shadowBlur = isHovered ? 24 : 12;
        }

        context.fillStyle = '#ffffff';
        context.beginPath();
        context.arc(node.drawX, node.drawY, radius + 7, 0, Math.PI * 2);
        context.fill();

        context.fillStyle = baseColor;
        context.beginPath();
        context.arc(node.drawX, node.drawY, radius, 0, Math.PI * 2);
        context.fill();

        context.lineWidth = isOrigin || isDestination ? 4 : 2;
        context.strokeStyle = isOrigin ? '#0f172a' : (isDestination ? '#f97316' : 'rgba(255, 255, 255, 0.88)');
        context.stroke();

        context.fillStyle = '#ffffff';
        context.font = `${isHovered ? '700 20px' : '700 18px'} "Segoe UI Emoji", "Apple Color Emoji", sans-serif`;
        context.textAlign = 'center';
        context.textBaseline = 'middle';
        context.fillText(icon, node.drawX, node.drawY + 1);

        context.fillStyle = '#0f172a';
        context.font = '700 12px Poppins, sans-serif';
        context.textBaseline = 'top';
        context.fillText(node.name, node.drawX, node.drawY + radius + 12);
        context.restore();
    });
}

function drawSelectionMarkers(context, nodeMap) {
    if (selectedOriginId && nodeMap.has(selectedOriginId)) {
        drawMarkerPill(context, nodeMap.get(selectedOriginId).drawX, nodeMap.get(selectedOriginId).drawY - 46, 'INICIO', '#0f172a', '#ffffff');
    }
    if (selectedDestinationId && nodeMap.has(selectedDestinationId)) {
        drawMarkerPill(context, nodeMap.get(selectedDestinationId).drawX, nodeMap.get(selectedDestinationId).drawY - 46, 'FIN', '#f97316', '#fff7ed');
    }
}

function drawMarkerPill(context, x, y, label, background, textColor) {
    context.save();
    context.font = '700 11px Poppins, sans-serif';
    const textWidth = context.measureText(label).width;
    const width = textWidth + 24;
    const height = 26;

    context.fillStyle = background;
    roundRect(context, x - (width / 2), y - (height / 2), width, height, 13);
    context.fill();

    context.fillStyle = textColor;
    context.textAlign = 'center';
    context.textBaseline = 'middle';
    context.fillText(label, x, y);
    context.restore();
}

function resolveCrowdClusters(positionedNodes) {
    const renderableNodes = positionedNodes.filter((node) => isRenderableNode(node));
    const popularNodes = renderableNodes.filter((node) => resolveAttractionCrowdScore(resolveAttractionById(node.id) || node) >= POPULARITY_THRESHOLD);
    const hotZones = resolveNodeGroupsByDistance(popularNodes)
        .filter((group) => group.length >= HOT_CLUSTER_MIN_SIZE)
        .map((group) => buildClusterEnvelope(group, 'hot'));

    const calmCandidates = renderableNodes.filter((node) => resolveAttractionCrowdScore(resolveAttractionById(node.id) || node) < POPULARITY_THRESHOLD);
    const calmZones = resolveNodeGroupsByDistance(calmCandidates)
        .filter((group) => group.length >= HOT_CLUSTER_MIN_SIZE)
        .map((group) => ({
            group,
            averageCrowd: group.reduce((sum, node) => sum + resolveAttractionCrowdScore(resolveAttractionById(node.id) || node), 0) / group.length
        }))
        .filter((entry) => entry.averageCrowd <= CALM_CLUSTER_MAX_AVERAGE)
        .map((entry) => buildClusterEnvelope(entry.group, 'calm'));

    return {
        hotZones,
        calmZones,
        popularNodeIds: new Set(popularNodes.map((node) => node.id))
    };
}

function resolveNodeGroupsByDistance(nodes) {
    const groups = [];
    const visited = new Set();

    nodes.forEach((node) => {
        if (visited.has(node.id)) {
            return;
        }

        const queue = [node];
        const component = [];
        visited.add(node.id);

        while (queue.length) {
            const current = queue.shift();
            component.push(current);

            nodes.forEach((candidate) => {
                if (visited.has(candidate.id)) {
                    return;
                }
                if (resolveNodeDistance(current, candidate) <= CLUSTER_DISTANCE_THRESHOLD) {
                    visited.add(candidate.id);
                    queue.push(candidate);
                }
            });
        }

        groups.push(component);
    });

    return groups;
}

function buildClusterEnvelope(group, type) {
    const centerX = group.reduce((sum, node) => sum + node.drawX, 0) / group.length;
    const centerY = group.reduce((sum, node) => sum + node.drawY, 0) / group.length;
    const maxDistance = Math.max(...group.map((node) => Math.hypot(node.drawX - centerX, node.drawY - centerY)));
    const pulse = 0.5 + (0.5 * Math.sin(Date.now() / 380));

    return {
        type,
        nodes: group,
        centerX,
        centerY,
        radius: maxDistance + 62 + (type === 'hot' ? pulse * 10 : 0)
    };
}

function drawCrowdClusters(context, crowdClusters) {
    if (!crowdClusters) {
        return;
    }

    crowdClusters.calmZones.forEach((zone) => {
        drawClusterZone(context, zone, {
            fill: 'rgba(74, 222, 128, 0.12)',
            stroke: 'rgba(34, 197, 94, 0.45)',
            label: 'Zona tranquila',
            pillBg: '#166534',
            pillText: '#ecfdf5'
        });
    });

    crowdClusters.hotZones.forEach((zone) => {
        drawClusterZone(context, zone, {
            fill: 'rgba(251, 146, 60, 0.16)',
            stroke: 'rgba(239, 68, 68, 0.54)',
            label: 'Zona de alta afluencia',
            pillBg: '#c2410c',
            pillText: '#fff7ed'
        });
    });
}

function drawClusterZone(context, zone, palette) {
    context.save();
    context.fillStyle = palette.fill;
    context.strokeStyle = palette.stroke;
    context.lineWidth = 3;
    context.setLineDash([10, 8]);
    context.beginPath();
    context.arc(zone.centerX, zone.centerY, zone.radius, 0, Math.PI * 2);
    context.fill();
    context.stroke();
    context.restore();

    drawMarkerPill(context, zone.centerX, zone.centerY - zone.radius - 16, palette.label, palette.pillBg, palette.pillText);
}

function resolveAttractionCrowdScore(attraction) {
    return Number(
        attraction?.peopleWaiting
        ?? attraction?.queueSize
        ?? attraction?.totalInQueue
        ?? attraction?.accumulatedVisitors
        ?? 0
    );
}

function resolveNodeDistance(source, target) {
    return Math.hypot(source.drawX - target.drawX, source.drawY - target.drawY);
}

function isNodeInsideCalmZone(nodeId) {
    const calmZones = visitorMapState.crowdClusters?.calmZones || [];
    return calmZones.some((zone) => zone.nodes.some((node) => node.id === nodeId));
}

function startRouteAnimation() {
    stopRouteAnimation();

    const animate = () => {
        visitorMapState.dashOffset = (visitorMapState.dashOffset + 1.2) % 1000;
        drawMap();
        visitorMapState.animationFrameId = window.requestAnimationFrame(animate);
    };

    visitorMapState.animationFrameId = window.requestAnimationFrame(animate);
}

function stopRouteAnimation() {
    if (visitorMapState.animationFrameId) {
        window.cancelAnimationFrame(visitorMapState.animationFrameId);
        visitorMapState.animationFrameId = null;
    }
}

function resolveCanvasNodeAtEvent(event, canvas) {
    const rect = canvas.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;
    return resolveCanvasNodeAtPosition(x, y);
}

function resolveCanvasNodeAtTouch(touch, canvas) {
    const rect = canvas.getBoundingClientRect();
    const x = touch.clientX - rect.left;
    const y = touch.clientY - rect.top;
    return resolveCanvasNodeAtPosition(x, y);
}

function resolveCanvasNodeAtPosition(x, y) {
    return visitorMapState.positionedNodes.find((node) => {
        if (!isRenderableNode(node)) {
            return false;
        }
        const radius = node.id === selectedOriginId || node.id === selectedDestinationId ? 38 : 32;
        return Math.hypot(node.drawX - x, node.drawY - y) <= radius;
    }) || null;
}

function showMapTooltip(node, clientX, clientY, persistent = false) {
    const tooltip = document.getElementById('mapTooltip');
    const stage = document.querySelector('.theme-park-stage');
    if (!tooltip || !stage) {
        return;
    }

    const attraction = resolveAttractionById(node.id) || node;
    const status = resolveAttractionState(attraction || node);
    const media = resolveAttractionImage(attraction);
    const waitTime = resolveTooltipWaitTime(attraction);
    const minHeight = resolveTooltipMinHeight(attraction);
    const entryPrice = resolveTooltipPrice(attraction);
    const attractionType = formatAttractionType(attraction?.type || node.type || 'ATRACCION');
    const mediaMarkup = media
        ? `<img src="${escapeMapHtml(media)}" alt="${escapeMapHtml(node.name)}" class="map-tooltip-image">`
        : `<div class="map-tooltip-media">${resolveNodeIcon(attraction)}</div>`;

    tooltip.hidden = false;
    tooltip.innerHTML = `
        ${mediaMarkup}
        <h4>${escapeMapHtml(node.name)}</h4>
        <div>${renderStatusBadge(status)}</div>
        <p class="map-tooltip-meta">${escapeMapHtml(attractionType)}</p>
        <div class="map-tooltip-row"><span><i class="fas fa-clock"></i> Tiempo de espera</span><strong>${escapeMapHtml(waitTime)}</strong></div>
        <div class="map-tooltip-row"><span><i class="fas fa-ruler-vertical"></i> Altura minima</span><strong>${escapeMapHtml(minHeight)}</strong></div>
        <div class="map-tooltip-row"><span><i class="fas fa-ticket"></i> Costo de entrada</span><strong>${escapeMapHtml(entryPrice)}</strong></div>
        <div class="map-tooltip-row"><span><i class="fas fa-shapes"></i> Categoria</span><strong>${escapeMapHtml(attractionType)}</strong></div>
    `;

    const tooltipRect = tooltip.getBoundingClientRect();
    const stageWidth = stage.clientWidth;
    const stageHeight = stage.clientHeight;
    const anchorX = Number(node.drawX || 0);
    const anchorY = Number(node.drawY || 0);
    let left = anchorX + 22;
    let top = anchorY - tooltipRect.height - 18;

    if (left + tooltipRect.width > stageWidth - 12) {
        left = anchorX - tooltipRect.width - 22;
    }
    if (left < 12) {
        left = 12;
    }
    if (top < 12) {
        top = anchorY + 18;
    }
    if (top + tooltipRect.height > stageHeight - 12) {
        top = Math.max(stageHeight - tooltipRect.height - 12, 12);
    }

    tooltip.style.left = `${left}px`;
    tooltip.style.top = `${Math.max(top, 12)}px`;
    visitorMapState.tooltipPinned = persistent;
    tooltip.dataset.persistent = persistent ? 'true' : 'false';
}

function hideMapTooltip(force = false) {
    const tooltip = document.getElementById('mapTooltip');
    if (!tooltip) {
        return;
    }
    if (!force && tooltip.dataset.persistent === 'true') {
        return;
    }
    visitorMapState.tooltipPinned = false;
    tooltip.dataset.persistent = 'false';
    tooltip.hidden = true;
    tooltip.textContent = '';
}

function resolveTooltipWaitTime(attraction) {
    const rawWaitTime = attraction?.waitTime ?? attraction?.estimatedWaitTime ?? attraction?.formattedWaitTime ?? 0;
    if (typeof rawWaitTime === 'string' && rawWaitTime.trim() !== '') {
        return rawWaitTime;
    }
    return `${Number(rawWaitTime || 0)} segundos`;
}

function resolveTooltipMinHeight(attraction) {
    const minHeight = Number(attraction?.minHeight ?? attraction?.estaturaMinima ?? 0);
    if (!Number.isFinite(minHeight) || minHeight <= 0) {
        return 'Sin restriccion';
    }
    if (minHeight <= 3) {
        return `${Math.round(minHeight * 100)} cm`;
    }
    return `${Math.round(minHeight)} cm`;
}

function resolveTooltipPrice(attraction) {
    const price = Number(attraction?.price ?? attraction?.additionalCost ?? 0);
    return `$${price.toLocaleString('es-CO', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
}

function resolveAttractionImage(attraction) {
    const raw = attraction?.imageUrl || attraction?.photoUrl || attraction?.photoPath || attraction?.image || null;
    if (!raw || typeof raw !== 'string') {
        return null;
    }
    if (raw.startsWith('http://') || raw.startsWith('https://') || raw.startsWith('/')) {
        return raw;
    }
    return null;
}

function resolveNodeIcon(attraction) {
    const reference = `${attraction?.type || ''} ${attraction?.name || ''}`.toUpperCase();
    if (reference.includes('ROLLER') || reference.includes('COASTER') || reference.includes('EXTREMA') || reference.includes('MONTA')) {
        return '🎢';
    }
    if (reference.includes('CARROUSEL') || reference.includes('CAROUSEL') || reference.includes('CARRUSEL')) {
        return '🎠';
    }
    if (reference.includes('WATER') || reference.includes('AQUA') || reference.includes('RIO')) {
        return '🌊';
    }
    if (reference.includes('FOOD') || reference.includes('COMIDA') || reference.includes('RESTAURANT')) {
        return '🍔';
    }
    if (reference.includes('BANO') || reference.includes('BANO') || reference.includes('ASEO') || reference.includes('RESTROOM')) {
        return '🚻';
    }
    if (reference.includes('KIDS') || reference.includes('INFANTIL')) {
        return '🎈';
    }
    return '🎡';
}

function escapeMapHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

function drawBlob(context, x, y, radiusX, radiusY) {
    context.beginPath();
    context.ellipse(x, y, radiusX, radiusY, 0, 0, Math.PI * 2);
    context.fill();
}

function roundRect(context, x, y, width, height, radius) {
    context.beginPath();
    context.moveTo(x + radius, y);
    context.lineTo(x + width - radius, y);
    context.quadraticCurveTo(x + width, y, x + width, y + radius);
    context.lineTo(x + width, y + height - radius);
    context.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);
    context.lineTo(x + radius, y + height);
    context.quadraticCurveTo(x, y + height, x, y + height - radius);
    context.lineTo(x, y + radius);
    context.quadraticCurveTo(x, y, x + radius, y);
    context.closePath();
}

Object.assign(visitorMapState, {
    zoom: visitorMapState.zoom || 1,
    panX: visitorMapState.panX || 0,
    panY: visitorMapState.panY || 0,
    minZoom: 0.5,
    maxZoom: 3,
    isPanning: false,
    hasDragged: false,
    lastPointerX: 0,
    lastPointerY: 0,
    touchMode: null,
    pinchDistance: 0
});

function setupMapCanvasInteractions() {
    if (visitorMapState.interactionsBound) {
        return;
    }

    const canvas = document.getElementById('parkMap');
    const stage = document.querySelector('.theme-park-stage');
    if (!canvas || !stage) {
        return;
    }

    ensureMapViewportControls(stage, canvas);

    canvas.addEventListener('wheel', (event) => {
        event.preventDefault();
        const direction = event.deltaY < 0 ? 1.12 : 0.9;
        applyZoom(direction, event.offsetX, event.offsetY);
    }, { passive: false });

    canvas.addEventListener('mousedown', (event) => {
        visitorMapState.isPanning = true;
        visitorMapState.hasDragged = false;
        visitorMapState.lastPointerX = event.clientX;
        visitorMapState.lastPointerY = event.clientY;
        stage.classList.add('is-panning');
    });

    window.addEventListener('mousemove', (event) => {
        if (visitorMapState.isPanning) {
            const dx = event.clientX - visitorMapState.lastPointerX;
            const dy = event.clientY - visitorMapState.lastPointerY;
            if (Math.abs(dx) > 1 || Math.abs(dy) > 1) {
                visitorMapState.hasDragged = true;
            }
            visitorMapState.panX += dx;
            visitorMapState.panY += dy;
            visitorMapState.lastPointerX = event.clientX;
            visitorMapState.lastPointerY = event.clientY;
            hideMapTooltip(true);
            drawMap();
            return;
        }

        const node = resolveCanvasNodeAtEvent(event, canvas);
        visitorMapState.hoveredNodeId = node ? node.id : null;
        if (node && !visitorMapState.tooltipPinned) {
            showMapTooltip(node, event.clientX, event.clientY);
        } else if (!node && !visitorMapState.tooltipPinned) {
            hideMapTooltip();
        }
        if (!visitorMapState.animationFrameId) {
            drawMap();
        }
    });

    window.addEventListener('mouseup', () => {
        visitorMapState.isPanning = false;
        stage.classList.remove('is-panning');
    });

    canvas.addEventListener('mouseleave', () => {
        visitorMapState.hoveredNodeId = null;
        if (!visitorMapState.tooltipPinned) {
            hideMapTooltip();
        }
        if (!visitorMapState.animationFrameId) {
            drawMap();
        }
    });

    canvas.addEventListener('click', (event) => {
        if (visitorMapState.hasDragged) {
            visitorMapState.hasDragged = false;
            return;
        }

        const node = resolveCanvasNodeAtEvent(event, canvas);
        if (!node) {
            hideMapTooltip(true);
            return;
        }

        showMapTooltip(node, event.clientX, event.clientY, true);
        handleMapNodeSelection(node);
    });

    canvas.addEventListener('touchstart', (event) => {
        const touches = event.touches;
        if (touches.length === 2) {
            visitorMapState.touchMode = 'pinch';
            visitorMapState.pinchDistance = resolveTouchDistance(touches[0], touches[1]);
            visitorMapState.hasDragged = true;
            hideMapTooltip(true);
            return;
        }

        const touch = touches[0];
        if (!touch) {
            return;
        }
        visitorMapState.touchMode = 'pan';
        visitorMapState.hasDragged = false;
        visitorMapState.lastPointerX = touch.clientX;
        visitorMapState.lastPointerY = touch.clientY;
    }, { passive: true });

    canvas.addEventListener('touchmove', (event) => {
        const touches = event.touches;
        if (touches.length === 2) {
            event.preventDefault();
            const newDistance = resolveTouchDistance(touches[0], touches[1]);
            if (visitorMapState.pinchDistance > 0) {
                const scaleFactor = newDistance / visitorMapState.pinchDistance;
                const rect = canvas.getBoundingClientRect();
                const centerX = ((touches[0].clientX + touches[1].clientX) / 2) - rect.left;
                const centerY = ((touches[0].clientY + touches[1].clientY) / 2) - rect.top;
                applyZoom(scaleFactor, centerX, centerY);
            }
            visitorMapState.pinchDistance = newDistance;
            visitorMapState.touchMode = 'pinch';
            return;
        }

        if (visitorMapState.touchMode !== 'pan') {
            return;
        }

        const touch = touches[0];
        if (!touch) {
            return;
        }

        event.preventDefault();
        const dx = touch.clientX - visitorMapState.lastPointerX;
        const dy = touch.clientY - visitorMapState.lastPointerY;
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1) {
            visitorMapState.hasDragged = true;
        }
        visitorMapState.panX += dx;
        visitorMapState.panY += dy;
        visitorMapState.lastPointerX = touch.clientX;
        visitorMapState.lastPointerY = touch.clientY;
        hideMapTooltip(true);
        drawMap();
    }, { passive: false });

    canvas.addEventListener('touchend', (event) => {
        if (visitorMapState.touchMode === 'pinch') {
            visitorMapState.pinchDistance = 0;
        }

        if (!visitorMapState.hasDragged) {
            const touch = event.changedTouches[0];
            if (touch) {
                const node = resolveCanvasNodeAtTouch(touch, canvas);
                if (!node) {
                    hideMapTooltip(true);
                } else {
                    showMapTooltip(node, touch.clientX, touch.clientY, true);
                    handleMapNodeSelection(node);
                }
            }
        }

        visitorMapState.touchMode = null;
        visitorMapState.hasDragged = false;
    });

    visitorMapState.interactionsBound = true;
}

function ensureMapViewportControls(stage, canvas) {
    if (stage.querySelector('.map-viewport-controls')) {
        return;
    }

    const controls = document.createElement('div');
    controls.className = 'map-viewport-controls';
    controls.innerHTML = `
        <button type="button" class="map-viewport-btn" data-action="zoom-in" aria-label="Acercar">+</button>
        <button type="button" class="map-viewport-btn" data-action="zoom-out" aria-label="Alejar">-</button>
        <button type="button" class="map-viewport-btn reset" data-action="reset" aria-label="Restablecer vista">Reset</button>
    `;

    controls.addEventListener('click', (event) => {
        const button = event.target.closest('.map-viewport-btn');
        if (!button) {
            return;
        }

        const action = button.dataset.action;
        const centerX = canvas.clientWidth / 2;
        const centerY = canvas.clientHeight / 2;
        if (action === 'zoom-in') {
            applyZoom(1.15, centerX, centerY);
        } else if (action === 'zoom-out') {
            applyZoom(0.87, centerX, centerY);
        } else {
            resetMapViewport();
        }
    });

    stage.appendChild(controls);
}

function drawMap(ctx = null, canvas = null) {
    const mapCanvas = canvas || document.getElementById('parkMap');
    if (!mapCanvas) {
        console.error('[Visitor] No se encontro el canvas parkMap');
        return;
    }

    const metrics = ensureInteractiveMapCanvasSize(mapCanvas);
    const context = ctx || mapCanvas.getContext('2d');
    const nodes = Array.isArray(graphSnapshot.nodes) ? graphSnapshot.nodes : [];
    const edges = Array.isArray(graphSnapshot.edges) ? graphSnapshot.edges : [];
    const positionedNodes = resolveGraphLayout(nodes, mapCanvas);
    const nodeMap = new Map(positionedNodes.map((node) => [node.id, node]));
    visitorMapState.positionedNodes = positionedNodes;
    visitorMapState.nodeMap = nodeMap;
    visitorMapState.crowdClusters = resolveCrowdClusters(positionedNodes);

    context.setTransform(1, 0, 0, 1, 0, 0);
    context.clearRect(0, 0, mapCanvas.width, mapCanvas.height);

    const scaledRatio = metrics.ratio * visitorMapState.zoom;
    context.setTransform(scaledRatio, 0, 0, scaledRatio, visitorMapState.panX * metrics.ratio, visitorMapState.panY * metrics.ratio);

    drawParkBackground(context, metrics.displayWidth, metrics.displayHeight);
    drawParkZones(context, positionedNodes);
    drawCrowdClusters(context, visitorMapState.crowdClusters);
    drawParkEdges(context, edges, nodeMap);
    drawAnimatedRoute(context, nodeMap);
    drawParkNodes(context, positionedNodes);
    drawSelectionMarkers(context, nodeMap);
}

function drawParkNodes(context, positionedNodes) {
    const zoom = visitorMapState.zoom || 1;
    const showLabels = zoom >= 0.9;

    positionedNodes.forEach((node) => {
        if (!isRenderableNode(node)) {
            drawConstructionNode(context, node);
            return;
        }

        const attraction = resolveAttractionById(node.id) || node;
        const isHovered = visitorMapState.hoveredNodeId === node.id;
        const isOrigin = node.id === selectedOriginId;
        const isDestination = node.id === selectedDestinationId;
        const isPopular = visitorMapState.crowdClusters?.popularNodeIds?.has(node.id);
        const isCalm = isNodeInsideCalmZone(node.id);
        const baseColor = resolveNodeColor(node.estado);
        const icon = resolveNodeIcon(attraction);
        const screenRadius = isOrigin || isDestination ? 30 : (isHovered ? 28 : 24);
        const radius = screenRadius / zoom;
        const pulse = 0.5 + (0.5 * Math.sin(Date.now() / 260));

        context.save();
        if (isPopular) {
            context.shadowColor = `rgba(249, 115, 22, ${0.45 + (pulse * 0.35)})`;
            context.shadowBlur = 16 + (pulse * 18);
            context.fillStyle = `rgba(251, 146, 60, ${0.18 + (pulse * 0.18)})`;
            context.beginPath();
            context.arc(node.drawX, node.drawY, radius + ((16 + (pulse * 6)) / zoom), 0, Math.PI * 2);
            context.fill();
        } else if (isCalm) {
            context.shadowColor = 'rgba(34, 197, 94, 0.28)';
            context.shadowBlur = 14;
            context.fillStyle = 'rgba(134, 239, 172, 0.14)';
            context.beginPath();
            context.arc(node.drawX, node.drawY, radius + (12 / zoom), 0, Math.PI * 2);
            context.fill();
        } else {
            context.shadowColor = isHovered ? 'rgba(15, 23, 42, 0.28)' : 'rgba(15, 23, 42, 0.16)';
            context.shadowBlur = isHovered ? 24 : 12;
        }

        context.fillStyle = '#ffffff';
        context.beginPath();
        context.arc(node.drawX, node.drawY, radius + (7 / zoom), 0, Math.PI * 2);
        context.fill();

        context.fillStyle = baseColor;
        context.beginPath();
        context.arc(node.drawX, node.drawY, radius, 0, Math.PI * 2);
        context.fill();

        context.lineWidth = (isOrigin || isDestination ? 4 : 2) / zoom;
        context.strokeStyle = isOrigin ? '#0f172a' : (isDestination ? '#f97316' : 'rgba(255, 255, 255, 0.88)');
        context.stroke();

        context.fillStyle = '#ffffff';
        context.font = `${(isHovered ? 20 : 18) / zoom}px "Segoe UI Emoji", "Apple Color Emoji", sans-serif`;
        context.textAlign = 'center';
        context.textBaseline = 'middle';
        context.fillText(icon, node.drawX, node.drawY + (1 / zoom));

        if (showLabels) {
            context.fillStyle = '#0f172a';
            context.font = `${12 / zoom}px Poppins, sans-serif`;
            context.textBaseline = 'top';
            context.fillText(node.name, node.drawX, node.drawY + radius + (12 / zoom));
        }
        context.restore();
    });
}

function drawMarkerPill(context, x, y, label, background, textColor) {
    const zoom = visitorMapState.zoom || 1;
    context.save();
    context.font = `${11 / zoom}px Poppins, sans-serif`;
    const textWidth = context.measureText(label).width;
    const width = textWidth + (24 / zoom);
    const height = 26 / zoom;

    context.fillStyle = background;
    roundRect(context, x - (width / 2), y - (height / 2), width, height, 13 / zoom);
    context.fill();

    context.fillStyle = textColor;
    context.textAlign = 'center';
    context.textBaseline = 'middle';
    context.fillText(label, x, y);
    context.restore();
}

function resolveCrowdClusters(positionedNodes) {
    const popularityThreshold = 1;
    const renderableNodes = positionedNodes.filter((node) => isRenderableNode(node));
    const popularNodes = renderableNodes.filter((node) => resolveAttractionCrowdScore(resolveAttractionById(node.id) || node) >= popularityThreshold);
    const hotZones = resolveNodeGroupsByDistance(popularNodes)
        .filter((group) => group.length >= HOT_CLUSTER_MIN_SIZE)
        .map((group) => buildClusterEnvelope(group, 'hot'));

    const calmCandidates = renderableNodes.filter((node) => resolveAttractionCrowdScore(resolveAttractionById(node.id) || node) < popularityThreshold);
    const calmZones = resolveNodeGroupsByDistance(calmCandidates)
        .filter((group) => group.length >= HOT_CLUSTER_MIN_SIZE)
        .map((group) => ({
            group,
            averageCrowd: group.reduce((sum, node) => sum + resolveAttractionCrowdScore(resolveAttractionById(node.id) || node), 0) / group.length
        }))
        .filter((entry) => entry.averageCrowd <= CALM_CLUSTER_MAX_AVERAGE)
        .map((entry) => buildClusterEnvelope(entry.group, 'calm'));

    return {
        hotZones,
        calmZones,
        popularNodeIds: new Set(popularNodes.map((node) => node.id))
    };
}

function resolveAttractionCrowdScore(attraction) {
    const queueSize = Number(attraction?.queueSize ?? attraction?.peopleWaiting ?? attraction?.totalInQueue ?? 0);
    const waitTime = Number(attraction?.waitTime ?? attraction?.estimatedWaitTime ?? 0);
    return queueSize >= 1 || waitTime > 0 ? Math.max(queueSize, 1) : 0;
}

function resolveCanvasNodeAtEvent(event, canvas) {
    const rect = canvas.getBoundingClientRect();
    return resolveCanvasNodeAtPosition(
        (event.clientX - rect.left),
        (event.clientY - rect.top)
    );
}

function resolveCanvasNodeAtTouch(touch, canvas) {
    const rect = canvas.getBoundingClientRect();
    return resolveCanvasNodeAtPosition(
        (touch.clientX - rect.left),
        (touch.clientY - rect.top)
    );
}

function resolveCanvasNodeAtPosition(screenX, screenY) {
    const worldPoint = convertScreenToWorld(screenX, screenY);
    return visitorMapState.positionedNodes.find((node) => {
        if (!isRenderableNode(node)) {
            return false;
        }
        const radius = ((node.id === selectedOriginId || node.id === selectedDestinationId) ? 38 : 32) / (visitorMapState.zoom || 1);
        return Math.hypot(node.drawX - worldPoint.x, node.drawY - worldPoint.y) <= radius;
    }) || null;
}

function convertScreenToWorld(screenX, screenY) {
    const zoom = visitorMapState.zoom || 1;
    return {
        x: (screenX - visitorMapState.panX) / zoom,
        y: (screenY - visitorMapState.panY) / zoom
    };
}

function applyZoom(multiplier, anchorX, anchorY) {
    const currentZoom = visitorMapState.zoom || 1;
    const nextZoom = Math.min(visitorMapState.maxZoom, Math.max(visitorMapState.minZoom, currentZoom * multiplier));
    if (Math.abs(nextZoom - currentZoom) < 0.001) {
        return;
    }

    const worldX = (anchorX - visitorMapState.panX) / currentZoom;
    const worldY = (anchorY - visitorMapState.panY) / currentZoom;
    visitorMapState.zoom = nextZoom;
    visitorMapState.panX = anchorX - (worldX * nextZoom);
    visitorMapState.panY = anchorY - (worldY * nextZoom);
    hideMapTooltip(true);
    drawMap();
}

function resetMapViewport() {
    visitorMapState.zoom = 1;
    visitorMapState.panX = 0;
    visitorMapState.panY = 0;
    hideMapTooltip(true);
    drawMap();
}

function resolveTouchDistance(firstTouch, secondTouch) {
    return Math.hypot(firstTouch.clientX - secondTouch.clientX, firstTouch.clientY - secondTouch.clientY);
}
