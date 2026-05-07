const API_BASE_URL = 'http://localhost:8080/api';
const DASHBOARD_REFRESH_MS = 5000;

let dashboardState = null;
let refreshHandle = null;

document.addEventListener('DOMContentLoaded', async () => {
    if (!localStorage.getItem('token') || localStorage.getItem('role') !== 'OPERATOR') {
        window.location.href = '../index.html';
        return;
    }

    bindNavigation();
    bindForms();

    try {
        await refreshDashboard(false);
    } catch (error) {
        showAlert(error.message || 'No fue posible cargar el panel del operador.', 'error');
    }

    refreshHandle = window.setInterval(() => {
        refreshDashboard(false);
    }, DASHBOARD_REFRESH_MS);
});

async function refreshDashboard(showToast = false) {
    dashboardState = await apiFetch('/operator/dashboard');
    renderOperatorMeta();
    renderSummary();
    renderAttractions();
    renderRestrictionSelector();
    renderCapacityCard(dashboardState.zoneCapacity);

    if (showToast) {
        showAlert('Panel actualizado.', 'success');
    }
}

function bindNavigation() {
    document.querySelectorAll('.nav-link[data-section]').forEach((link) => {
        link.addEventListener('click', (event) => {
            event.preventDefault();
            const sectionId = link.dataset.section;

            document.querySelectorAll('.nav-link[data-section]').forEach((item) => item.classList.remove('active'));
            document.querySelectorAll('.section').forEach((section) => section.classList.remove('active'));

            link.classList.add('active');
            document.getElementById(sectionId)?.classList.add('active');
        });
    });
}

function bindForms() {
    document.getElementById('restrictionForm')?.addEventListener('submit', async (event) => {
        event.preventDefault();
        await runRestrictionCheck();
    });
}

function renderOperatorMeta() {
    document.getElementById('operatorName').textContent = dashboardState.operatorName || localStorage.getItem('username') || '-';
    document.getElementById('operatorEmail').textContent = dashboardState.operatorEmail || 'Sin correo';
    document.getElementById('zoneName').textContent = dashboardState.zoneName || 'Sin zona';
    document.getElementById('zonePill').textContent = dashboardState.zoneName || 'Zona sin asignar';
    localStorage.setItem('zoneId', dashboardState.zoneId ?? '');
    localStorage.setItem('zoneName', dashboardState.zoneName ?? '');
}

function renderSummary() {
    const attractions = Array.isArray(dashboardState.attractions) ? dashboardState.attractions : [];
    document.getElementById('totalAttractions').textContent = String(attractions.length);
    document.getElementById('zoneOccupancyStat').textContent = formatCapacity(dashboardState.zoneCapacity);
}

function renderAttractions() {
    const container = document.getElementById('attractionsDashboard');
    const attractions = Array.isArray(dashboardState.attractions) ? dashboardState.attractions : [];

    if (!attractions.length) {
        container.innerHTML = '<div class="empty-state">No hay atracciones asociadas a esta zona.</div>';
        return;
    }

    container.innerHTML = attractions.map((attraction) => `
        <article class="attraction-card">
            <div class="card-header">
                <div>
                    <p class="card-kicker">${attraction.zoneName || 'Zona'}</p>
                    <h4>${attraction.name}</h4>
                </div>
                <span class="status-badge status-${normalizeState(attraction.estado)}">${normalizeState(attraction.estado)}</span>
            </div>

            <div class="metrics-grid">
                <div class="metric">
                    <span>Visitantes totales</span>
                    <strong>${attraction.visitantesTotales}</strong>
                </div>
                <div class="metric">
                    <span>Meta de mantenimiento</span>
                    <strong>${attraction.visitantesTotales} / ${attraction.maintenanceThreshold}</strong>
                </div>
                <div class="metric">
                    <span>Fila actual</span>
                    <strong>${attraction.peopleWaiting}</strong>
                </div>
                <div class="metric">
                    <span>Espera estimada</span>
                    <strong>${attraction.estimatedWaitTime} s</strong>
                </div>
            </div>

            <div class="status-toggle">
                ${renderStatusButton(attraction, 'ABIERTA')}
                ${renderStatusButton(attraction, 'MANTENIMIENTO')}
                ${renderStatusButton(attraction, 'CLIMA')}
            </div>

            <div class="queue-preview">
                <div class="queue-preview-header">
                    <span>Vista previa de la cola</span>
                    <strong>Top 3</strong>
                </div>
                ${renderQueuePreview(attraction.queuePreview)}
            </div>
        </article>
    `).join('');
}

function renderStatusButton(attraction, state) {
    const activeClass = normalizeState(attraction.estado) === state ? 'active' : '';
    return `
        <button class="toggle-button ${activeClass}" onclick="updateAttractionState(${attraction.id}, '${state}')">
            ${state}
        </button>
    `;
}

function renderQueuePreview(queuePreview = []) {
    if (!queuePreview.length) {
        return '<p class="queue-empty">No hay visitantes en espera.</p>';
    }

    return queuePreview.map((entry) => `
        <div class="queue-row ${entry.fastPass ? 'fast-pass' : ''}">
            <span>#${entry.positionInQueue || '-'} ${entry.visitorName}</span>
            <strong>${entry.fastPass ? 'FAST-PASS' : entry.ticketType || 'GENERAL'}</strong>
        </div>
    `).join('');
}

function renderRestrictionSelector() {
    const select = document.getElementById('restrictionAttraction');
    const attractions = Array.isArray(dashboardState.attractions) ? dashboardState.attractions : [];
    const previousValue = select.value;

    select.innerHTML = '<option value="">Selecciona una atraccion</option>';
    attractions.forEach((attraction) => {
        const option = document.createElement('option');
        option.value = String(attraction.id);
        option.textContent = `${attraction.name} (${normalizeState(attraction.estado)})`;
        select.appendChild(option);
    });

    if (previousValue && attractions.some((item) => String(item.id) === previousValue)) {
        select.value = previousValue;
    }
}

async function runRestrictionCheck() {
    const attractionId = Number(document.getElementById('restrictionAttraction').value);
    const visitorId = Number(document.getElementById('visitorIdInput').value);
    const resultNode = document.getElementById('restrictionResult');

    if (!attractionId || !visitorId) {
        showAlert('Debes seleccionar una atraccion e ingresar un ID de visitante.', 'error');
        return;
    }

    try {
        const result = await apiFetch('/operator/restrictions/check', {
            method: 'POST',
            body: JSON.stringify({ attractionId, visitorId })
        });

        resultNode.className = `restriction-result ${result.allowed ? 'ok' : 'fail'}`;
        resultNode.innerHTML = `
            <div class="restriction-icon">${result.allowed ? 'CHECK' : 'X'}</div>
            <div>
                <h4>${result.visitorName} -> ${result.attractionName}</h4>
                <p>${result.message}</p>
                <div class="restriction-grid">
                    <span>Edad: ${result.visitorAge} / min ${result.requiredAge}</span>
                    <span>Altura: ${Number(result.visitorHeight).toFixed(2)} / min ${Number(result.requiredHeight).toFixed(2)} m</span>
                    <span>Saldo: $${Number(result.visitorBalance).toFixed(2)} / costo $${Number(result.requiredBalance).toFixed(2)}</span>
                </div>
            </div>
        `;
    } catch (error) {
        resultNode.className = 'restriction-result fail';
        resultNode.textContent = error.message || 'No fue posible validar el visitante.';
    }
}

async function updateAttractionState(attractionId, estado) {
    try {
        await apiFetch(`/operator/attractions/${attractionId}/status`, {
            method: 'POST',
            body: JSON.stringify({ estado })
        });
        await refreshDashboard(false);
        showAlert(`Estado actualizado a ${estado}.`, 'success');
    } catch (error) {
        showAlert(error.message || 'No fue posible cambiar el estado.', 'error');
    }
}

function renderCapacityCard(capacity) {
    const zoneName = capacity?.zoneName || dashboardState.zoneName || 'Zona';
    const current = Number(capacity?.currentOccupancy || 0);
    const max = Number(capacity?.maxCapacity || 0);
    const ratio = Math.max(0, Math.min(1, Number(capacity?.ratio || 0)));
    const full = Boolean(capacity?.full);

    document.getElementById('capacityZoneName').textContent = zoneName;
    document.getElementById('capacityNumbers').textContent = `${current} / ${max}`;
    document.getElementById('capacityBadge').textContent = full ? 'Zona llena' : 'Disponible';
    document.getElementById('capacityBadge').className = `capacity-badge ${full ? 'full' : ''}`;
    document.getElementById('capacityFill').style.width = `${ratio * 100}%`;
    document.getElementById('capacityFill').className = `capacity-fill ${full ? 'full' : ''}`;
    document.getElementById('capacityMessage').textContent = full
        ? 'Alerta visual: la zona alcanzo su capacidad maxima.'
        : 'La zona aun puede recibir visitantes.';
    document.getElementById('capacityCard').className = `capacity-card ${full ? 'full' : ''}`;
}

async function apiFetch(path, options = {}) {
    const response = await fetch(`${API_BASE_URL}${path}`, {
        ...options,
        headers: {
            'Content-Type': 'application/json',
            Authorization: localStorage.getItem('token') || '',
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

function normalizeState(state) {
    return String(state || '').trim().toUpperCase() || 'DESCONOCIDO';
}

function formatCapacity(capacity) {
    const current = Number(capacity?.currentOccupancy || 0);
    const max = Number(capacity?.maxCapacity || 0);
    return `${current} / ${max}`;
}

function showAlert(message, type = 'success') {
    const alertNode = document.getElementById('appAlert');
    alertNode.hidden = false;
    alertNode.className = `app-alert ${type}`;
    alertNode.textContent = message;
    window.clearTimeout(showAlert.timeoutId);
    showAlert.timeoutId = window.setTimeout(() => {
        alertNode.hidden = true;
    }, 3200);
}

function logout() {
    if (refreshHandle) {
        window.clearInterval(refreshHandle);
    }
    localStorage.clear();
    window.location.href = '../index.html';
}
