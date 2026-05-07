const API_BASE_URL = 'http://localhost:8080/api';
const REFRESH_INTERVAL_MS = 5000;

let currentUser = null;
let attractions = [];
let zones = [];
let favorites = [];
let graphSnapshot = { nodes: [], edges: [], zones: [] };
let highlightedPath = [];
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
    const destinationNode = document.getElementById('destinationNode');

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

        if (node.id === currentUser?.currentLocationAttractionId) {
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
    if (destinoId != null) {
        selectedDestinationId = Number(destinoId);
        const destinationNode = document.getElementById('destinationNode');
        if (destinationNode) {
            destinationNode.value = String(destinoId);
        }
    }

    if (!selectedDestinationId) {
        showAppAlert('Selecciona un destino para calcular la ruta.', 'error');
        return;
    }

    try {
        const route = await apiFetch(`/visitor/path?id=${encodeURIComponent(localStorage.getItem('userId') || '')}&destination=${selectedDestinationId}`);
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
    const destinationNode = document.getElementById('destinationNode');
    const nodes = Array.isArray(graphSnapshot.nodes) ? graphSnapshot.nodes : [];
    const activeNodes = nodes.filter((node) => node.status === 'ABIERTA');

    if (!destinationNode) {
        return;
    }

    destinationNode.innerHTML = '<option value="">Selecciona destino</option>';

    nodes.forEach((node) => {
        destinationNode.insertAdjacentHTML('beforeend', `<option value="${node.id}">${node.name} (${node.status})</option>`);
    });

    if (!selectedDestinationId && activeNodes.length > 1) {
        selectedDestinationId = activeNodes[1].id;
    }

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
    setText('currentLocationBadge', `Ubicacion actual: ${resolveAttractionName(currentUser?.currentLocationAttractionId)}`);
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

        if (node.id === currentUser?.currentLocationAttractionId) {
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
    if (destinoId != null) {
        selectedDestinationId = Number(destinoId);
        const destinationNode = document.getElementById('destinationNode');
        if (destinationNode) {
            destinationNode.value = String(destinoId);
        }
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
        const route = await apiFetch(`/visitor/path?id=${encodeURIComponent(localStorage.getItem('userId') || '')}&destination=${selectedDestinationId}`);
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
    const destinationNode = document.getElementById('destinationNode');
    const nodes = Array.isArray(graphSnapshot.nodes) ? graphSnapshot.nodes : [];
    const activeNodes = nodes.filter((node) => !node.blocked);

    if (!destinationNode) {
        return;
    }

    destinationNode.innerHTML = '<option value="">Selecciona destino</option>';

    nodes.forEach((node) => {
        destinationNode.insertAdjacentHTML('beforeend', `<option value="${node.id}">${node.name} (${node.estado})${node.blocked ? ' [BLOQUEADA]' : ''}</option>`);
    });

    if (!selectedDestinationId && activeNodes.length > 1) {
        selectedDestinationId = activeNodes[1].id;
    }

    destinationNode.value = selectedDestinationId ?? '';
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
