// static/js/theme.js
(function () {
    const body      = document.body;
    const toggleBtn = document.getElementById('themeToggle');
    const overlay   = document.getElementById('themeTransitionOverlay');

    // 统一一个 key：选手端 / 后台共用同一套偏好
    const THEME_KEY = 'tournament-theme-preference';
    let themeSource = 'system'; // 'system' | 'user'
    let currentTheme = 'light';

    const mediaQuery = window.matchMedia
        ? window.matchMedia('(prefers-color-scheme: dark)')
        : null;

    function getSystemTheme() {
        return mediaQuery && mediaQuery.matches ? 'dark' : 'light';
    }

    function applyTheme(theme) {
        currentTheme = theme === 'dark' ? 'dark' : 'light';
        body.setAttribute('data-theme', currentTheme);
    }

    function animateThemeChange(targetTheme, origin) {
        if (!overlay) {
            applyTheme(targetTheme);
            return;
        }

        const docRect   = document.documentElement.getBoundingClientRect();
        const maxRadius = Math.hypot(docRect.width, docRect.height);

        const cx = origin && typeof origin.x === 'number'
            ? origin.x
            : docRect.width / 2;
        const cy = origin && typeof origin.y === 'number'
            ? origin.y
            : docRect.height * 0.25;

        overlay.classList.remove('overlay-to-dark', 'overlay-to-light');
        overlay.style.transition = 'none';

        if (targetTheme === 'dark') {
            overlay.classList.add('overlay-to-dark');
        } else {
            overlay.classList.add('overlay-to-light');
        }

        overlay.style.opacity  = '1';
        overlay.style.clipPath = `circle(0px at ${cx}px ${cy}px)`;

        requestAnimationFrame(function () {
            requestAnimationFrame(function () {
                overlay.style.transition = 'clip-path 650ms ease-out, opacity 650ms ease-out';
                overlay.style.clipPath   = `circle(${maxRadius}px at ${cx}px ${cy}px)`;
            });
        });

        function onEnd(e) {
            if (e.propertyName !== 'clip-path') return;
            overlay.removeEventListener('transitionend', onEnd);
            applyTheme(targetTheme);
            overlay.style.opacity = '0';
        }
        overlay.addEventListener('transitionend', onEnd);
    }

    // 初始化主题
    (function initTheme() {
        try {
            const stored = localStorage.getItem(THEME_KEY);
            if (stored === 'light' || stored === 'dark') {
                themeSource = 'user';
                applyTheme(stored);
            } else {
                themeSource = 'system';
                applyTheme(getSystemTheme());
            }
        } catch (e) {
            applyTheme(getSystemTheme());
        }
    })();

    // 点击按钮切换
    if (toggleBtn) {
        toggleBtn.addEventListener('click', function (ev) {
            const nextTheme = currentTheme === 'dark' ? 'light' : 'dark';
            themeSource = 'user';
            try {
                localStorage.setItem(THEME_KEY, nextTheme);
            } catch (e) {}
            animateThemeChange(nextTheme, { x: ev.clientX, y: ev.clientY });
        });
    }

    // 跟随系统偏好
    if (mediaQuery && mediaQuery.addEventListener) {
        mediaQuery.addEventListener('change', function (e) {
            if (themeSource !== 'system') return;
            const newTheme = e.matches ? 'dark' : 'light';
            animateThemeChange(newTheme, null);
        });
    }
})();
