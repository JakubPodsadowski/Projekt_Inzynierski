/**
 * Global UI bootstrap — simulation panel owns mode/demo visibility.
 */
document.addEventListener('DOMContentLoaded', function () {
    if (typeof WarehouseSimulation !== 'undefined') {
        WarehouseSimulation.init();
    }
});
