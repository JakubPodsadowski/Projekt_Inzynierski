/**
 * Global UI: DEMO/REAL mode badge, toggle, and demo-only visibility.
 */
const WarehouseMode = (function () {
    let currentMode = null;

    function setLoading(loading) {
        const spinner = document.getElementById('mode-loading');
        const toggle = document.getElementById('mode-toggle');
        if (spinner) {
            spinner.classList.toggle('d-none', !loading);
        }
        if (toggle) {
            toggle.disabled = loading;
        }
    }

    function applyMode(response) {
        currentMode = response.mode;
        const label = document.getElementById('mode-label');
        const toggle = document.getElementById('mode-toggle');
        const toggleWrapper = document.getElementById('mode-toggle-wrapper');

        if (label) {
            label.textContent = response.mode;
            label.classList.remove('badge-success', 'badge-secondary', 'badge-info');
            if (response.mode === 'DEMO') {
                label.classList.add('badge-success');
            } else {
                label.classList.add('badge-secondary');
            }
        }

        if (toggleWrapper) {
            toggleWrapper.classList.toggle('d-none', !response.profileDemo);
        }

        if (toggle && response.profileDemo) {
            toggle.checked = response.mode === 'DEMO';
        }

        const isDemo = response.mode === 'DEMO';
        document.querySelectorAll('[data-demo-only]').forEach(function (el) {
            el.classList.toggle('d-none', !isDemo);
        });

        document.body.classList.toggle('mode-demo', isDemo);
        document.body.classList.toggle('mode-real', !isDemo);
    }

    async function refresh() {
        setLoading(true);
        try {
            const response = await WarehouseApi.get('/system/mode');
            applyMode(response);
            return response;
        } finally {
            setLoading(false);
        }
    }

    async function setMode(mode) {
        setLoading(true);
        try {
            const response = await WarehouseApi.post('/system/mode', { mode: mode });
            applyMode(response);
            WarehouseApi.showToast('Mode set to ' + response.mode, 'success');
            return response;
        } finally {
            setLoading(false);
        }
    }

    function isDemo() {
        return currentMode === 'DEMO';
    }

    function init() {
        const toggle = document.getElementById('mode-toggle');
        if (toggle) {
            toggle.addEventListener('change', function () {
                const nextMode = toggle.checked ? 'DEMO' : 'REAL';
                setMode(nextMode).catch(function () {
                    refresh();
                });
            });
        }
        refresh().catch(function () {
            const label = document.getElementById('mode-label');
            if (label) {
                label.textContent = 'OFFLINE';
                label.classList.remove('badge-success', 'badge-secondary');
                label.classList.add('badge-info');
            }
        });
    }

    return {
        init: init,
        refresh: refresh,
        setMode: setMode,
        isDemo: isDemo
    };
})();

document.addEventListener('DOMContentLoaded', function () {
    WarehouseMode.init();
});
