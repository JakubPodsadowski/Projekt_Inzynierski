/**
 * Relocations page: pending / in-transit / history, force schedule, cancel, balance.
 */
(function () {
    'use strict';

    const POLL_MS = 3000;
    let timer = null;
    let employeesById = {};
    /** employeeId → open relocation log id (SCHEDULED / IN_TRANSIT) */
    let openLogByEmployeeId = {};

    function escapeHtml(value) {
        const div = document.createElement('div');
        div.textContent = value == null ? '' : String(value);
        return div.innerHTML;
    }

    function formatDateTime(value) {
        if (!value) {
            return '—';
        }
        try {
            return new Date(value).toLocaleString();
        } catch (e) {
            return String(value);
        }
    }

    function formatShortTime(value) {
        if (!value) {
            return '—';
        }
        try {
            return new Date(value).toLocaleString(undefined, {
                month: 'short',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit'
            });
        } catch (e) {
            return String(value);
        }
    }

    function statusBadge(status) {
        const map = {
            SCHEDULED: 'primary',
            IN_TRANSIT: 'info',
            COMPLETED: 'success',
            CANCELLED: 'dark'
        };
        const cls = map[status] || 'secondary';
        return '<span class="badge badge-' + cls + '">' + escapeHtml(status || '—') + '</span>';
    }

    function employeeName(emp) {
        if (!emp) {
            return '—';
        }
        return escapeHtml((emp.firstName || '') + ' ' + (emp.lastName || '')).trim() || ('#' + emp.id);
    }

    function employeeLabel(id) {
        const emp = employeesById[id];
        if (emp) {
            return employeeName(emp) + ' <span class="text-muted">#' + id + '</span>';
        }
        return '#' + id;
    }

    function route(from, to) {
        return escapeHtml((from || '?') + ' → ' + (to || '?'));
    }

    function cancelButton(logId) {
        if (!logId) {
            return '';
        }
        return '<button type="button" class="btn btn-xs btn-outline-danger cancel-reloc-btn" data-id="' +
            logId + '">Cancel</button>';
    }

    function renderPending(list) {
        const tbody = document.getElementById('pending-body');
        document.getElementById('pending-count').textContent = String(list.length);
        if (!list.length) {
            tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">No pending</td></tr>';
            return;
        }
        tbody.innerHTML = list.map(function (emp) {
            const logId = openLogByEmployeeId[emp.id];
            return (
                '<tr>' +
                '<td><a href="/employees/' + emp.id + '/view">' + employeeName(emp) + '</a></td>' +
                '<td>' + route(emp.currentDepartment, emp.targetDepartment) + '</td>' +
                '<td>' + (emp.orderItemsRemaining ?? 0) + '</td>' +
                '<td class="text-right">' + cancelButton(logId) + '</td>' +
                '</tr>'
            );
        }).join('');
    }

    function renderTransit(list) {
        const tbody = document.getElementById('transit-body');
        document.getElementById('transit-count').textContent = String(list.length);
        if (!list.length) {
            tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">Nobody in transit</td></tr>';
            return;
        }
        tbody.innerHTML = list.map(function (emp) {
            const logId = openLogByEmployeeId[emp.id];
            return (
                '<tr>' +
                '<td><a href="/employees/' + emp.id + '/view">' + employeeName(emp) + '</a></td>' +
                '<td>' + route(emp.currentDepartment, emp.targetDepartment) + '</td>' +
                '<td class="text-nowrap">' + formatDateTime(emp.relocatingUntil) + '</td>' +
                '<td class="text-right">' + cancelButton(logId) + '</td>' +
                '</tr>'
            );
        }).join('');
    }

    function renderHistory(logs) {
        const tbody = document.getElementById('history-body');
        document.getElementById('history-count').textContent = String(logs.length);
        if (!logs.length) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-3">No history</td></tr>';
            return;
        }
        tbody.innerHTML = logs.map(function (log) {
            const when = log.arrivedAt || log.transitStartedAt || log.scheduledAt;
            const open = log.status === 'SCHEDULED' || log.status === 'IN_TRANSIT';
            const iph = Number(log.iphAtSource || 0).toFixed(0) + ' → ' +
                Number(log.predictedIphAtTarget || 0).toFixed(0) +
                (log.iphOnArrival != null ? ' (arr ' + Number(log.iphOnArrival).toFixed(0) + ')' : '');
            return (
                '<tr>' +
                '<td class="text-nowrap">' + formatShortTime(when) + '</td>' +
                '<td><a href="/employees/' + log.employeeId + '/view">' +
                employeeLabel(log.employeeId) + '</a></td>' +
                '<td>' + route(log.fromDepartment, log.toDepartment) + '</td>' +
                '<td>' + statusBadge(log.status) + '</td>' +
                '<td class="text-nowrap small">' + escapeHtml(iph) + '</td>' +
                '<td class="text-right">' + (open ? cancelButton(log.id) : '') + '</td>' +
                '</tr>'
            );
        }).join('');
    }

    function fillForceSelects(employees, departments) {
        const empSelect = document.getElementById('force-employee');
        const deptSelect = document.getElementById('force-department');
        const empVal = empSelect.value;
        const deptVal = deptSelect.value;

        empSelect.innerHTML = '<option value="">— select —</option>' +
            employees.map(function (emp) {
                return '<option value="' + emp.id + '">' +
                    escapeHtml(emp.firstName + ' ' + emp.lastName) +
                    ' (' + escapeHtml(emp.currentDepartment || '?') + ')</option>';
            }).join('');

        deptSelect.innerHTML = '<option value="">— select —</option>' +
            departments.map(function (dept) {
                return '<option value="' + dept.id + '">' + escapeHtml(dept.name) + '</option>';
            }).join('');

        if (empVal) {
            empSelect.value = empVal;
        }
        if (deptVal) {
            deptSelect.value = deptVal;
        }
    }

    function bindCancelButtons(root) {
        root.querySelectorAll('.cancel-reloc-btn').forEach(function (btn) {
            btn.addEventListener('click', function () {
                const id = Number(btn.getAttribute('data-id'));
                if (!id) {
                    return;
                }
                btn.disabled = true;
                WarehouseApi.post('/relocations/' + id + '/cancel')
                    .then(function () {
                        WarehouseApi.showToast('Relocation cancelled', 'success');
                        return refresh();
                    })
                    .catch(function () { /* toast shown */ })
                    .finally(function () {
                        btn.disabled = false;
                    });
            });
        });
    }

    function indexOpenLogs(history) {
        openLogByEmployeeId = {};
        history.forEach(function (log) {
            if (log.status === 'SCHEDULED' || log.status === 'IN_TRANSIT') {
                openLogByEmployeeId[log.employeeId] = log.id;
            }
        });
    }

    async function refresh() {
        const loading = document.getElementById('relocations-loading');
        loading.classList.remove('d-none');
        try {
            const [pending, transit, history, employees, departments] = await Promise.all([
                WarehouseApi.get('/relocations/pending'),
                WarehouseApi.get('/relocations/in-transit'),
                WarehouseApi.get('/relocations/history?limit=80'),
                WarehouseApi.get('/employees'),
                WarehouseApi.get('/departments')
            ]);

            employeesById = {};
            employees.forEach(function (emp) {
                employeesById[emp.id] = emp;
            });
            indexOpenLogs(history);
            fillForceSelects(employees, departments);
            renderPending(pending);
            renderTransit(transit);
            renderHistory(history);

            const page = document.getElementById('relocations-page');
            bindCancelButtons(page);
        } catch (err) {
            document.getElementById('pending-body').innerHTML =
                '<tr><td colspan="4" class="text-center text-danger py-3">' +
                escapeHtml(err.message || 'Failed to load') + '</td></tr>';
        } finally {
            loading.classList.add('d-none');
        }
    }

    async function forceRelocate() {
        const employeeId = Number(document.getElementById('force-employee').value);
        const toDepartmentId = Number(document.getElementById('force-department').value);
        if (!employeeId || !toDepartmentId) {
            WarehouseApi.showToast('Select employee and target department', 'danger');
            return;
        }
        await WarehouseApi.post('/relocations', { employeeId: employeeId, toDepartmentId: toDepartmentId });
        WarehouseApi.showToast('Relocation scheduled', 'success');
        await refresh();
    }

    async function balanceNow() {
        const result = await WarehouseApi.post('/relocations/balance');
        const n = result && result.scheduled != null ? result.scheduled : 0;
        WarehouseApi.showToast('Balance scheduled ' + n + ' move(s)', 'success');
        await refresh();
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (!document.getElementById('relocations-page')) {
            return;
        }

        document.getElementById('relocations-refresh-btn').addEventListener('click', function () {
            refresh().catch(function () { /* toast shown */ });
        });
        document.getElementById('relocations-balance-btn').addEventListener('click', function () {
            balanceNow().catch(function () { /* toast shown */ });
        });
        document.getElementById('force-relocate-btn').addEventListener('click', function () {
            forceRelocate().catch(function () { /* toast shown */ });
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
