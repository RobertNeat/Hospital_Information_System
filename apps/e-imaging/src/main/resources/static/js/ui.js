// Nawigacja bez przeladowania (jak SPA): naglowek zostaje, podmieniana jest tylko tresc strony.
// Bez JS linki i formularze dzialaja klasycznie.
(function () {
    const swap = (html, url, push) => {
        const doc = new DOMParser().parseFromString(html, 'text/html');
        for (const id of ['nav-tabs', 'app']) {
            document.getElementById(id).replaceWith(doc.getElementById(id));
        }
        document.title = doc.title;
        if (push) history.pushState(null, '', url);
    };
    const load = async (url, options, push) => {
        try {
            const res = await fetch(url, options);
            const html = await res.text();
            swap(html, res.url, push);
        } catch (e) {
            location.href = url;
        }
    };
    document.addEventListener('click', (ev) => {
        const a = ev.target.closest('a[data-spa]');
        if (a && !ev.metaKey && !ev.ctrlKey && !ev.shiftKey) {
            ev.preventDefault();
            load(a.href, undefined, true);
        }
    });
    document.addEventListener('submit', (ev) => {
        const form = ev.target;
        if (form.method !== 'post') return;
        ev.preventDefault();
        load(form.action, { method: 'POST', body: new URLSearchParams(new FormData(form)) }, true);
    });
    window.addEventListener('popstate', () => load(location.href, undefined, false));
})();
