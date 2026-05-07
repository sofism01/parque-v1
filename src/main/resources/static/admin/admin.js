const API_BASE_URL = 'http://localhost:8080/api';
const DEV_TOKEN = 'dev-admin-token';
const ADMIN_REFRESH_INTERVAL_MS = 5000;

let attractions = [];
let zones = [];
let operators = [];
let graphSnapshot = { nodes: [], edges: [], zones: [] };
let editingAttractionId = null;
let currentQueueInfoAttractionId = null;
let adminRefreshHandle = null;
let adminRefreshInFlight = false;
let renderedGraphNodes = [];

document.addEventListener('DOMContentLoaded', async () => {
    ensureDevSession();
    loadUsername();
    initNavigation();
    initAdminMapInteractions();
    await initializeDashboard();
    startAdminPolling();
});

window.addEventListener('beforeunload', () => {
    if (adminRefreshHandle != null) {
        window.clearInterval(adminRefreshHandle);
        adminRefreshHandle = null;
    }
});

function ensureDevSession() {
    if (!localStorage.getItem('token')) {
        localStorage.setItem('token', DEV_TOKEN);
        localStorage.setItem('username', localStorage.getItem('username') || 'admin');
        localStorage.setItem('role', 'ADMIN');
    }
}

async function initializeDashboard() {
    await loadData();
    await Promise.all([
        loadZones(),
        loadOperators(),
        loadGraph(),
        loadDashboard()
    ]);
}

async function loadData() {
    try {
        const response = await fetch('/api/data');
        if (!response.ok) {
            throw new Error('No fue posible leer data.json');
        }

        const data = await response.json();
        attractions = Array.isArray(data.attractions) ? data.attractions.map(normalizeAdminAttraction) : [];
        zones = Array.isArray(data.zones) ? data.zones : [];
        operators = Array.isArray(data.operators) ? data.operators : [];
        renderAttractionsTable();
        renderZonesGrid();
        renderOperatorsTable();
        populateZoneSelectors();
    } catch (error) {
        showAlert(`No fue posible cargar los datos base: ${error.message}`, 'danger');
        await loadAttractions();
    }
}

function loadUsername() {
    const usernameNode = document.getElementById('username');
    if (usernameNode) {
        usernameNode.textContent = localStorage.getItem('username') || 'Administrador';
    }
}

function initNavigation() {
    const navLinks = document.querySelectorAll('.nav-link');
    navLinks.forEach((link) => {
        link.addEventListener('click', (event) => {
            const href = link.getAttribute('href');
            if (!href || !href.startsWith('#')) {
                return;
            }

            event.preventDefault();
            document.querySelectorAll('.section').forEach((section) => section.classList.remove('active'));
            navLinks.forEach((navLink) => navLink.classList.remove('active'));

            const target = document.querySelector(href);
            if (target) {
                target.classList.add('active');
            }
            link.classList.add('active');
        });
    });
}

async function apiFetch(path, options = {}) {
    const response = await fetch(`${API_BASE_URL}${path}`, {
        mode: 'cors',
        cache: 'no-store',
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

async function loadDashboard() {
    try {
        const report = await apiFetch('/reports/latest');
        setText('totalVisitors', report.totalVisitors ?? 0);
        setText('dailyRevenue', `$${Number(report.dailyRevenue || 0).toFixed(2)}`);
        setText('activeAttractions', attractions.filter((attraction) => isAttractionOpen(attraction)).length);

        const maintenanceAlerts = Array.isArray(report.maintenanceAlerts) ? report.maintenanceAlerts.length : 0;
        const weatherClosures = Array.isArray(report.weatherClosures) ? report.weatherClosures.length : 0;
        setText('alertCount', maintenanceAlerts + weatherClosures);
    } catch (error) {
        showAlert(`No fue posible cargar el dashboard: ${error.message}`, 'danger');
    }
}

async function loadAttractions() {
    try {
        attractions = (await apiFetch('/admin/attractions')).map(normalizeAdminAttraction);
        renderAttractionsTable();
    } catch (error) {
        showAlert(`No fue posible cargar atracciones: ${error.message}`, 'danger');
    }
}

function renderAttractionsTable() {
    const tbody = document.getElementById('attractionsTableBody');
    if (!tbody) {
        return;
    }

    tbody.innerHTML = '';

    attractions.forEach((attraction) => {
        const needsMaintenance = Number(attraction.accumulatedVisitors || 0) >= 500;
        const row = document.createElement('tr');
        if (needsMaintenance) {
            row.classList.add('alert-row');
        }

        const maintenanceAlert = needsMaintenance
            ? '<div class="maintenance-note">Mantenimiento preventivo requerido</div>'
            : '';

        row.innerHTML = `
            <td>${attraction.name}</td>
            <td>${attraction.type}</td>
            <td>${attraction.accumulatedVisitors}${maintenanceAlert}</td>
            <td><span class="badge ${getStatusBadge(resolveAttractionState(attraction))}">${resolveAttractionState(attraction)}</span></td>
            <td>${attraction.formattedWaitTime || '0 segundos'}</td>
            <td>${Number(attraction.totalFila || 0)}</td>
            <td>
                <button class="btn-secondary" onclick="showQueueInfo(${attraction.id})">Ver Info</button>
                ${needsMaintenance ? `<button class="btn-primary" onclick="repairAttraction(${attraction.id})">Reparar</button>` : ''}
                ${!isAttractionOpen(attraction) ? `<button class="btn-primary" onclick="reopenAttraction(${attraction.id})">Abrir Atraccion</button>` : ''}
                <button class="btn-secondary" onclick="prepararEdicion(${attraction.id})">Editar</button>
                <button class="btn-danger" onclick="deleteAttraction(${attraction.id})">Eliminar</button>
            </td>
        `;
        tbody.appendChild(row);
    });
}

async function loadZones() {
    try {
        zones = await apiFetch('/attractions/zones');
        renderZonesGrid();
        populateZoneSelectors();
    } catch (error) {
        showAlert(`No fue posible cargar zonas: ${error.message}`, 'danger');
    }
}

function renderZonesGrid() {
    const grid = document.getElementById('zonesGrid');
    if (!grid) {
        return;
    }

    grid.innerHTML = '';

    zones.filter(isValidZone).forEach((zone) => {
        const occupancy = Number(zone.currentOccupancy ?? 0);
        const maxCapacity = Number(zone.maxCapacity ?? 0);
        const occupancyPercent = maxCapacity > 0 ? Math.min((occupancy / maxCapacity) * 100, 100) : 0;
        const operatorCount = Array.isArray(zone.operatorIds) ? zone.operatorIds.length : 0;
        const attractionCount = Array.isArray(zone.attractionIds) ? zone.attractionIds.length : 0;

        const card = document.createElement('div');
        card.className = 'zone-card';
        card.id = `zone-card-${zone.id}`;
        card.innerHTML = `
            <h3>${zone.name}</h3>
            <div class="zone-info">
                <p><strong>Ocupacion:</strong> ${occupancy}/${maxCapacity}</p>
                <div class="zone-progress">
                    <div class="zone-progress-bar" style="width:${occupancyPercent}%"></div>
                </div>
                <p><strong>Operadores:</strong> ${operatorCount}</p>
                <p><strong>Atracciones:</strong> ${attractionCount}</p>
                ${Number(zone.id) !== 0 ? `<button class="btn-danger" onclick="deleteZone(${zone.id})">Eliminar</button>` : ''}
            </div>
        `;
        grid.appendChild(card);
    });
}

async function loadOperators() {
    try {
        operators = await apiFetch('/admin/operators');
        renderOperatorsTable();
    } catch (error) {
        showAlert(`No fue posible cargar operadores: ${error.message}`, 'danger');
    }
}

function renderOperatorsTable() {
    const tbody = document.getElementById('operatorsTableBody');
    if (!tbody) {
        return;
    }

    tbody.innerHTML = '';

    operators.forEach((operator) => {
        const attractionNames = Array.isArray(operator.assignedAttractionNames)
            ? operator.assignedAttractionNames.join(', ')
            : '';
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${operator.username}</td>
            <td>${operator.email || '-'}</td>
            <td>${operator.zoneName || operator.zoneId || '-'}</td>
            <td>${attractionNames || 'Sin asignar'}</td>
            <td><span class="badge ${operator.active ? 'badge-success' : 'badge-danger'}">${operator.active ? 'Activo' : 'Inactivo'}</span></td>
        `;
        tbody.appendChild(row);
    });
}

async function loadGraph() {
    try {
        graphSnapshot = normalizeGraphSnapshot(await apiFetch('/admin/graph'));
        drawParkMap();
    } catch (error) {
        showAlert(`No fue posible cargar el grafo: ${error.message}`, 'danger');
    }
}

function drawParkMap() {
    const canvas = document.getElementById('parkMap');
    if (!canvas) {
        return;
    }

    const ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const gradient = ctx.createLinearGradient(0, 0, canvas.width, canvas.height);
    gradient.addColorStop(0, '#f8fafc');
    gradient.addColorStop(1, '#dbeafe');
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    const nodes = Array.isArray(graphSnapshot.nodes) ? graphSnapshot.nodes : [];
    const edges = Array.isArray(graphSnapshot.edges) ? graphSnapshot.edges : [];
    const positionedNodes = resolveGraphLayout(nodes, canvas);
    renderedGraphNodes = positionedNodes;
    const nodeMap = new Map(positionedNodes.map((node) => [node.id, node]));

    ctx.strokeStyle = '#94a3b8';
    ctx.lineWidth = 2;
    edges.forEach((edge) => {
        const source = nodeMap.get(edge.sourceId);
        const target = nodeMap.get(edge.targetId);
        if (!source || !target) {
            return;
        }

        ctx.beginPath();
        ctx.moveTo(source.drawX, source.drawY);
        ctx.lineTo(target.drawX, target.drawY);
        ctx.stroke();

        const midX = (source.drawX + target.drawX) / 2;
        const midY = (source.drawY + target.drawY) / 2;
        ctx.fillStyle = '#334155';
        ctx.font = '12px sans-serif';
        ctx.fillText(String(edge.weight), midX + 4, midY - 4);
    });

    positionedNodes.forEach((node) => {
        ctx.beginPath();
        ctx.fillStyle = resolveNodeColor(resolveAttractionState(node));
        ctx.arc(node.drawX, node.drawY, 18, 0, Math.PI * 2);
        ctx.fill();

        ctx.lineWidth = 2;
        ctx.strokeStyle = '#0f172a';
        ctx.stroke();

        ctx.fillStyle = '#0f172a';
        ctx.font = 'bold 12px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText(node.name, node.drawX, node.drawY + 34);
    });
}

function resolveGraphLayout(nodes, canvas) {
    if (nodes.length === 0) {
        return [];
    }

    const fallbackRadius = Math.min(canvas.width, canvas.height) / 2.5;
    const centerX = canvas.width / 2;
    const centerY = canvas.height / 2;

    return nodes.map((node, index) => {
        const angle = (Math.PI * 2 * index) / nodes.length;
        const fallbackX = centerX + Math.cos(angle) * fallbackRadius;
        const fallbackY = centerY + Math.sin(angle) * fallbackRadius;

        return {
            ...node,
            drawX: Number.isFinite(node.posX) ? node.posX + 40 : fallbackX,
            drawY: Number.isFinite(node.posY) ? node.posY + 40 : fallbackY
        };
    });
}

function resolveNodeColor(status) {
    if (status === 'ABIERTA') {
        return '#28a745';
    }
    if (status === 'MANTENIMIENTO') {
        return '#f59e0b';
    }
    return '#dc3545';
}

function initAdminMapInteractions() {
    const canvas = document.getElementById('parkMap');
    if (!canvas) {
        return;
    }

    canvas.addEventListener('click', (event) => {
        const rect = canvas.getBoundingClientRect();
        const x = event.clientX - rect.left;
        const y = event.clientY - rect.top;

        const selectedNode = renderedGraphNodes.find((node) => {
            const distance = Math.hypot(node.drawX - x, node.drawY - y);
            return distance <= 18;
        });

        if (selectedNode) {
            prepararEdicion(selectedNode.id);
        }
    });
}

function startAdminPolling() {
    if (adminRefreshHandle != null) {
        window.clearInterval(adminRefreshHandle);
    }

    adminRefreshHandle = window.setInterval(() => {
        fetchAtraccionesAdmin(false);
    }, ADMIN_REFRESH_INTERVAL_MS);
}

async function fetchAtraccionesAdmin(showErrors = true) {
    if (adminRefreshInFlight) {
        return;
    }

    adminRefreshInFlight = true;
    try {
        const latestAttractions = (await apiFetch('/admin/attractions')).map(normalizeAdminAttraction);
        attractions = mergeAttractionsState(attractions, latestAttractions);
        graphSnapshot = mergeGraphNodeStates(graphSnapshot, latestAttractions);
        renderAttractionsTable();
        syncQueueInfoModal();
        setText('activeAttractions', attractions.filter((attraction) => isAttractionOpen(attraction)).length);
        drawParkMap();
        await loadDashboard();
    } catch (error) {
        if (showErrors) {
            showAlert(`No fue posible actualizar atracciones en tiempo real: ${error.message}`, 'danger');
        }
    } finally {
        adminRefreshInFlight = false;
    }
}

async function generateDailyReport() {
    try {
        const today = new Date().toISOString().split('T')[0];
        const report = await apiFetch(`/reports/generate?date=${today}`, { method: 'POST' });
        displayReport(report);
    } catch (error) {
        showAlert(`No fue posible generar el reporte: ${error.message}`, 'danger');
    }
}

function displayReport(report) {
    const container = document.getElementById('reportContainer');
    if (!container) {
        return;
    }

    let html = `
        <div class="report-section">
            <h3>Resumen del Dia - ${report.reportDate}</h3>
            <div class="report-stats">
                <div class="report-stat">
                    <div class="label">Visitantes Totales</div>
                    <div class="value">${report.totalVisitors ?? 0}</div>
                </div>
                <div class="report-stat">
                    <div class="label">Ingresos Diarios</div>
                    <div class="value">$${Number(report.dailyRevenue || 0).toFixed(2)}</div>
                </div>
                <div class="report-stat">
                    <div class="label">Capacidad del Parque</div>
                    <div class="value">${report.capacityPercentage ?? 0}%</div>
                </div>
            </div>
        </div>
    `;

    if (report.mostVisitedAttractions && Object.keys(report.mostVisitedAttractions).length > 0) {
        html += '<div class="report-section"><h3>Atracciones Mas Visitadas</h3><ul>';
        Object.entries(report.mostVisitedAttractions).forEach(([name, count]) => {
            html += `<li>${name}: ${count} visitantes</li>`;
        });
        html += '</ul></div>';
    }

    if (Array.isArray(report.weatherClosures) && report.weatherClosures.length > 0) {
        html += '<div class="report-section"><h3>Cierres por Clima</h3><ul>';
        report.weatherClosures.forEach((closure) => {
            html += `<li>${closure}</li>`;
        });
        html += '</ul></div>';
    }

    if (Array.isArray(report.maintenanceAlerts) && report.maintenanceAlerts.length > 0) {
        html += '<div class="report-section"><h3>Alertas de Mantenimiento</h3><ul>';
        report.maintenanceAlerts.forEach((alertMessage) => {
            html += `<li>${alertMessage}</li>`;
        });
        html += '</ul></div>';
    }

    container.innerHTML = html;
}

async function triggerWeatherAlert() {
    try {
        await apiFetch('/attractions/close-by-weather', {
            method: 'POST',
            body: JSON.stringify({ alert: 'TORMENTA_ELECTRICA' })
        });
        showAlert('Atracciones cerradas por clima', 'danger');
        await refreshAfterMutation({ reloadOperators: false });
    } catch (error) {
        showAlert(`No fue posible activar la alerta climatica: ${error.message}`, 'danger');
    }
}

async function triggerMaintenanceCheck() {
    try {
        const needsMaintenance = await apiFetch('/attractions/check-maintenance', { method: 'POST' });
        if (Array.isArray(needsMaintenance) && needsMaintenance.length > 0) {
            showAlert(`${needsMaintenance.length} atraccion(es) pasaron a mantenimiento`, 'warning');
        } else {
            showAlert('No hay nuevas atracciones que requieran mantenimiento', 'success');
        }
        await Promise.all([loadAttractions(), loadGraph(), loadDashboard()]);
    } catch (error) {
        showAlert(`No fue posible revisar mantenimiento: ${error.message}`, 'danger');
    }
}

function showAddAttractionForm() {
    const form = document.getElementById('attractionForm');
    if (form) {
        form.reset();
    }
    editingAttractionId = null;
    setAttractionEditId('');
    const zoneSelector = document.getElementById('attractionZoneId');
    if (zoneSelector) {
        zoneSelector.disabled = false;
    }
    const statusSelector = document.getElementById('attractionStatus');
    if (statusSelector) {
        statusSelector.value = 'ACTIVA';
    }
    const modalTitle = document.getElementById('attractionModalTitle');
    if (modalTitle) {
        modalTitle.textContent = 'Nueva Atraccion';
    }
    const submitButton = document.getElementById('attractionSubmitButton');
    if (submitButton) {
        submitButton.textContent = 'Guardar';
    }
    const hint = document.getElementById('attractionCoordinateHint');
    if (hint) {
        hint.textContent = 'La ubicacion se asigna automaticamente segun la zona seleccionada.';
    }
    openModal('attractionModal');
}

async function showAddZoneForm() {
    await loadAvailableOperators();
    openModal('zoneModal');
}

function showAddOperatorForm() {
    openModal('operatorModal');
}

function showQueueInfo(attractionId) {
    currentQueueInfoAttractionId = Number(attractionId);
    syncQueueInfoModal();
    openModal('queueInfoModal');
}

function syncQueueInfoModal() {
    if (currentQueueInfoAttractionId == null) {
        return;
    }

    const attraction = attractions.find((item) => Number(item.id) === Number(currentQueueInfoAttractionId));
    if (!attraction) {
        currentQueueInfoAttractionId = null;
        return;
    }

    setText('queueInfoModalTitle', `Fila de ${attraction.name || 'Atraccion'}`);
    setText('queueInfoSummary', `Personas en fila: ${Number(attraction.totalFila || 0)}`);
    setText(
        'queueInfoBreakdown',
        `Fast Pass: ${Number(attraction.conteoFastPass || 0)} | Familiar: ${Number(attraction.conteoFamiliar || 0)} | General: ${Number(attraction.conteoGeneral || 0)}`
    );
}

function openModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.style.display = 'block';
    }
}

function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.style.display = 'none';
    }
    if (modalId === 'queueInfoModal') {
        currentQueueInfoAttractionId = null;
    }
}

async function saveAttraction(event) {
    event.preventDefault();
    const editIdRaw = document.getElementById('edit-id')?.value?.trim() || '';
    const editId = parseIntegerOrNull(editIdRaw);
    const currentAttraction = editId != null
        ? attractions.find((item) => Number(item.id) === editId) || null
        : null;

    const name = document.getElementById('attractionName').value.trim();
    const type = document.getElementById('attractionType').value.trim();
    const capacity = parseFloat(document.getElementById('attractionCapacity').value);
    const minHeight = parseFloat(document.getElementById('attractionMinHeight').value);
    const minAge = parseFloat(document.getElementById('attractionMinAge').value);
    const additionalCost = parseFloat(document.getElementById('attractionCost').value);

    if (name === '' || type === '') {
        showAlert('Error: Nombre y Tipo de Mecanismo son obligatorios', 'danger');
        return;
    }

    if ([capacity, minHeight, minAge, additionalCost].some((value) => Number.isFinite(value) && value < 0)) {
        showAlert('Error: No se permiten valores negativos', 'danger');
        return;
    }

    const attractionData = sanitizeAttractionPayload({
        name: normalizeTextOrFallback(name, currentAttraction?.name ?? null),
        type: normalizeTextOrFallback(type, currentAttraction?.type ?? null),
        maxCapacityPerCycle: parseIntegerOrFallback(document.getElementById('attractionCapacity').value, currentAttraction?.maxCapacityPerCycle ?? null),
        minHeight: parseDecimalOrFallback(document.getElementById('attractionMinHeight').value, currentAttraction?.minHeight ?? null),
        minAge: parseIntegerOrFallback(document.getElementById('attractionMinAge').value, currentAttraction?.minAge ?? null),
        additionalCost: parseDecimalOrFallback(document.getElementById('attractionCost').value, currentAttraction?.additionalCost ?? null),
        zoneId: parseIntegerOrFallback(document.getElementById('attractionZoneId').value, currentAttraction?.zoneId ?? null),
        status: normalizeTextOrFallback(document.getElementById('attractionStatus').value, currentAttraction?.status ?? null)
    });

    try {
        if (editId != null) {
            await apiFetch(`/admin/attractions/${editId}`, {
                method: 'PUT',
                body: JSON.stringify(attractionData)
            });
        } else {
            await apiFetch('/attractions', {
                method: 'POST',
                body: JSON.stringify({
                    ...attractionData,
                    posX: null,
                    posY: null,
                    accumulatedVisitors: 0,
                    estimatedWaitTime: 0,
                    closureReason: 'NINGUNO'
                })
            });
        }
        document.getElementById('attractionForm').reset();
        editingAttractionId = null;
        setAttractionEditId('');
        closeModal('attractionModal');
        showAlert('Atraccion guardada exitosamente', 'success');
        await loadAttractions();
        await Promise.all([loadZones(), loadGraph(), loadDashboard(), loadOperators()]);
    } catch (error) {
        showAlert(`No fue posible guardar la atraccion: ${error.message}`, 'danger');
    }
}

async function saveZone(event) {
    event.preventDefault();

    const zone = {
        name: document.getElementById('zoneName').value.trim(),
        maxCapacity: Number(document.getElementById('zoneCapacity').value),
        operatorId: parseIntegerOrNull(document.getElementById('operator-select').value),
        currentOccupancy: 0,
        attractionIds: []
    };

    try {
        await apiFetch('/attractions/zones', {
            method: 'POST',
            body: JSON.stringify(zone)
        });
        document.getElementById('zoneForm').reset();
        closeModal('zoneModal');
        showAlert('Zona creada exitosamente', 'success');
        await refreshAfterMutation();
    } catch (error) {
        showAlert(`No fue posible guardar la zona: ${error.message}`, 'danger');
    }
}

async function deleteZoneLegacy(id) {
    const confirmed = window.confirm('¿Estás seguro de eliminar esta zona? Las atracciones quedarán sin ubicación.');
    if (!confirmed) {
        return;
    }

    try {
        await apiFetch(`/attractions/zones/${id}`, { method: 'DELETE' });
        showAlert('Zona eliminada exitosamente', 'success');
        await refreshAfterMutation();
    } catch (error) {
        showAlert(`No fue posible eliminar la zona: ${error.message}`, 'danger');
    }
}

async function saveOperator(event) {
    event.preventDefault();

    const operator = {
        username: document.getElementById('operatorUsername').value.trim(),
        email: document.getElementById('operatorEmail').value.trim(),
        password: document.getElementById('operatorPassword').value
    };

    if (!operator.email) {
        showAlert('El correo del operador es obligatorio.', 'danger');
        return;
    }

    try {
        await apiFetch('/admin/operators', {
            method: 'POST',
            body: JSON.stringify(operator)
        });
        document.getElementById('operatorForm').reset();
        closeModal('operatorModal');
        showAlert('Operador creado exitosamente. Password por defecto: operator123 si no definiste una.', 'success');
        await Promise.all([loadOperators(), loadZones()]);
    } catch (error) {
        showAlert(`No fue posible guardar el operador: ${error.message}`, 'danger');
    }
}

async function repairAttraction(id) {
    try {
        await apiFetch(`/attractions/${id}/repair`, { method: 'POST' });
        showAlert('Mantenimiento preventivo reiniciado', 'success');
        await refreshAfterMutation({ reloadOperators: false });
    } catch (error) {
        showAlert(`No fue posible reparar la atraccion: ${error.message}`, 'danger');
    }
}

async function reopenAttraction(id) {
    try {
        await apiFetch(`/attractions/${id}/reopen`, { method: 'PUT' });
        showAlert('Atraccion abierta manualmente', 'success');
        await refreshAfterMutation({ reloadOperators: false });
    } catch (error) {
        showAlert(`No fue posible abrir la atraccion: ${error.message}`, 'danger');
    }
}

async function deleteAttraction(id) {
    try {
        await apiFetch(`/admin/attractions/${id}`, { method: 'DELETE' });
        showAlert('Atraccion eliminada exitosamente', 'success');
        await refreshAfterMutation();
    } catch (error) {
        showAlert(`No fue posible eliminar la atraccion: ${error.message}`, 'danger');
    }
}

async function prepararEdicion(id) {
    try {
        const attraction = attractions.find((item) => Number(item.id) === Number(id))
            || await apiFetch(`/attractions/${id}`);
        if (!attraction) {
            showAlert('No fue posible cargar la atraccion seleccionada', 'danger');
            return;
        }

        editingAttractionId = id;
        setAttractionEditId(id);
        document.getElementById('attractionName').value = attraction.name || '';
        document.getElementById('attractionType').value = attraction.type || '';
        document.getElementById('attractionCapacity').value = attraction.maxCapacityPerCycle || 0;
        document.getElementById('attractionMinHeight').value = attraction.minHeight || 0;
        document.getElementById('attractionMinAge').value = attraction.minAge || 0;
        document.getElementById('attractionCost').value = attraction.additionalCost || 0;
        document.getElementById('attractionZoneId').value = attraction.zoneId || '';
        document.getElementById('attractionStatus').value = attraction.status || 'ACTIVA';
        document.getElementById('attractionPosX').value = attraction.posX ?? '';
        document.getElementById('attractionPosY').value = attraction.posY ?? '';
        const modalTitle = document.getElementById('attractionModalTitle');
        if (modalTitle) {
            modalTitle.textContent = 'Editar Atraccion';
        }
        const submitButton = document.getElementById('attractionSubmitButton');
        if (submitButton) {
            submitButton.textContent = 'Actualizar';
        }
    const zoneSelector = document.getElementById('attractionZoneId');
        if (zoneSelector) {
            zoneSelector.value = attraction.zoneId || '';
            zoneSelector.disabled = false;
        }
        const hint = document.getElementById('attractionCoordinateHint');
        if (hint) {
            hint.textContent = 'Las coordenadas existentes se conservan si no se recalculan en backend.';
        }
        openModal('attractionModal');
    } catch (error) {
        showAlert(`No fue posible cargar la atraccion seleccionada: ${error.message}`, 'danger');
    }
}

function editAttraction(id) {
    prepararEdicion(id);
}

function setAttractionEditId(value) {
    const editIdInput = document.getElementById('edit-id');
    if (editIdInput) {
        editIdInput.value = value;
    }
}

function parseIntegerOrNull(value) {
    if (value == null) {
        return null;
    }
    const text = String(value).trim();
    if (text === '') {
        return null;
    }
    const parsed = parseInt(text, 10);
    return Number.isNaN(parsed) ? null : parsed;
}

function parseIntegerOrFallback(value, fallback) {
    const parsed = parseIntegerOrNull(value);
    return parsed ?? fallback ?? null;
}

function parseDecimalOrFallback(value, fallback) {
    if (value == null) {
        return fallback ?? null;
    }
    const text = String(value).trim();
    if (text === '') {
        return fallback ?? null;
    }
    const parsed = parseFloat(text);
    return Number.isNaN(parsed) ? fallback ?? null : parsed;
}

function normalizeTextOrFallback(value, fallback) {
    if (value == null) {
        return fallback ?? null;
    }
    const text = String(value).trim();
    return text === '' ? fallback ?? null : text;
}

function sanitizeAttractionPayload(payload) {
    return Object.fromEntries(
        Object.entries(payload).filter(([, value]) => value !== undefined)
    );
}

async function refreshAfterMutation(options = {}) {
    const { reloadOperators = true } = options;
    const reloadTasks = [loadAttractions(), loadZones(), loadGraph(), loadDashboard()];
    if (reloadOperators) {
        reloadTasks.push(loadOperators());
    }
    await Promise.all(reloadTasks);
}

async function deleteZone(id) {
    const confirmed = window.confirm('Estas seguro de eliminar esta zona? Se borraran tambien todas sus atracciones y caminos.');
    if (!confirmed) {
        return;
    }

    try {
        await apiFetch(`/attractions/zones/${id}`, { method: 'DELETE' });
        clearGraphCanvas();
        graphSnapshot = { nodes: [], edges: [], zones: [] };
        zones = [];
        attractions = [];
        showAlert('Zona y atracciones eliminadas exitosamente', 'success');
        await refreshAfterMutation();
    } catch (error) {
        showAlert(`No fue posible eliminar la zona: ${error.message}`, 'danger');
    }
}

function clearGraphCanvas() {
    const canvas = document.getElementById('parkMap');
    if (!canvas) {
        return;
    }

    const ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, canvas.width, canvas.height);
}

function populateZoneSelectors() {
    populateZoneSelector('attractionZoneId');
}

function populateZoneSelector(selectId) {
    const select = document.getElementById(selectId);
    if (!select) {
        return;
    }

    const currentValue = select.value;
    select.innerHTML = '<option value="">Seleccionar zona</option>';
    zones.filter(isValidZone).forEach((zone) => {
        const option = document.createElement('option');
        option.value = zone.id;
        option.textContent = zone.name;
        select.appendChild(option);
    });
    select.value = currentValue;
}

function isValidZone(zone) {
    return zone
        && zone.id != null
        && Number(zone.id) > 0
        && typeof zone.name === 'string'
        && zone.name.trim() !== '';
}

async function loadAvailableOperators() {
    try {
        operators = await apiFetch('/admin/operators');
        populateAvailableOperators();
    } catch (error) {
        showAlert(`No fue posible cargar operadores disponibles: ${error.message}`, 'danger');
    }
}

function populateAvailableOperators() {
    const select = document.getElementById('operator-select');
    if (!select) {
        return;
    }

    select.innerHTML = '<option value="">Seleccionar operador responsable</option>';
    operators
        .filter((operator) => operator && operator.id != null && operator.zoneId == null)
        .forEach((operator) => {
            const option = document.createElement('option');
            option.value = operator.id;
            option.textContent = operator.email
                ? `${operator.username} (${operator.email})`
                : operator.username;
            select.appendChild(option);
        });
}

function showAlert(message, type = 'warning') {
    const alertsList = document.getElementById('alertsList');
    if (!alertsList) {
        return;
    }

    const alertItem = document.createElement('div');
    alertItem.className = `alert-item ${type}`;
    alertItem.innerHTML = `
        <span>${message}</span>
        <button onclick="this.parentElement.remove()" class="btn-secondary">Cerrar</button>
    `;
    alertsList.insertBefore(alertItem, alertsList.firstChild);

    setTimeout(() => {
        alertItem.remove();
    }, 5000);
}

function getStatusBadge(status) {
    switch (status) {
        case 'ABIERTA':
            return 'badge-success';
        case 'MANTENIMIENTO':
            return 'badge-warning';
        case 'CLIMA':
            return 'badge-danger';
        default:
            return 'badge-info';
    }
}

function normalizeAdminAttraction(attraction) {
    const estado = resolveAttractionState(attraction);
    return {
        ...attraction,
        estado,
        status: attraction?.status ?? (estado === 'ABIERTA' ? 'ACTIVA' : estado === 'CLIMA' ? 'CERRADA' : 'MANTENIMIENTO'),
        formattedWaitTime: attraction?.formattedWaitTime ?? `${Number(attraction?.estimatedWaitTime ?? 0)} segundos`,
        totalFila: Number(attraction?.totalFila ?? attraction?.peopleWaiting ?? 0),
        conteoFastPass: Number(attraction?.conteoFastPass ?? 0),
        conteoFamiliar: Number(attraction?.conteoFamiliar ?? 0),
        conteoGeneral: Number(attraction?.conteoGeneral ?? 0)
    };
}

function normalizeGraphSnapshot(snapshot) {
    return {
        ...snapshot,
        nodes: (Array.isArray(snapshot?.nodes) ? snapshot.nodes : []).map((node) => {
            const attraction = attractions.find((item) => Number(item.id) === Number(node.id));
            const estado = resolveAttractionState(attraction || node);
            return {
                ...node,
                estado,
                status: estado
            };
        })
    };
}

function mergeAttractionsState(currentAttractions, latestAttractions) {
    const currentById = new Map((Array.isArray(currentAttractions) ? currentAttractions : []).map((item) => [Number(item.id), item]));
    return latestAttractions.map((item) => ({
        ...(currentById.get(Number(item.id)) || {}),
        ...item
    }));
}

function mergeGraphNodeStates(snapshot, latestAttractions) {
    const attractionById = new Map(latestAttractions.map((item) => [Number(item.id), item]));
    return {
        ...snapshot,
        nodes: (Array.isArray(snapshot?.nodes) ? snapshot.nodes : []).map((node) => {
            const attraction = attractionById.get(Number(node.id));
            if (!attraction) {
                return node;
            }

            const estado = resolveAttractionState(attraction);
            return {
                ...node,
                estado,
                status: estado
            };
        })
    };
}

function resolveAttractionState(entity) {
    const rawState = entity?.estado ?? entity?.status ?? null;
    if (!rawState) {
        return 'CLIMA';
    }

    const normalized = String(rawState).trim().toUpperCase();
    if (normalized === 'ACTIVA') {
        return 'ABIERTA';
    }
    if (normalized === 'CERRADA' || normalized === 'TORMENTA') {
        return 'CLIMA';
    }
    return normalized;
}

function isAttractionOpen(entity) {
    return resolveAttractionState(entity) === 'ABIERTA';
}

function getAuthHeaders() {
    return {
        'Content-Type': 'application/json',
        Authorization: localStorage.getItem('token') || DEV_TOKEN
    };
}

function setText(elementId, value) {
    const element = document.getElementById(elementId);
    if (element) {
        element.textContent = value;
    }
}

function logout() {
    localStorage.clear();
    window.location.href = '../index.html';
}

window.onclick = function onclickHandler(event) {
    document.querySelectorAll('.modal').forEach((modal) => {
        if (event.target === modal) {
            modal.style.display = 'none';
        }
    });
};
