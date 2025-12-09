// static/js/effects.js
(function () {
    const body = document.body;

    // 光标柔光（仅桌面端）
    document.addEventListener('DOMContentLoaded', function () {
        try {
            if (window.matchMedia && !window.matchMedia('(pointer: fine)').matches) {
                return;
            }
        } catch (e) {}

        const glow = document.getElementById('cursorGlow');
        if (!glow) return;

        document.addEventListener('pointermove', function (e) {
            if (e.pointerType && e.pointerType !== 'mouse') return;

            const theme = body.getAttribute('data-theme') || 'light';
            if (theme === 'light') {
                glow.style.opacity = '1';
                glow.style.left = e.clientX + 'px';
                glow.style.top  = e.clientY + 'px';
            } else {
                glow.style.opacity = '0';
            }
        });

        document.addEventListener('mouseleave', function () {
            glow.style.opacity = '0';
        });
    });

    // 所有 .btn + 主题按钮 绑定水波纹
    document.addEventListener('DOMContentLoaded', function () {
        const buttons = document.querySelectorAll('.btn, .theme-toggle');
        buttons.forEach(function (btn) {
            if (btn.dataset.rippleBound === '1') return;
            btn.dataset.rippleBound = '1';

            btn.addEventListener('click', function (ev) {
                const rect = btn.getBoundingClientRect();
                const ripple = document.createElement('span');
                ripple.className = 'ripple-effect';
                ripple.style.left = (ev.clientX - rect.left) + 'px';
                ripple.style.top  = (ev.clientY - rect.top) + 'px';
                btn.appendChild(ripple);
                setTimeout(function () {
                    ripple.remove();
                }, 650);
            });
        });
    });
})();
