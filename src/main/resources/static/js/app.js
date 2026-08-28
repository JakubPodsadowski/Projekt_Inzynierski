/**
 * Global UI helpers (mode badge wired in part 2).
 */
document.addEventListener('DOMContentLoaded', function () {
    const label = document.getElementById('mode-label');
    if (label && label.textContent.trim() === '—') {
        label.textContent = 'UI';
        label.classList.add('badge', 'badge-secondary');
    }
});
