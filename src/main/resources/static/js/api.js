/**
 * Shared REST client for /api/v1 (same-origin Thymeleaf UI).
 */
const WarehouseApi = (function () {
    const API_BASE = '/api/v1';

    function showToast(message, type) {
        const container = document.getElementById('toast-container');
        if (!container) {
            console.error(message);
            return;
        }

        const variant = type === 'danger' ? 'bg-danger' : type === 'success' ? 'bg-success' : 'bg-info';
        const id = 'toast-' + Date.now();
        const html =
            '<div id="' + id + '" class="toast ' + variant + ' text-white" role="alert" data-autohide="true" data-delay="5000">' +
            '  <div class="toast-body">' + escapeHtml(message) + '</div>' +
            '</div>';
        container.insertAdjacentHTML('beforeend', html);
        const el = document.getElementById(id);
        $(el).toast('show');
        $(el).on('hidden.bs.toast', function () {
            el.remove();
        });
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    async function request(path, options) {
        const opts = Object.assign({
            headers: { 'Accept': 'application/json' }
        }, options || {});

        if (opts.body && typeof opts.body === 'object' && !(opts.body instanceof FormData)) {
            opts.headers['Content-Type'] = 'application/json';
            opts.body = JSON.stringify(opts.body);
        }

        const response = await fetch(API_BASE + path, opts);

        if (!response.ok) {
            let message = response.statusText;
            try {
                const body = await response.json();
                if (body && body.error) {
                    message = body.error;
                }
            } catch (e) {
                // ignore non-JSON error bodies
            }
            showToast(message, 'danger');
            throw new Error(message);
        }

        if (response.status === 204) {
            return null;
        }

        const contentType = response.headers.get('Content-Type') || '';
        if (contentType.includes('application/json')) {
            return response.json();
        }
        return null;
    }

    return {
        get: function (path) {
            return request(path, { method: 'GET' });
        },
        post: function (path, body) {
            return request(path, { method: 'POST', body: body });
        },
        patch: function (path, body) {
            return request(path, { method: 'PATCH', body: body });
        },
        delete: function (path) {
            return request(path, { method: 'DELETE' });
        },
        showToast: showToast
    };
})();
