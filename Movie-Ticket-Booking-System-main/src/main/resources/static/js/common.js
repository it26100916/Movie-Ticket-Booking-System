/* ===== Shared front-end helpers: API calls, navigation, auth guard, UI utilities ===== */

const API = {
  async call(method, url, body) {
    const opts = { method, headers: {}, credentials: 'same-origin' };
    if (body !== undefined) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
    const res = await fetch(url, opts);
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (e) { data = text; }
    if (!res.ok) {
      const message = (data && data.message) || `Request failed (${res.status})`;
      const err = new Error(message);
      err.status = res.status;
      throw err;
    }
    return data;
  },
  get(url) { return this.call('GET', url); },
  post(url, body) { return this.call('POST', url, body ?? {}); },
  put(url, body) { return this.call('PUT', url, body); },
  del(url) { return this.call('DELETE', url); }
};

/** Shows a short notification in the corner. type: 'ok' | 'err' */
function toast(message, type = 'ok') {
  let wrap = document.querySelector('.toast-wrap');
  if (!wrap) {
    wrap = document.createElement('div');
    wrap.className = 'toast-wrap';
    document.body.appendChild(wrap);
  }
  const el = document.createElement('div');
  el.className = `toast ${type === 'err' ? 'err' : 'ok'}`;
  el.textContent = message;
  wrap.appendChild(el);
  setTimeout(() => el.remove(), 3800);
}

/** Escapes text before putting it into innerHTML. */
function esc(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function money(value) {
  const n = Number(value || 0);
  return 'LKR ' + n.toLocaleString('en-LK', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function fmtDateTime(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleString('en-GB', { weekday: 'short', day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
}

function fmtDate(iso) {
  if (!iso) return '';
  return new Date(iso + (iso.length === 10 ? 'T00:00:00' : '')).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}

function fmtTime(iso) {
  return new Date(iso).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
}

function dayKey(iso) {
  const d = new Date(iso);
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
}

function formatBadge(format) {
  const map = { STANDARD: ['badge-std', '2D'], IMAX: ['badge-imax', 'IMAX'], THREE_D: ['badge-3d', '3D'] };
  const [cls, label] = map[format] || ['badge-muted', format];
  return `<span class="badge ${cls}">${esc(label)}</span>`;
}

function statusBadge(status) {
  const map = {
    PENDING_PAYMENT: ['badge-warn', 'Awaiting payment'],
    CONFIRMED: ['badge-ok', 'Confirmed'],
    CANCELLED: ['badge-bad', 'Cancelled'],
    SUCCESS: ['badge-ok', 'Paid'],
    REFUNDED: ['badge-muted', 'Refunded'],
    PARTIALLY_REFUNDED: ['badge-warn', 'Partly refunded']
  };
  const [cls, label] = map[status] || ['badge-muted', status];
  return `<span class="badge ${cls}">${esc(label)}</span>`;
}

/** Query-string value of the current page, e.g. qs('showId'). */
function qs(name) {
  return new URLSearchParams(window.location.search).get(name);
}

/** Returns the logged-in user, or null. */
async function currentUser() {
  try { return await API.get('/api/auth/me'); } catch (e) { return null; }
}

/** Redirects to the right login page when the user does not have the required role. */
async function requireRole(role, loginPage) {
  const me = await currentUser();
  if (!me || me.role !== role) {
    const next = encodeURIComponent(window.location.pathname.replace(/^\//, '') + window.location.search);
    window.location.href = `${loginPage}?next=${next}`;
    return null;
  }
  return me;
}

/** Builds the top navigation bar into <header id="topbar"></header>. */
async function renderNav(active) {
  const header = document.getElementById('topbar');
  if (!header) return;
  const me = await currentUser();
  const links = [
    ['index.html', 'Movies', 'movies'],
    ['theatres.html', 'Theatres', 'theatres']
  ];
  let right;
  if (me && me.role === 'CUSTOMER') {
    links.push(['my-bookings.html', 'My Bookings', 'bookings'], ['profile.html', 'Profile', 'profile']);
    right = `<span class="pill">Hi, ${esc(me.fullName.split(' ')[0])} &middot; ${me.loyaltyPoints ?? 0} pts</span>
             <button class="btn btn-ghost btn-sm" id="logoutBtn">Log out</button>`;
  } else if (me && me.role === 'ADMIN') {
    links.push(['admin-dashboard.html', 'Admin Dashboard', 'admin']);
    right = `<span class="pill">Admin &middot; ${esc(me.fullName)}</span>
             <button class="btn btn-ghost btn-sm" id="logoutBtn">Log out</button>`;
  } else {
    right = `<a class="btn btn-ghost btn-sm" href="customer-login.html">Customer log in</a>
             <a class="btn btn-primary btn-sm" href="admin-login.html">Admin</a>`;
  }
  header.innerHTML = `
    <div class="topbar-inner">
      <a class="brand" href="index.html"><span class="logo">🎬</span><span>CineMax</span></a>
      <nav class="nav-links">
        ${links.map(([href, label, key]) =>
          `<a href="${href}" class="${key === active ? 'active' : ''}">${label}</a>`).join('')}
      </nav>
      <div class="nav-right">${right}</div>
    </div>`;
  const logout = document.getElementById('logoutBtn');
  if (logout) {
    logout.addEventListener('click', async () => {
      try { await API.post('/api/auth/logout', {}); } catch (e) { /* ignore */ }
      window.location.href = 'index.html';
    });
  }
}

/** Safe redirect target from ?next= (only same-site relative pages are allowed). */
function nextPage(fallback) {
  const next = qs('next');
  if (next && /^[a-z0-9-]+\.html(\?[^\s]*)?$/i.test(next)) return next;
  return fallback;
}
