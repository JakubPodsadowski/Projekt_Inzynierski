/**
 * Simulation control panel: virtual clock, speed, pause/resume, chaos.
 */
const WarehouseSimulation = (function () {
    const POLL_MS = 1000;
    let status = null;
    let pollTimer = null;
    let speedDebounce = null;
    const modeListeners = [];
    let lastDemoMode = null;

    function formatVirtualTime(isoString) {
        if (!isoString) {
            return '—';
        }
        const date = new Date(isoString);
        if (Number.isNaN(date.getTime())) {
            return isoString;
        }
        const pad = function (n) {
            return String(n).padStart(2, '0');
        };
        return pad(date.getDate()) + '.' + pad(date.getMonth() + 1) + '.' + date.getFullYear()
            + ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes()) + ':' + pad(date.getSeconds());
    }

    function setText(id, text) {
        const el = document.getElementById(id);
        if (el) {
            el.textContent = text;
        }
    }

    function clearDemoViews() {
        // Overview KPIs
        setText('kpi-imbalance', '0.0');
        setText('kpi-pending', '0');
        setText('kpi-in-transit', '0');
        setText('kpi-on-break', '0');
        setText('kpi-total-employees', '0');
        setText('overview-timestamp', '—');

        const overviewBody = document.getElementById('overview-departments-body');
        if (overviewBody) {
            overviewBody.innerHTML =
                '<tr><td colspan="5" class="text-center text-muted py-4">No departments</td></tr>';
        }

        const departmentsBody = document.getElementById('departments-table-body');
        if (departmentsBody) {
            departmentsBody.innerHTML =
                '<tr><td colspan="8" class="text-center text-muted py-4">No departments</td></tr>';
        }

        const demoSelect = document.getElementById('demo-dept-select');
        if (demoSelect) {
            demoSelect.innerHTML = '<option value="">— select —</option>';
        }
    }

    function applyDemoOnly(isDemoMode) {
        document.querySelectorAll('[data-demo-only]').forEach(function (el) {
            el.classList.toggle('d-none', !isDemoMode);
        });
        document.body.classList.toggle('mode-demo', isDemoMode);
        document.body.classList.toggle('mode-real', !isDemoMode);

        if (!isDemoMode) {
            clearDemoViews();
        }

        if (lastDemoMode !== isDemoMode) {
            lastDemoMode = isDemoMode;
            modeListeners.forEach(function (listener) {
                try {
                    listener(isDemoMode);
                } catch (e) {
                    console.error(e);
                }
            });
            window.dispatchEvent(new CustomEvent('warehouse:mode', { detail: { demo: isDemoMode } }));
        }
    }

    function applyStatus(response) {
        status = response;
        const modeLabel = document.getElementById('mode-label');
        const modeToggle = document.getElementById('mode-toggle');
        const modeToggleWrapper = document.getElementById('mode-toggle-wrapper');
        const clock = document.getElementById('sim-clock');
        const badge = document.getElementById('sim-state-badge');
        const speed = document.getElementById('sim-speed');
        const speedLabel = document.getElementById('sim-speed-label');
        const playBtn = document.getElementById('sim-play-btn');
        const pauseBtn = document.getElementById('sim-pause-btn');

        const isPaused = response.paused || !response.running;
        const isDemoRunning = response.profileDemo && !isPaused;

        // Hide all demo-only UI (simulation bar, sabotage, chaos) in REAL mode
        applyDemoOnly(isDemoRunning);

        if (modeToggleWrapper) {
            modeToggleWrapper.classList.toggle('d-none', !response.profileDemo);
        }

        if (clock) {
            clock.textContent = formatVirtualTime(response.virtualTime);
        }

        if (badge) {
            badge.classList.remove('badge-success', 'badge-secondary', 'badge-warning');
            if (isDemoRunning) {
                badge.textContent = 'RUNNING';
                badge.classList.add('badge-success');
            } else {
                badge.textContent = 'PAUSED';
                badge.classList.add('badge-warning');
            }
        }

        if (modeLabel) {
            modeLabel.textContent = isDemoRunning ? 'DEMO' : 'REAL';
            modeLabel.classList.remove('badge-success', 'badge-secondary', 'badge-info');
            modeLabel.classList.add(isDemoRunning ? 'badge-success' : 'badge-secondary');
        }

        if (modeToggle && response.profileDemo && document.activeElement !== modeToggle) {
            modeToggle.checked = isDemoRunning;
        }

        if (speed && document.activeElement !== speed) {
            speed.value = String(response.speed);
        }
        if (speedLabel) {
            speedLabel.textContent = response.speed + 'x';
        }

        if (playBtn) {
            playBtn.disabled = !response.profileDemo || !isPaused;
        }
        if (pauseBtn) {
            pauseBtn.disabled = !response.profileDemo || isPaused;
        }
    }

    async function refresh() {
        const response = await WarehouseApi.get('/simulation/status');
        applyStatus(response);
        return response;
    }

    async function setSpeed(speed) {
        const response = await WarehouseApi.post('/simulation/speed', { speed: Number(speed) });
        applyStatus(response);
        return response;
    }

    async function pause() {
        const response = await WarehouseApi.post('/simulation/pause');
        applyStatus(response);
        WarehouseApi.showToast('Switched to REAL (simulation paused)', 'success');
        return response;
    }

    async function resume() {
        const response = await WarehouseApi.post('/simulation/resume');
        applyStatus(response);
        WarehouseApi.showToast('Switched to DEMO (simulation running)', 'success');
        return response;
    }

    async function orderSurge(items) {
        const response = await WarehouseApi.post('/simulation/chaos/order-surge', {
            itemsPerEmployee: items
        });
        WarehouseApi.showToast(
            'Chaos: +' + response.itemsPerEmployee + ' items for ' + response.affectedEmployees + ' employees',
            'success'
        );
        return response;
    }

    function isDemo() {
        if (document.body.classList.contains('mode-demo')) {
            return true;
        }
        if (document.body.classList.contains('mode-real')) {
            return false;
        }
        return status != null
            && status.profileDemo === true
            && status.paused !== true
            && status.running === true;
    }

    function onModeChange(listener) {
        if (typeof listener === 'function') {
            modeListeners.push(listener);
        }
    }

    function bindEvents() {
        const speed = document.getElementById('sim-speed');
        if (speed) {
            speed.addEventListener('input', function () {
                const label = document.getElementById('sim-speed-label');
                if (label) {
                    label.textContent = speed.value + 'x';
                }
            });
            speed.addEventListener('change', function () {
                clearTimeout(speedDebounce);
                speedDebounce = setTimeout(function () {
                    setSpeed(speed.value).catch(function () {
                        refresh();
                    });
                }, 150);
            });
        }

        const playBtn = document.getElementById('sim-play-btn');
        if (playBtn) {
            playBtn.addEventListener('click', function () {
                resume().catch(function () { /* toast shown */ });
            });
        }

        const pauseBtn = document.getElementById('sim-pause-btn');
        if (pauseBtn) {
            pauseBtn.addEventListener('click', function () {
                pause().catch(function () { /* toast shown */ });
            });
        }

        const modeToggle = document.getElementById('mode-toggle');
        if (modeToggle) {
            modeToggle.addEventListener('change', function () {
                const action = modeToggle.checked ? resume() : pause();
                action.catch(function () {
                    refresh();
                });
            });
        }

        const chaos500 = document.getElementById('sim-chaos-500');
        if (chaos500) {
            chaos500.addEventListener('click', function () {
                orderSurge(500).catch(function () { /* toast shown */ });
            });
        }

        const chaos1000 = document.getElementById('sim-chaos-1000');
        if (chaos1000) {
            chaos1000.addEventListener('click', function () {
                orderSurge(1000).catch(function () { /* toast shown */ });
            });
        }
    }

    function init() {
        bindEvents();
        refresh()
            .then(function () {
                pollTimer = setInterval(function () {
                    refresh().catch(function () { /* ignore transient errors */ });
                }, POLL_MS);
            })
            .catch(function () {
                const modeLabel = document.getElementById('mode-label');
                if (modeLabel) {
                    modeLabel.textContent = 'OFFLINE';
                    modeLabel.classList.remove('badge-success', 'badge-secondary');
                    modeLabel.classList.add('badge-info');
                }
            });
    }

    return {
        init: init,
        refresh: refresh,
        isDemo: isDemo,
        onModeChange: onModeChange
    };
})();
