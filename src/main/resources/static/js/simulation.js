/**
 * Simulation control panel: virtual clock, speed, pause/resume, chaos.
 * Header Demo toggle switches REAL/DEMO datasources via /system/mode.
 */
const WarehouseSimulation = (function () {
    const POLL_MS = 1000;
    let status = null;
    let pollTimer = null;
    let speedDebounce = null;
    const modeListeners = [];
    let lastModeKey = null;

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

    function isDemoMode(response) {
        const current = response || status;
        return current != null && current.mode === 'DEMO';
    }

    function notifyModeChange(response) {
        const demo = isDemoMode(response);
        const key = String(response.profileDemo) + ':' + response.mode;
        document.body.classList.toggle('mode-demo', demo);
        document.body.classList.toggle('mode-real', !demo);

        document.querySelectorAll('[data-demo-only]').forEach(function (el) {
            el.classList.toggle('d-none', !demo);
        });

        if (lastModeKey !== key) {
            lastModeKey = key;
            modeListeners.forEach(function (listener) {
                try {
                    listener(demo);
                } catch (e) {
                    console.error(e);
                }
            });
            window.dispatchEvent(new CustomEvent('warehouse:mode', {
                detail: { demo: demo, mode: response.mode }
            }));
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

        const demo = isDemoMode(response);
        const isPaused = response.paused || !response.running;
        const isDemoRunning = demo && !isPaused;

        notifyModeChange(response);

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
            } else if (demo) {
                badge.textContent = 'PAUSED';
                badge.classList.add('badge-warning');
            } else {
                badge.textContent = 'OFF';
                badge.classList.add('badge-secondary');
            }
        }

        if (modeLabel) {
            modeLabel.textContent = demo ? 'DEMO' : 'REAL';
            modeLabel.classList.remove('badge-success', 'badge-secondary', 'badge-info', 'badge-warning');
            modeLabel.classList.add(demo ? 'badge-success' : 'badge-secondary');
        }

        if (modeToggle && response.profileDemo && document.activeElement !== modeToggle) {
            modeToggle.checked = demo;
        }

        if (speed && document.activeElement !== speed) {
            speed.value = String(response.speed);
        }
        if (speedLabel) {
            speedLabel.textContent = response.speed + 'x';
        }

        if (playBtn) {
            playBtn.disabled = !demo || !isPaused;
        }
        if (pauseBtn) {
            pauseBtn.disabled = !demo || isPaused;
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
        WarehouseApi.showToast('Simulation paused', 'success');
        return response;
    }

    async function resume() {
        const response = await WarehouseApi.post('/simulation/resume');
        applyStatus(response);
        WarehouseApi.showToast('Simulation resumed', 'success');
        return response;
    }

    async function setSystemMode(mode) {
        const response = await WarehouseApi.post('/system/mode', { mode: mode });
        await refresh();
        WarehouseApi.showToast(
            mode === 'DEMO' ? 'Switched to DEMO database' : 'Switched to REAL database',
            'success'
        );
        return response;
    }

    async function orderSurge(items) {
        const response = await WarehouseApi.post('/simulation/chaos/order-surge', {
            itemsPerEmployee: items
        });
        WarehouseApi.showToast(
            'Chaos: +' + response.itemsPerEmployee + ' items in '
                + response.affectedEmployees + ' new orders',
            'success'
        );
        return response;
    }

    function isDemo() {
        return isDemoMode(status);
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
                const mode = modeToggle.checked ? 'DEMO' : 'REAL';
                setSystemMode(mode).catch(function () {
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
                    modeLabel.classList.remove('badge-success', 'badge-secondary', 'badge-warning');
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
