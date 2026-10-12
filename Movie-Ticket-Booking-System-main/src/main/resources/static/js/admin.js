/* ===== Admin dashboard =====
   One generic CRUD engine (table + modal form) is driven by the SECTIONS list below.
   Each section is owned by one team member (see the "owner" field), which keeps the
   front-end split the same as the back-end split. */

const SECTIONS = [
  {
    key: 'overview', title: 'Dashboard', icon: '📊', owner: '',
    ownerText: 'Summary of the whole system'
  },
  {
    key: 'customers', title: 'Customers', icon: '👥', owner: 'M1',
    ownerText: 'Member 1 · User & Authentication (customer READ / DELETE)',
    listEndpoint: '/api/admin/customers', endpoint: '/api/admin/customers', singular: 'customer',
    search: true, canCreate: false, canEdit: false, canDelete: true,
    columns: [
      { k: 'fullName', l: 'Name' }, { k: 'email', l: 'Email' }, { k: 'phone', l: 'Phone' },
      { k: 'loyaltyPoints', l: 'Points' }, { k: 'createdAt', l: 'Joined', f: v => fmtDate(v) }
    ]
  },
  {
    key: 'admins', title: 'Admins', icon: '🛡️', owner: 'M1',
    ownerText: 'Member 1 · User & Authentication (admin CRUD)',
    listEndpoint: '/api/admin/admins', endpoint: '/api/admin/admins', singular: 'admin',
    canCreate: true, canEdit: true, canDelete: true,
    editFields: ['permissionLevel'],
    columns: [
      { k: 'fullName', l: 'Name' }, { k: 'email', l: 'Email' },
      { k: 'permissionLevel', l: 'Permission', f: v => `<span class="badge ${v === 'SUPER_ADMIN' ? 'badge-imax' : 'badge-muted'}">${esc(v)}</span>` },
      { k: 'createdAt', l: 'Created', f: v => fmtDate(v) }
    ],
    fields: [
      { name: 'fullName', label: 'Full name', required: true, maxlength: 100 },
      { name: 'email', label: 'Email', type: 'email', required: true },
      { name: 'password', label: 'Password (min 6)', type: 'password', required: true },
      { name: 'permissionLevel', label: 'Permission level', type: 'select', required: true,
        options: [['STAFF', 'STAFF'], ['SUPER_ADMIN', 'SUPER_ADMIN']], default: 'STAFF' }
    ]
  },
  {
    key: 'movies', title: 'Movies', icon: '🎬', owner: 'M2',
    ownerText: 'Member 2 · Movie Catalogue (movie CRUD, subclasses by format)',
    listEndpoint: '/api/movies', endpoint: '/api/admin/movies', singular: 'movie',
    canCreate: true, canEdit: true, canDelete: true,
    columns: [
      { k: 'title', l: 'Title' }, { k: 'genre', l: 'Genre' },
      { k: 'format', l: 'Format', f: v => formatBadge(v) },
      { k: 'durationMinutes', l: 'Minutes' }, { k: 'rating', l: 'Rating' },
      { k: 'surcharge', l: 'Surcharge', f: v => money(v) }
    ],
    fields: [
      { name: 'title', label: 'Title', required: true, maxlength: 150 },
      { name: 'genre', label: 'Genre', required: true, maxlength: 60 },
      { name: 'language', label: 'Language', required: true, maxlength: 40 },
      { name: 'durationMinutes', label: 'Duration (minutes)', type: 'number', required: true, min: 30, max: 400 },
      { name: 'rating', label: 'Rating (U, PG, PG-13…)', maxlength: 10 },
      { name: 'format', label: 'Format', type: 'select', required: true,
        options: [['STANDARD', 'Standard 2D'], ['IMAX', 'IMAX (+LKR 300)'], ['THREE_D', '3D (+LKR 200)']] },
      { name: 'releaseDate', label: 'Release date', type: 'date' },
      { name: 'director', label: 'Director', maxlength: 150 },
      { name: 'producers', label: 'Producers (comma-separated)', maxlength: 300 },
      { name: 'writers', label: 'Writers (comma-separated)', maxlength: 300 },
      { name: 'castMembers', label: 'Cast (comma-separated)', maxlength: 500 },
      { name: 'trailerUrl', label: 'Trailer link (YouTube, optional)', maxlength: 500, full: true },
      { name: 'stillUrls', label: 'Scene image URLs (comma-separated, optional)', maxlength: 2000, full: true },
      { name: 'posterUrl', label: 'Poster image URL (optional)', maxlength: 500, full: true },
      { name: 'description', label: 'Description', type: 'textarea', maxlength: 1000, full: true }
    ]
  },
  {
    key: 'theatres', title: 'Theatres', icon: '🏛️', owner: 'M3',
    ownerText: 'Member 3 · Theatre & Screen (theatre CRUD)',
    listEndpoint: '/api/theatres', endpoint: '/api/admin/theatres', singular: 'theatre',
    canCreate: true, canEdit: true, canDelete: true,
    columns: [
      { k: 'name', l: 'Name' }, { k: 'city', l: 'City' }, { k: 'address', l: 'Address' }, { k: 'phone', l: 'Phone' }
    ],
    fields: [
      { name: 'name', label: 'Theatre name', required: true, maxlength: 100 },
      { name: 'city', label: 'City', required: true, maxlength: 60 },
      { name: 'address', label: 'Address', maxlength: 200, full: true },
      { name: 'phone', label: 'Phone', maxlength: 20 }
    ]
  },
  {
    key: 'screens', title: 'Screens', icon: '🖥️', owner: 'M3',
    ownerText: 'Member 3 · Theatre & Screen (screen CRUD, STANDARD / PREMIUM / VIP)',
    listEndpoint: '/api/screens', endpoint: '/api/admin/screens', singular: 'screen',
    canCreate: true, canEdit: true, canDelete: true,
    columns: [
      { k: 'name', l: 'Screen' }, { k: 'theatreName', l: 'Theatre' },
      { k: 'category', l: 'Category' }, { k: 'rowCount', l: 'Rows' }, { k: 'seatsPerRow', l: 'Seats/row' },
      { k: 'totalSeats', l: 'Total' }, { k: 'priceMultiplier', l: '× price' }
    ],
    fields: [
      { name: 'name', label: 'Screen name', required: true, maxlength: 60 },
      { name: 'theatreId', label: 'Theatre', type: 'select', required: true, numeric: true,
        optionsFrom: '/api/theatres', labelFn: t => t.name, valueFn: t => t.id },
      { name: 'screenType', label: 'Screen type', type: 'select', required: true,
        options: [['STANDARD', 'Standard 2D (×1.00)'], ['PREMIUM', 'Premium recliner (×1.30)'], ['VIP', 'VIP lounge (×1.80)']] },
      { name: 'rowCount', label: 'Rows (1–26)', type: 'number', required: true, min: 1, max: 26 },
      { name: 'seatsPerRow', label: 'Seats per row (1–30)', type: 'number', required: true, min: 1, max: 30 }
    ]
  },
  {
    key: 'shows', title: 'Showtimes', icon: '🗓️', owner: 'M4',
    ownerText: 'Member 4 · Showtime Scheduling (showtime CRUD, overlap checks, pricing)',
    listEndpoint: '/api/admin/shows', endpoint: '/api/admin/shows', singular: 'showtime',
    canCreate: true, canEdit: true, canDelete: true,
    columns: [
      { k: 'movieTitle', l: 'Movie' }, { k: 'theatreName', l: 'Theatre' }, { k: 'screenName', l: 'Screen' },
      { k: 'startTime', l: 'Starts', f: v => fmtDateTime(v) },
      { k: 'basePrice', l: 'Base', f: v => money(v) },
      { k: 'seatPrice', l: 'Seat price', f: v => money(v) },
      { k: 'pricingStrategy', l: 'Rule' }
    ],
    fields: [
      { name: 'movieId', label: 'Movie', type: 'select', required: true, numeric: true,
        optionsFrom: '/api/movies', labelFn: m => m.title, valueFn: m => m.id },
      { name: 'screenId', label: 'Screen', type: 'select', required: true, numeric: true,
        optionsFrom: '/api/screens', labelFn: s => `${s.theatreName} · ${s.name}`, valueFn: s => s.id },
      { name: 'startTime', label: 'Start time', type: 'datetime-local', required: true,
        get: r => r.startTime },
      { name: 'basePrice', label: 'Base price (LKR)', type: 'number', required: true, min: 1 }
    ]
  },
  {
    key: 'bookings', title: 'Bookings', icon: '🎟️', owner: 'M5',
    ownerText: 'Member 5 · Booking & Seat Reservation (admin READ / DELETE of cancelled bookings)',
    listEndpoint: '/api/admin/bookings', endpoint: '/api/admin/bookings', singular: 'booking',
    canCreate: false, canEdit: false, canDelete: true,
    canDeleteRow: r => r.status === 'CANCELLED',
    columns: [
      { k: 'reference', l: 'Reference' }, { k: 'customerName', l: 'Customer' }, { k: 'movieTitle', l: 'Movie' },
      { k: 'showTime', l: 'Show time', f: v => fmtDateTime(v) },
      { k: 'seatCodes', l: 'Seats', f: v => v.join(', ') },
      { k: 'totalAmount', l: 'Total', f: v => money(v) },
      { k: 'status', l: 'Status', f: v => statusBadge(v) }
    ]
  },
  {
    key: 'payments', title: 'Payments', icon: '💳', owner: 'M6',
    ownerText: 'Member 6 · Payment & Reports (payment READ / UPDATE refund / DELETE)',
    listEndpoint: '/api/admin/payments', endpoint: '/api/admin/payments', singular: 'payment',
    canCreate: false, canEdit: true, canDelete: true,
    canEditRow: r => r.status !== 'REFUNDED',
    canDeleteRow: r => r.status === 'REFUNDED',
    editFields: ['status'],
    columns: [
      { k: 'transactionRef', l: 'Transaction' }, { k: 'bookingReference', l: 'Booking' },
      { k: 'customerName', l: 'Customer' }, { k: 'method', l: 'Method' },
      { k: 'amount', l: 'Amount', f: v => money(v) },
      { k: 'refundedAmount', l: 'Refunded', f: v => money(v) },
      { k: 'status', l: 'Status', f: v => statusBadge(v) },
      { k: 'paidAt', l: 'Paid at', f: v => fmtDateTime(v) }
    ],
    fields: [
      { name: 'status', label: 'Status', type: 'select', required: true,
        options: [['REFUNDED', 'Record manual full refund']], get: () => 'REFUNDED',
        hint: 'Automatic refunds happen when a booking is cancelled. Use this for manual refunds only.' }
    ]
  },
  {
    key: 'files', title: 'Files & Reports', icon: '📁', owner: 'M6',
    ownerText: 'Member 6 · File handling (read seed text files, write sales reports)'
  }
];

const state = { section: null, rows: [], fileLog: '' };

const $ = id => document.getElementById(id);

/* ---------- boot ---------- */
(async function boot() {
  const me = await requireRole('ADMIN', 'admin-login.html');
  if (!me) return;
  $('sideNav').innerHTML = SECTIONS.map(s => `
    <button class="side-link" data-key="${s.key}">
      <span>${s.icon}</span><span>${esc(s.title)}</span>
    </button>`).join('');
  document.querySelectorAll('.side-link').forEach(b => b.addEventListener('click', () => { location.hash = b.dataset.key; }));
  $('logoutBtn').addEventListener('click', async () => {
    try { await API.post('/api/auth/logout', {}); } catch (e) { /* ignore */ }
    window.location.href = 'admin-login.html';
  });
  $('modalClose').addEventListener('click', closeModal);
  $('modal').addEventListener('click', e => { if (e.target.id === 'modal') closeModal(); });
  window.addEventListener('hashchange', () => showSection(location.hash.slice(1)));
  showSection(location.hash.slice(1));
})();

function showSection(key) {
  const section = SECTIONS.find(s => s.key === key) || SECTIONS[0];
  state.section = section;
  document.querySelectorAll('.side-link').forEach(b => b.classList.toggle('active', b.dataset.key === section.key));
  $('sectionTitle').textContent = section.title;
  const body = $('sectionBody');
  body.innerHTML = '<div class="loader">Loading…</div>';
  const run = section.key === 'overview' ? renderOverview(body)
    : section.key === 'files' ? renderFiles(body)
    : renderCrud(body, section);
  run.catch(err => { body.innerHTML = `<div class="card error-text">${esc(err.message)}</div>`; });
}

/* ---------- overview (summary cards) ---------- */
async function renderOverview(body) {
  const [movies, shows, bookings, customers, payments] = await Promise.all([
    API.get('/api/movies'), API.get('/api/admin/shows'), API.get('/api/admin/bookings'),
    API.get('/api/admin/customers'), API.get('/api/admin/payments')
  ]);
  const confirmed = bookings.filter(b => b.status === 'CONFIRMED').length;
  const net = payments.reduce((sum, p) => sum + Number(p.amount) - Number(p.refundedAmount), 0);
  const stat = (num, label) => `<div class="card stat"><span class="num gradient-text">${num}</span><span class="lbl">${label}</span></div>`;
  body.innerHTML = `
    <div class="grid">
      ${stat(movies.length, 'Movies')}
      ${stat(shows.length, 'Showtimes')}
      ${stat(confirmed, 'Confirmed bookings')}
      ${stat(customers.length, 'Customers')}
      ${stat(money(net), 'Net payments (after refunds)')}
    </div>
    <div class="section-head"><div><h2>Recent bookings</h2><p>Latest 8 reservations across all theatres.</p></div></div>
    <div class="table-wrap"><table class="table">
      <thead><tr><th>Reference</th><th>Customer</th><th>Movie</th><th>Show time</th><th>Seats</th><th>Total</th><th>Status</th></tr></thead>
      <tbody>${bookings.slice(0, 8).map(b => `<tr>
        <td>${esc(b.reference)}</td><td>${esc(b.customerName)}</td><td>${esc(b.movieTitle)}</td>
        <td>${fmtDateTime(b.showTime)}</td><td>${b.seatCodes.map(esc).join(', ')}</td>
        <td>${money(b.totalAmount)}</td><td>${statusBadge(b.status)}</td></tr>`).join('') ||
        '<tr><td colspan="7" class="empty">No bookings yet.</td></tr>'}</tbody></table></div>`;
}

/* ---------- generic CRUD table ---------- */
async function renderCrud(body, s) {
  state.rows = await API.get(s.listEndpoint);
  body.innerHTML = `
    <div class="toolbar">
      <div class="row">
        ${s.search ? '<input id="searchBox" placeholder="Search by name or email…" style="width:280px">' : ''}
        <span class="muted" id="rowCount">${state.rows.length} record(s)</span>
      </div>
      ${s.canCreate ? `<button class="btn btn-primary" id="newBtn">+ New ${esc(s.singular)}</button>` : ''}
    </div>
    <div class="table-wrap"><table class="table">
      <thead><tr>${s.columns.map(c => `<th>${esc(c.l)}</th>`).join('')}<th></th></tr></thead>
      <tbody id="tbody"></tbody>
    </table></div>`;
  drawRows(s, state.rows);
  if (s.canCreate) $('newBtn').addEventListener('click', () => openForm(s, null));
  if (s.search) {
    let timer;
    $('searchBox').addEventListener('input', e => {
      clearTimeout(timer);
      timer = setTimeout(async () => {
        const q = encodeURIComponent(e.target.value.trim());
        state.rows = await API.get(s.listEndpoint + '?q=' + q);
        drawRows(s, state.rows);
        $('rowCount').textContent = state.rows.length + ' record(s)';
      }, 250);
    });
  }
}

function drawRows(s, rows) {
  const tbody = $('tbody');
  const span = s.columns.length + 1;
  if (!rows.length) { tbody.innerHTML = `<tr><td colspan="${span}" class="empty">No records found.</td></tr>`; return; }
  tbody.innerHTML = rows.map(r => {
    const canEdit = s.canEdit && (!s.canEditRow || s.canEditRow(r));
    const canDelete = s.canDelete && (!s.canDeleteRow || s.canDeleteRow(r));
    return `<tr>
      ${s.columns.map(c => `<td>${c.f ? c.f(r[c.k], r) : esc(r[c.k] ?? '')}</td>`).join('')}
      <td class="actions">
        ${canEdit ? `<button class="btn btn-ghost btn-sm" data-edit="${r.id}">Edit</button>` : ''}
        ${canDelete ? `<button class="btn btn-danger btn-sm" data-del="${r.id}">Delete</button>` : ''}
      </td></tr>`;
  }).join('');
  tbody.querySelectorAll('[data-edit]').forEach(b => b.addEventListener('click',
    () => openForm(s, rows.find(x => String(x.id) === b.dataset.edit))));
  tbody.querySelectorAll('[data-del]').forEach(b => b.addEventListener('click',
    () => removeRow(s, rows.find(x => String(x.id) === b.dataset.del))));
}

/* ---------- create / update form ---------- */
async function openForm(s, row) {
  const fields = row && s.editFields ? s.fields.filter(f => s.editFields.includes(f.name)) : s.fields;
  for (const f of fields) {
    if (f.optionsFrom) {
      const list = await API.get(f.optionsFrom);
      f.loaded = list.map(item => [f.valueFn(item), f.labelFn(item)]);
    }
  }
  const form = $('modalForm');
  $('modalTitle').textContent = (row ? 'Edit ' : 'New ') + s.singular;
  form.innerHTML = `
    <div class="form-grid">${fields.map(f => fieldHtml(f, row)).join('')}</div>
    <div class="error-text" id="formError"></div>
    <div class="modal-foot">
      <button type="button" class="btn btn-ghost" id="formCancel">Cancel</button>
      <button type="submit" class="btn btn-primary" id="formSubmit">${row ? 'Save changes' : 'Create'}</button>
    </div>`;
  $('formCancel').addEventListener('click', closeModal);
  form.onsubmit = async (e) => {
    e.preventDefault();
    const errorBox = $('formError');
    errorBox.textContent = '';
    const body = {};
    for (const f of fields) {
      const el = form.querySelector(`[name="${f.name}"]`);
      let v = el.value;
      if (f.type === 'number') v = v === '' ? null : Number(v);
      else if (f.type === 'datetime-local') v = v ? v + ':00' : null;
      else v = v.trim() === '' ? null : v.trim();
      if (f.numeric && v !== null) v = Number(v);
      if (f.required && (v === null || v === '')) { errorBox.textContent = `${f.label} is required`; return; }
      body[f.name] = v;
    }
    const url = row ? `${s.endpoint}/${row.id}` : s.endpoint;
    $('formSubmit').disabled = true;
    try {
      if (row) await API.put(url, body); else await API.post(url, body);
      closeModal();
      toast(row ? 'Changes saved' : 'Created successfully');
      showSection(s.key);
    } catch (err) {
      errorBox.textContent = err.message;
    } finally {
      $('formSubmit').disabled = false;
    }
  };
  $('modal').classList.add('open');
}

function fieldHtml(f, row) {
  const raw = row ? (f.get ? f.get(row) : row[f.name]) : (f.default ?? '');
  let val = raw === null || raw === undefined ? '' : raw;
  if (f.type === 'datetime-local' && val) val = String(val).slice(0, 16);
  const req = f.required ? 'required' : '';
  const span = f.full ? ' full' : '';
  let input;
  if (f.type === 'select') {
    const options = f.loaded || f.options || [];
    const hasValue = options.some(([v]) => String(v) === String(val));
    input = `<select name="${f.name}" ${req}>
      ${!hasValue ? '<option value="" disabled selected>Choose…</option>' : ''}
      ${options.map(([v, l]) => `<option value="${esc(v)}" ${String(v) === String(val) ? 'selected' : ''}>${esc(l)}</option>`).join('')}
    </select>`;
  } else if (f.type === 'textarea') {
    input = `<textarea name="${f.name}" ${f.maxlength ? `maxlength="${f.maxlength}"` : ''}>${esc(val)}</textarea>`;
  } else {
    input = `<input name="${f.name}" type="${f.type || 'text'}" value="${esc(val)}" ${req}
      ${f.min !== undefined ? `min="${f.min}"` : ''} ${f.max !== undefined ? `max="${f.max}"` : ''}
      ${f.maxlength ? `maxlength="${f.maxlength}"` : ''} ${f.type === 'number' ? 'step="any"' : ''}>`;
  }
  return `<div class="field${span}"><label>${esc(f.label)}${f.required ? ' *' : ''}</label>${input}
          ${f.hint ? `<span class="hint">${esc(f.hint)}</span>` : ''}</div>`;
}

function closeModal() { $('modal').classList.remove('open'); }

/* ---------- delete ---------- */
async function removeRow(s, row) {
  if (!row) return;
  const label = row.title || row.name || row.fullName || row.reference || row.transactionRef || ('#' + row.id);
  if (!confirm(`Delete ${s.singular} "${label}"? This cannot be undone.`)) return;
  try {
    await API.del(`${s.endpoint}/${row.id}`);
    toast('Deleted');
    showSection(s.key);
  } catch (err) {
    toast(err.message, 'err');
  }
}

/* ---------- files: READ seed text files, WRITE sales report ---------- */
async function renderFiles(body) {
  body.innerHTML = `
    <div class="grid">
      <div class="card">
        <span class="badge badge-ok">READ</span>
        <h3 class="mt-1">Import movies</h3>
        <p>Reads <code>data/seed/movies.txt</code> (pipe-separated lines) and adds new movies. Duplicate titles are skipped.</p>
        <button class="btn btn-primary" data-run="import-movies">Import movies</button>
      </div>
      <div class="card">
        <span class="badge badge-ok">READ</span>
        <h3 class="mt-1">Import users</h3>
        <p>Reads <code>data/seed/users.txt</code> and creates customer and admin accounts. Passwords are stored as BCrypt hashes.</p>
        <button class="btn btn-primary" data-run="import-users">Import users</button>
      </div>
      <div class="card">
        <span class="badge badge-warn">WRITE</span>
        <h3 class="mt-1">Sales report</h3>
        <p>Writes a timestamped text report to <code>data/reports/</code> and downloads it.</p>
        <a class="btn btn-primary" href="/api/admin/files/export-sales">Download sales report</a>
      </div>
    </div>
    <div class="section-head"><h2>Last result</h2></div>
    <pre class="card out" id="fileOut">${esc(state.fileLog || 'Nothing run yet.')}</pre>`;
  body.querySelectorAll('[data-run]').forEach(btn => btn.addEventListener('click', async () => {
    btn.disabled = true;
    try {
      const result = await API.post('/api/admin/files/' + btn.dataset.run, {});
      state.fileLog = JSON.stringify(result, null, 2);
      toast(`Imported ${result.imported}, skipped ${result.skipped}`);
    } catch (err) {
      state.fileLog = 'Error: ' + err.message;
      toast(err.message, 'err');
    } finally {
      btn.disabled = false;
      $('fileOut').textContent = state.fileLog;
    }
  }));
}
