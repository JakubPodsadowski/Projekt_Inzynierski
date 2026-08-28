/**
 * Overview dashboard: polls GET /api/v1/overview.
 */
(function () {
    const POLL_INTERVAL_MS = 2500;

    function formatNumber(value, decimals) {
        if (value == null || Number.isNaN(value)) {
            return '—';
        }
        return Number(value).toFixed(decimals);
    }

    function formatTimestamp(isoString) {
        if (!isoString) {
            return '—';
        }
        const date = new Date(isoString);
        if (Number.isNaN(date.getTime())) {
            return isoString;
        }
        return date.toLocaleString();
    }

    function deficitClass(deficit) {
        if (deficit > 5) {
            return 'text-danger font-weight-bold';
        }
        if (deficit < -5) {
            return 'text-success font-weight-bold';
        }
        return '';
    }

    function setText(id, text) {
        const el = document.getElementById(id);
        if (el) {
            el.textContent = text;
        }
    }

    function renderDepartments(departments) {
        const tbody = document.getElementById('overview-departments-body');
        if (!tbody) {
            return;
        }

        if (!departments || departments.length === 0) {
            tbody.innerHTML =
                '<tr><td colspan="5" class="text-center text-muted py-4">No departments</td></tr>';
            return;
        }

        tbody.innerHTML = departments.map(function (dept) {
            const deficit = dept.deficit;
            const cls = deficitClass(deficit);
            return '<tr>' +
                '<td><a href="/departments">' + escapeHtml(dept.name) + '</a></td>' +
                '<td class="text-right">' + formatNumber(dept.currentIph, 1) + '</td>' +
                '<td class="text-right">' + formatNumber(dept.requiredIph, 1) + '</td>' +
                '<td class="text-right ' + cls + '">' + formatNumber(deficit, 1) + '</td>' +
                '<td class="text-right">' + dept.employeeCount + '</td>' +
                '</tr>';
        }).join('');
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function renderOverview(data) {
        setText('kpi-imbalance', formatNumber(data.deficitImbalance, 1));
        setText('kpi-pending', String(data.pendingRelocations));
        setText('kpi-in-transit', String(data.inTransitRelocations));
        setText('kpi-on-break', String(data.employeesOnBreak));
        setText('kpi-total-employees', String(data.totalEmployees));
        setText('overview-timestamp', formatTimestamp(data.timestamp));
        renderDepartments(data.departments);
    }

    function setLoading(loading) {
        const el = document.getElementById('overview-loading');
        if (el) {
            el.classList.toggle('d-none', !loading);
        }
    }

    async function refresh() {
        setLoading(true);
        try {
            const data = await WarehouseApi.get('/overview');
            renderOverview(data);
        } finally {
            setLoading(false);
        }
    }

    function init() {
        if (!document.getElementById('overview-dashboard')) {
            return;
        }

        refresh();
        setInterval(refresh, POLL_INTERVAL_MS);
    }

    document.addEventListener('DOMContentLoaded', init);
})();
