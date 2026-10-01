/**
 * Inventory page: bins vs unitsPerBin capacity (read-only).
 */
(function () {
    'use strict';

    const POLL_MS = 5000;
    let timer = null;
    let debounceTimer = null;

    function escapeHtml(value) {
        const div = document.createElement('div');
        div.textContent = value == null ? '' : String(value);
        return div.innerHTML;
    }

    function fillBar(percent, empty) {
        if (empty) {
            return '<div class="progress progress-xs mb-0">' +
                '<div class="progress-bar bg-secondary" style="width:0%"></div></div>' +
                '<small class="text-muted">empty</small>';
        }
        const pct = Math.max(0, Math.min(100, Number(percent) || 0));
        let cls = 'bg-success';
        if (pct >= 100) {
            cls = 'bg-danger';
        } else if (pct >= 75) {
            cls = 'bg-warning';
        }
        return '<div class="progress progress-xs mb-0">' +
            '<div class="progress-bar ' + cls + '" style="width:' + pct + '%"></div></div>' +
            '<small>' + pct.toFixed(0) + '%</small>';
    }

    function buildQuery() {
        const params = new URLSearchParams();
        const sku = document.getElementById('inventory-sku').value.trim();
        const code = document.getElementById('inventory-code').value.trim();
        const occupiedOnly = document.getElementById('inventory-occupied-only').checked;
        if (sku) {
            params.set('sku', sku);
        }
        if (code) {
            params.set('locationCode', code);
        }
        if (occupiedOnly) {
            params.set('occupiedOnly', 'true');
        }
        const qs = params.toString();
        return qs ? ('?' + qs) : '';
    }

    function renderStats(rows) {
        let occupied = 0;
        let empty = 0;
        let full = 0;
        rows.forEach(function (row) {
            if (row.empty) {
                empty++;
            } else {
                occupied++;
                if ((row.fillPercent || 0) >= 100) {
                    full++;
                }
            }
        });
        document.getElementById('inv-stat-rows').textContent = String(rows.length);
        document.getElementById('inv-stat-occupied').textContent = String(occupied);
        document.getElementById('inv-stat-empty').textContent = String(empty);
        document.getElementById('inv-stat-full').textContent = String(full);
    }

    function renderRows(rows) {
        const tbody = document.getElementById('inventory-body');
        renderStats(rows);
        if (!rows.length) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">No bins match</td></tr>';
            return;
        }
        tbody.innerHTML = rows.map(function (row) {
            const sku = row.empty ? '<span class="text-muted">—</span>' : escapeHtml(row.productSku || '');
            const name = row.empty ? '<span class="text-muted">free</span>' : escapeHtml(row.productName || '');
            const qty = row.empty ? '—' : String(row.quantity);
            const cap = row.empty ? '—' : String(row.unitsPerBin);
            return (
                '<tr>' +
                '<td><code>' + escapeHtml(row.locationCode) + '</code></td>' +
                '<td>' + sku + '</td>' +
                '<td>' + name + '</td>' +
                '<td class="text-right">' + qty + '</td>' +
                '<td class="text-right">' + cap + '</td>' +
                '<td>' + fillBar(row.fillPercent, row.empty) + '</td>' +
                '</tr>'
            );
        }).join('');
    }

    async function refresh() {
        const loading = document.getElementById('inventory-loading');
        loading.classList.remove('d-none');
        try {
            const rows = await WarehouseApi.get('/inventory' + buildQuery());
            renderRows(rows);
        } catch (err) {
            document.getElementById('inventory-body').innerHTML =
                '<tr><td colspan="6" class="text-center text-danger py-4">' +
                escapeHtml(err.message || 'Failed to load') + '</td></tr>';
        } finally {
            loading.classList.add('d-none');
        }
    }

    function scheduleRefresh() {
        if (debounceTimer) {
            clearTimeout(debounceTimer);
        }
        debounceTimer = setTimeout(function () {
            refresh().catch(function () { /* toast shown */ });
        }, 250);
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (!document.getElementById('inventory-page')) {
            return;
        }

        document.getElementById('inventory-refresh-btn').addEventListener('click', function () {
            refresh().catch(function () { /* toast shown */ });
        });
        document.getElementById('inventory-sku').addEventListener('input', scheduleRefresh);
        document.getElementById('inventory-code').addEventListener('input', scheduleRefresh);
        document.getElementById('inventory-occupied-only').addEventListener('change', function () {
            refresh().catch(function () { /* toast shown */ });
        });

        refresh().catch(function () { /* toast shown */ });
        timer = setInterval(function () {
            refresh().catch(function () { /* toast shown */ });
        }, POLL_MS);

        window.addEventListener('warehouse:mode', function () {
            refresh().catch(function () { /* toast shown */ });
        });
    });

    window.addEventListener('beforeunload', function () {
        if (timer) {
            clearInterval(timer);
        }
    });
})();
