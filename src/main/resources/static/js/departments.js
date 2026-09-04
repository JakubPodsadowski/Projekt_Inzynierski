/**
 * Departments page: list, PATCH edit, demo sabotage shortcuts.
 */
(function () {
    let departments = [];

    function formatNumber(value, decimals) {
        if (value == null || Number.isNaN(value)) {
            return '—';
        }
        return Number(value).toFixed(decimals);
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

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function findDepartment(id) {
        return departments.find(function (dept) {
            return dept.id === id;
        });
    }

    function renderTable() {
        const tbody = document.getElementById('departments-table-body');
        if (!tbody) {
            return;
        }

        if (departments.length === 0) {
            tbody.innerHTML =
                '<tr><td colspan="8" class="text-center text-muted py-4">No departments</td></tr>';
            return;
        }

        tbody.innerHTML = departments.map(function (dept) {
            const cls = deficitClass(dept.deficit);
            return '<tr>' +
                '<td>' + escapeHtml(dept.name) + '</td>' +
                '<td class="text-right">' + formatNumber(dept.currentIph, 1) + '</td>' +
                '<td class="text-right">' + formatNumber(dept.requiredIph, 1) + '</td>' +
                '<td class="text-right">' + formatNumber(dept.performanceMultiplier, 2) + '</td>' +
                '<td class="text-right ' + cls + '">' + formatNumber(dept.deficit, 1) + '</td>' +
                '<td class="text-right">' + dept.targetDailyItems + '</td>' +
                '<td class="text-right">' + dept.employeeCount + '</td>' +
                '<td class="text-right">' +
                '<button type="button" class="btn btn-xs btn-outline-primary dept-edit-btn" ' +
                'data-id="' + dept.id + '"><i class="fas fa-edit"></i> Edit</button>' +
                '</td>' +
                '</tr>';
        }).join('');

        tbody.querySelectorAll('.dept-edit-btn').forEach(function (btn) {
            btn.addEventListener('click', function () {
                openEditModal(Number(btn.getAttribute('data-id')));
            });
        });
    }

    function renderDemoSelect() {
        const select = document.getElementById('demo-dept-select');
        if (!select) {
            return;
        }

        const current = select.value;
        select.innerHTML = '<option value="">— select —</option>' +
            departments.map(function (dept) {
                return '<option value="' + dept.id + '">' + escapeHtml(dept.name) + '</option>';
            }).join('');

        if (current) {
            select.value = current;
        }
    }

    async function loadDepartments() {
        departments = await WarehouseApi.get('/departments?sort=deficit,desc');
        renderTable();
        renderDemoSelect();
    }

    function openEditModal(id) {
        const dept = findDepartment(id);
        if (!dept) {
            return;
        }

        document.getElementById('dept-edit-id').value = dept.id;
        document.getElementById('dept-edit-name').textContent = dept.name;
        document.getElementById('dept-edit-required-iph').value = dept.requiredIph;
        document.getElementById('dept-edit-target-items').value = dept.targetDailyItems;
        document.getElementById('dept-edit-multiplier').value = dept.performanceMultiplier;
        $('#dept-edit-modal').modal('show');
    }

    async function saveEdit() {
        const id = Number(document.getElementById('dept-edit-id').value);
        const body = {
            requiredIph: Number(document.getElementById('dept-edit-required-iph').value),
            targetDailyItems: Number(document.getElementById('dept-edit-target-items').value),
            performanceMultiplier: Number(document.getElementById('dept-edit-multiplier').value)
        };

        await WarehouseApi.patch('/departments/' + id, body);
        $('#dept-edit-modal').modal('hide');
        WarehouseApi.showToast('Department updated', 'success');
        await loadDepartments();
    }

    function getSelectedDemoDepartment() {
        const select = document.getElementById('demo-dept-select');
        const id = Number(select.value);
        if (!id) {
            WarehouseApi.showToast('Select a department first', 'danger');
            return null;
        }
        return findDepartment(id);
    }

    async function applyBump() {
        const dept = getSelectedDemoDepartment();
        if (!dept) {
            return;
        }

        const bump = Number(document.getElementById('demo-bump-amount').value);
        if (!bump || bump <= 0) {
            WarehouseApi.showToast('Bump amount must be positive', 'danger');
            return;
        }

        await WarehouseApi.patch('/departments/' + dept.id, {
            requiredIph: dept.requiredIph + bump
        });
        WarehouseApi.showToast('Required IPH increased by ' + bump, 'success');
        await loadDepartments();
    }

    async function applyTargetDeficit() {
        const dept = getSelectedDemoDepartment();
        if (!dept) {
            return;
        }

        const targetDeficit = Number(document.getElementById('demo-target-deficit').value);
        if (Number.isNaN(targetDeficit)) {
            WarehouseApi.showToast('Enter a valid deficit value', 'danger');
            return;
        }

        const requiredIph = targetDeficit + (dept.currentIph * dept.performanceMultiplier);
        await WarehouseApi.patch('/departments/' + dept.id, {
            requiredIph: Math.max(0, requiredIph)
        });
        WarehouseApi.showToast('Deficit set to ~' + targetDeficit, 'success');
        await loadDepartments();
    }

    function bindEvents() {
        const refreshBtn = document.getElementById('departments-refresh-btn');
        if (refreshBtn) {
            refreshBtn.addEventListener('click', function () {
                loadDepartments().catch(function () { /* toast shown */ });
            });
        }

        const saveBtn = document.getElementById('dept-edit-save-btn');
        if (saveBtn) {
            saveBtn.addEventListener('click', function () {
                saveEdit().catch(function () { /* toast shown */ });
            });
        }

        const bumpBtn = document.getElementById('demo-bump-btn');
        if (bumpBtn) {
            bumpBtn.addEventListener('click', function () {
                applyBump().catch(function () { /* toast shown */ });
            });
        }

        const deficitBtn = document.getElementById('demo-deficit-btn');
        if (deficitBtn) {
            deficitBtn.addEventListener('click', function () {
                applyTargetDeficit().catch(function () { /* toast shown */ });
            });
        }
    }

    function init() {
        if (!document.getElementById('departments-page')) {
            return;
        }

        bindEvents();
        loadDepartments().catch(function () { /* toast shown */ });
    }

    document.addEventListener('DOMContentLoaded', init);
})();
