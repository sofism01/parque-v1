// API base URL
const API_BASE_URL = 'http://localhost:8080/api';

// Elementos del DOM
const loginForm = document.getElementById('loginForm');
const errorMessage = document.getElementById('errorMessage');
const successMessage = document.getElementById('successMessage');

// Event listeners
loginForm.addEventListener('submit', handleLogin);

async function handleLogin(e) {
    e.preventDefault();

    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;

    // Limpiar mensajes anteriores
    errorMessage.style.display = 'none';
    successMessage.style.display = 'none';

    try {
        const response = await fetch(`${API_BASE_URL}/auth/login`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                username: username,
                password: password
            })
        });

        const data = await response.json();

        if (data.success) {
            // Guardar token y datos de usuario
            localStorage.setItem('token', data.token);
            localStorage.setItem('role', data.role);
            localStorage.setItem('userId', data.userId);
            localStorage.setItem('username', data.username);
            localStorage.setItem('zoneId', data.zoneId ?? '');
            localStorage.setItem('zoneName', data.zoneName ?? '');

            successMessage.textContent = 'Login exitoso. Redirigiendo...';
            successMessage.style.display = 'block';

            // Redirigir según rol
            setTimeout(() => {
                switch(data.role) {
                    case 'ADMIN':
                        window.location.href = 'admin/dashboard.html';
                        break;
                    case 'OPERATOR':
                        window.location.href = 'operator/operator.html';
                        break;
                    case 'VISITOR':
                        window.location.href = 'visitor/visitor.html';
                        break;
                    default:
                        window.location.href = 'index.html';
                }
            }, 1500);
        } else {
            errorMessage.textContent = data.message || 'Error en el login';
            errorMessage.style.display = 'block';
        }
    } catch (error) {
        console.error('Error:', error);
        errorMessage.textContent = 'Error de conexión con el servidor';
        errorMessage.style.display = 'block';
    }
}

// Función para verificar si el usuario está autenticado
function checkAuth() {
    const token = localStorage.getItem('token');
    if (!token) {
        window.location.href = 'index.html';
        return false;
    }
    return true;
}

// Función para obtener headers con autenticación
function getAuthHeaders() {
    return {
        'Content-Type': 'application/json',
        'Authorization': localStorage.getItem('token') || ''
    };
}

// Función para hacer logout
function logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('role');
    localStorage.removeItem('userId');
    localStorage.removeItem('username');
    localStorage.removeItem('zoneId');
    localStorage.removeItem('zoneName');
    window.location.href = 'index.html';
}
