(function () {
    'use strict';

    const POLL_MS = 3000;
    let timer = null;

    function escapeHtml(value) {
        const div = document.createElement('div');
        div.textContent = value == null ? '' : String(value);
        return div.innerHTML;
    }

    function statusBadge(emp) {
        if (emp.onBreak) {
            return '<span class="badge badge-warning">On break</span>';
        }
        const status = emp.relocationStatus || 'NONE';
        if (status === 'IN_TRANSIT') {
            return '<span class="badge badge-info">In transit</span>';
        }
        if (status === 'SCHEDULED') {
            return '<span class="badge badge-primary">Scheduled reloc</span>';
        }
        return '<span class="badge badge-success">Working</span>';
    }

    function renderRows(employees) {
        const tbody = document.getElementById('employees-body');
        if (!employees.length) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-4">No employees</td></tr>';
            return;
        }

        tbody.innerHTML = employees.map(function (emp) {
            const name = escapeHtml(emp.firstName + ' ' + emp.lastName);
            const dept = escapeHtml(emp.currentDepartment || '—');
            return (
                '<tr>' +
                '<td><strong>' + name + '</strong></td>' +
                '<td>' + dept + '</td>' +
                '<td>' + Number(emp.currentIph || 0).toFixed(1) + '</td>' +
                '<td>' + escapeHtml(emp.breakTier || '—') + '</td>' +
                '<td>' + statusBadge(emp) + '</td>' +
                '<td>' + (emp.orderItemsRemaining ?? 0) + '</td>' +
                '<td class="text-right">' +
                '<a class="btn btn-sm btn-outline-primary" href="/employees/' + emp.id + '/view">' +
                'View</a></td>' +
                '</tr>'
            );
        }).join('');
    }

    async function refresh() {
        const loading = document.getElementById('employees-loading');
        if (loading) {
            loading.classList.remove('d-none');
        }
        try {
            const employees = await WarehouseApi.get('/employees');
            renderRows(employees);
        } catch (err) {
            document.getElementById('employees-body').innerHTML =
                '<tr><td colspan="7" class="text-center text-danger py-4">' +
                escapeHtml(err.message || 'Failed to load') +
                '</td></tr>';
        } finally {
            if (loading) {
                loading.classList.add('d-none');
            }
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (!document.getElementById('employees-body')) {
            return;
        }
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
