(function () {
    'use strict';

    const POLL_MS = 2500;
    const employeeId = window.EMPLOYEE_ID;
    let timer = null;
    let lastNotifications = [];

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

    function relocBadge(status) {
        const map = {
            SCHEDULED: 'primary',
            IN_TRANSIT: 'info',
            COMPLETED: 'success',
            CANCELLED: 'dark'
        };
        const cls = map[status] || 'secondary';
        return '<span class="badge badge-' + cls + '">' + escapeHtml(status || '—') + '</span>';
    }

    function activityIcon(type) {
        if (!type) {
            return 'fa-circle';
        }
        if (type.indexOf('PICK') === 0) {
            return 'fa-box';
        }
        if (type.indexOf('PACK') === 0) {
            return 'fa-box-open';
        }
        if (type.indexOf('PUTAWAY') === 0) {
            return 'fa-dolly';
        }
        return 'fa-circle';
    }

    function activityLabel(activity) {
        if (activity.message) {
            return escapeHtml(activity.message);
        }
        const parts = [escapeHtml(activity.type || 'ACTIVITY')];
        if (activity.quantity) {
            parts.push('×' + activity.quantity);
        }
        if (activity.productSku) {
            parts.push(escapeHtml(activity.productSku));
        }
        if (activity.orderNumber) {
            parts.push('order ' + escapeHtml(activity.orderNumber));
        }
        if (activity.receiptNumber) {
            parts.push('receipt ' + escapeHtml(activity.receiptNumber));
        }
        if (activity.locationCode) {
            parts.push('@ ' + escapeHtml(activity.locationCode));
        }
        return parts.join(' · ');
    }

    function renderHeader(emp) {
        document.getElementById('employee-header').classList.remove('d-none');
        document.getElementById('emp-name').textContent = emp.firstName + ' ' + emp.lastName;
        document.getElementById('emp-id').textContent = emp.id;
        document.getElementById('emp-dept').textContent = emp.currentDepartment || '—';
        document.getElementById('emp-iph').textContent = Number(emp.currentIph || 0).toFixed(1);
        document.getElementById('emp-break-tier').textContent = emp.breakTier || '—';
        document.getElementById('emp-items').textContent = emp.orderItemsRemaining ?? 0;

        const onBreak = document.getElementById('emp-on-break');
        onBreak.classList.toggle('d-none', !emp.onBreak);

        const statusEl = document.getElementById('emp-reloc-status');
        statusEl.className = 'badge';
        const status = emp.relocationStatus || 'NONE';
        statusEl.classList.add(
            status === 'IN_TRANSIT' ? 'badge-info'
                : status === 'SCHEDULED' ? 'badge-primary'
                    : 'badge-secondary'
        );
        statusEl.textContent = status;

        document.getElementById('emp-reloc-target').textContent = emp.targetDepartment || '—';
        document.getElementById('emp-reloc-until').textContent = formatDateTime(emp.relocatingUntil);
        document.getElementById('emp-reloc-count').textContent = emp.relocationCountToday ?? 0;
        document.getElementById('emp-reloc-last').textContent = formatDateTime(emp.lastRelocatedAt);

        const title = emp.firstName + ' ' + emp.lastName;
        document.title = title + ' | Warehouse';
        const h1 = document.querySelector('.content-header h1');
        if (h1) {
            h1.textContent = title;
        }
    }

    function renderActivities(activities) {
        const list = document.getElementById('activity-timeline');
        document.getElementById('activity-count').textContent = String(activities.length);

        if (!activities.length) {
            list.innerHTML = '<li class="text-muted text-center py-4">No activity yet</li>';
            return;
        }

        list.innerHTML = activities.map(function (a) {
            return (
                '<li class="activity-item">' +
                '<div class="activity-icon"><i class="fas ' + activityIcon(a.type) + '"></i></div>' +
                '<div class="activity-body">' +
                '<div class="activity-meta">' +
                '<span class="badge badge-light mr-1">' + escapeHtml(a.type || '') + '</span>' +
                '<span class="text-muted">' + formatShortTime(a.occurredAt) + '</span>' +
                (a.departmentName
                    ? ' <span class="text-muted">· ' + escapeHtml(a.departmentName) + '</span>'
                    : '') +
                '</div>' +
                '<div class="activity-text">' + activityLabel(a) + '</div>' +
                '</div>' +
                '</li>'
            );
        }).join('');
    }

    function renderRelocations(logs) {
        const tbody = document.getElementById('relocation-body');
        document.getElementById('relocation-count').textContent = String(logs.length);

        if (!logs.length) {
            tbody.innerHTML = '<tr><td colspan="3" class="text-center text-muted py-3">No relocations</td></tr>';
            return;
        }

        tbody.innerHTML = logs.map(function (log) {
            const when = log.arrivedAt || log.transitStartedAt || log.scheduledAt;
            const route = escapeHtml((log.fromDepartment || '?') + ' → ' + (log.toDepartment || '?'));
            return (
                '<tr>' +
                '<td class="text-nowrap">' + formatShortTime(when) + '</td>' +
                '<td>' + route + '</td>' +
                '<td>' + relocBadge(log.status) + '</td>' +
                '</tr>'
            );
        }).join('');
    }

    function renderNotifications(notifications) {
        lastNotifications = notifications || [];
        const list = document.getElementById('notification-list');
        const unread = lastNotifications.filter(function (n) { return !n.read; });
        const markBtn = document.getElementById('mark-all-read');
        markBtn.disabled = unread.length === 0;

        if (!lastNotifications.length) {
            list.innerHTML = '<li class="list-group-item text-muted text-center">No notifications</li>';
            return;
        }

        list.innerHTML = lastNotifications.map(function (n) {
            const unreadCls = n.read ? '' : ' notification-unread';
            return (
                '<li class="list-group-item notification-item' + unreadCls + '" data-id="' + n.id + '">' +
                '<div class="d-flex justify-content-between align-items-start">' +
                '<div>' +
                '<span class="badge badge-secondary mb-1">' + escapeHtml(n.type || '') + '</span>' +
                '<div class="notification-msg">' + escapeHtml(n.message || '') + '</div>' +
                '<small class="text-muted">' + formatShortTime(n.createdAt) + '</small>' +
                '</div>' +
                (n.read
                    ? ''
                    : '<button type="button" class="btn btn-xs btn-outline-primary mark-read-btn" data-id="' +
                      n.id + '">Read</button>') +
                '</div>' +
                '</li>'
            );
        }).join('');

        list.querySelectorAll('.mark-read-btn').forEach(function (btn) {
            btn.addEventListener('click', function () {
                const id = Number(btn.getAttribute('data-id'));
                WarehouseApi.post('/employees/' + employeeId + '/notifications/' + id + '/read')
                    .then(function () {
                        return refresh();
                    })
                    .catch(function () { /* toast shown */ });
            });
        });
    }

    async function refresh() {
        if (!employeeId) {
            showError('Missing employee id');
            return;
        }

        const loading = document.getElementById('employee-loading');
        loading.classList.remove('d-none');

        try {
            const [emp, activities, relocations, notifications] = await Promise.all([
                WarehouseApi.get('/employees/' + employeeId),
                WarehouseApi.get('/employees/' + employeeId + '/activities?limit=80'),
                WarehouseApi.get('/employees/' + employeeId + '/relocations'),
                WarehouseApi.get('/employees/' + employeeId + '/notifications?limit=40')
            ]);

            document.getElementById('employee-error').classList.add('d-none');
            renderHeader(emp);
            renderActivities(activities);
            renderRelocations(relocations);
            renderNotifications(notifications);
        } catch (err) {
            showError(err.message || 'Failed to load employee');
        } finally {
            loading.classList.add('d-none');
        }
    }

    function showError(message) {
        const el = document.getElementById('employee-error');
        el.textContent = message;
        el.classList.remove('d-none');
    }

    async function markAllRead() {
        const unread = lastNotifications.filter(function (n) { return !n.read; });
        for (let i = 0; i < unread.length; i++) {
            await WarehouseApi.post(
                '/employees/' + employeeId + '/notifications/' + unread[i].id + '/read'
            );
        }
        await refresh();
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (!document.getElementById('employee-header')) {
            return;
        }

        document.getElementById('mark-all-read').addEventListener('click', function () {
            markAllRead().catch(function () { /* toast shown */ });
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
