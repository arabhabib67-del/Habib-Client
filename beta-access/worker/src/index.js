const ITERATIONS = 310000;
const encoder = new TextEncoder();

const ADMIN_HTML = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Habib Client Beta Access</title>
  <style>
    :root { color-scheme: dark; font: 15px/1.5 system-ui, sans-serif; background: #10141c; color: #f2f4f8; }
    * { box-sizing: border-box; }
    body { margin: 0; padding: 32px 16px; }
    main { max-width: 720px; margin: 0 auto; padding: 26px; border: 1px solid #ffffff30; border-radius: 18px; background: #1a1e27ee; box-shadow: 0 18px 60px #0008; }
    h1 { margin: 0 0 4px; font-size: 24px; }
    p { color: #c2cad5; margin: 0 0 20px; }
    label { display: block; margin: 14px 0 5px; font-weight: 650; }
    input, button { font: inherit; color: inherit; border-radius: 10px; }
    input { width: 100%; padding: 10px 12px; border: 1px solid #ffffff36; background: #090c12; outline: none; }
    input:focus { border-color: #ff7a45; box-shadow: 0 0 0 3px #ff7a4524; }
    button { border: 0; padding: 9px 14px; cursor: pointer; background: #ff6a2b; font-weight: 700; }
    button.secondary { background: #ffffff1b; border: 1px solid #ffffff30; }
    button.danger { background: #a73636; }
    button:disabled { opacity: .55; cursor: wait; }
    .row { display: flex; align-items: end; gap: 10px; }
    .row label { flex: 1; }
    #status { min-height: 22px; margin: 12px 0; color: #d8e0ea; }
    #status.error { color: #ff9b91; }
    #status.ok { color: #8ce5a2; }
    table { width: 100%; margin-top: 24px; border-collapse: collapse; font-size: 13px; }
    th, td { text-align: left; padding: 10px 8px; border-top: 1px solid #ffffff1e; }
    th { color: #c2cad5; }
    td:first-child { font-family: ui-monospace, monospace; word-break: break-all; }
    .hidden { display: none; }
    .note { font-size: 13px; margin-top: 8px; }
    @media (max-width: 600px) { main { padding: 20px 15px; } .row { display: block; } .row button { margin-top: 10px; } }
  </style>
</head>
<body>
<main>
  <h1>Habib Client beta access</h1>
  <p>Add a Minecraft account UUID and assign it a unique password. Passwords are never shown again after saving.</p>
  <label for="token">Admin token</label>
  <input id="token" type="password" autocomplete="off" placeholder="Paste the secret you set for BETA_ADMIN_TOKEN">
  <button id="load" class="secondary" type="button">Load approved accounts</button>
  <form id="form">
    <div class="row">
      <label>Minecraft account UUID<input id="uuid" required maxlength="36" placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"></label>
    </div>
    <label for="password">New password for this account</label>
    <input id="password" type="password" required minlength="12" maxlength="128" autocomplete="new-password" placeholder="Use a unique password with at least 12 characters">
    <p class="note">Saving an existing UUID replaces that account’s password. Give the password directly to that tester.</p>
    <button id="save" type="submit">Add or reset password</button>
  </form>
  <div id="status" role="status" aria-live="polite"></div>
  <section id="users" class="hidden">
    <h2>Approved accounts</h2>
    <table><thead><tr><th>Account UUID</th><th>Added</th><th></th></tr></thead><tbody id="userRows"></tbody></table>
  </section>
</main>
<script>
  const token = document.querySelector('#token');
  const status = document.querySelector('#status');
  const users = document.querySelector('#users');
  const rows = document.querySelector('#userRows');
  const uuidInput = document.querySelector('#uuid');
  const passwordInput = document.querySelector('#password');
  const setStatus = (text, kind = '') => { status.textContent = text; status.className = kind; };
  async function api(path, options = {}) {
    const response = await fetch(path, { ...options, headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token.value, ...(options.headers || {}) } });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(result.message || 'Request failed (' + response.status + ').');
    return result;
  }
  async function loadUsers() {
    try {
      const result = await api('/api/admin/users');
      rows.replaceChildren();
      for (const user of result.users) {
        const row = document.createElement('tr');
        const idCell = document.createElement('td'); idCell.textContent = user.uuid;
        const dateCell = document.createElement('td'); dateCell.textContent = new Date(user.createdAt * 1000).toLocaleDateString();
        const actionCell = document.createElement('td');
        const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'danger'; remove.textContent = 'Revoke';
        remove.addEventListener('click', async () => {
          if (!confirm('Revoke beta access for this account?')) return;
          try { await api('/api/admin/users?uuid=' + encodeURIComponent(user.uuid), { method: 'DELETE' }); setStatus('Access revoked.', 'ok'); await loadUsers(); }
          catch (error) { setStatus(error.message, 'error'); }
        });
        actionCell.append(remove); row.append(idCell, dateCell, actionCell); rows.append(row);
      }
      users.classList.remove('hidden');
      setStatus('Account list loaded.', 'ok');
    } catch (error) { setStatus(error.message, 'error'); }
  }
  document.querySelector('#load').addEventListener('click', loadUsers);
  document.querySelector('#form').addEventListener('submit', async event => {
    event.preventDefault();
    const save = document.querySelector('#save'); save.disabled = true;
    try {
      await api('/api/admin/users', { method: 'PUT', body: JSON.stringify({ uuid: uuidInput.value.trim(), password: passwordInput.value }) });
      passwordInput.value = '';
      uuidInput.value = '';
      setStatus('Password saved. Give it directly to the tester; it cannot be viewed here later.', 'ok');
      await loadUsers();
    } catch (error) { setStatus(error.message, 'error'); }
    finally { save.disabled = false; }
  });
</script>
</body>
</html>`;

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
      "Referrer-Policy": "no-referrer",
    },
  });
}

function uuidValue(value) {
  if (typeof value !== "string") return null;
  const uuid = value.trim().toLowerCase();
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(uuid) ? uuid : null;
}

function toBase64(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function fromBase64(value) {
  return Uint8Array.from(atob(value), (char) => char.charCodeAt(0));
}

async function passwordHash(password, salt) {
  const key = await crypto.subtle.importKey("raw", encoder.encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", hash: "SHA-256", salt, iterations: ITERATIONS },
    key,
    256,
  );
  return new Uint8Array(bits);
}

function equalBytes(left, right) {
  if (left.byteLength !== right.byteLength) return !crypto.subtle.timingSafeEqual(right, right);
  return crypto.subtle.timingSafeEqual(left, right);
}

async function adminAllowed(request, env) {
  const supplied = request.headers.get("Authorization") || "";
  if (!env.BETA_ADMIN_TOKEN || !supplied.startsWith("Bearer ")) return false;
  const expectedBytes = encoder.encode(env.BETA_ADMIN_TOKEN);
  const suppliedBytes = encoder.encode(supplied.slice(7));
  return equalBytes(expectedBytes, suppliedBytes);
}

async function verifyPassword(request, env) {
  let body;
  try { body = await request.json(); } catch { return json({ ok: false, message: "Invalid request." }, 400); }
  if (!body || typeof body !== "object" || Array.isArray(body)) {
    return json({ ok: false, message: "Invalid request." }, 400);
  }
  const uuid = uuidValue(body.uuid);
  const password = typeof body.password === "string" ? body.password : "";
  if (!uuid || password.length < 1 || password.length > 128) {
    return json({ ok: false, message: "Enter the password assigned to this account." }, 400);
  }

  const row = await env.DB.prepare(
    "SELECT salt, password_hash, active, failed_attempts, locked_until FROM beta_users WHERE uuid = ?1",
  ).bind(uuid).first();
  if (!row || row.active !== 1) return json({ ok: false, message: "This account or password is not approved." }, 401);

  const now = Math.floor(Date.now() / 1000);
  if (row.locked_until > now) {
    return json({ ok: false, message: "Too many attempts. Wait a little, then try again." }, 429);
  }

  const supplied = await passwordHash(password, fromBase64(row.salt));
  if (!equalBytes(supplied, fromBase64(row.password_hash))) {
    await env.DB.prepare(
      "UPDATE beta_users SET failed_attempts = CASE WHEN locked_until > 0 AND locked_until <= ?1 THEN 1 ELSE failed_attempts + 1 END, locked_until = CASE WHEN (CASE WHEN locked_until > 0 AND locked_until <= ?1 THEN 1 ELSE failed_attempts + 1 END) >= 8 THEN ?2 ELSE 0 END, updated_at = ?1 WHERE uuid = ?3",
    ).bind(now, now + 600, uuid).run();
    const state = await env.DB.prepare("SELECT locked_until FROM beta_users WHERE uuid = ?1").bind(uuid).first();
    if (state && state.locked_until > now) {
      return json({ ok: false, message: "Too many attempts. Wait a little, then try again." }, 429);
    }
    return json({ ok: false, message: "This account or password is not approved." }, 401);
  }

  await env.DB.prepare(
    "UPDATE beta_users SET failed_attempts = 0, locked_until = 0, updated_at = ?1 WHERE uuid = ?2",
  ).bind(now, uuid).run();
  return json({ ok: true, message: "Access approved." });
}

async function adminUsers(request, env, url) {
  if (!await adminAllowed(request, env)) return json({ message: "Admin token is missing or incorrect." }, 401);

  if (request.method === "GET") {
    const result = await env.DB.prepare(
      "SELECT uuid, created_at, active FROM beta_users ORDER BY created_at DESC",
    ).all();
    return json({ users: result.results.map((row) => ({ uuid: row.uuid, createdAt: row.created_at, active: row.active === 1 })) });
  }

  if (request.method === "PUT") {
    let body;
    try { body = await request.json(); } catch { return json({ message: "Invalid request." }, 400); }
    if (!body || typeof body !== "object" || Array.isArray(body)) {
      return json({ message: "Invalid request." }, 400);
    }
    const uuid = uuidValue(body.uuid);
    const password = typeof body.password === "string" ? body.password : "";
    if (!uuid) return json({ message: "Enter a valid Minecraft account UUID." }, 400);
    if (password.length < 12 || password.length > 128) {
      return json({ message: "Use a unique password between 12 and 128 characters." }, 400);
    }
    const salt = crypto.getRandomValues(new Uint8Array(16));
    const hash = await passwordHash(password, salt);
    const now = Math.floor(Date.now() / 1000);
    await env.DB.prepare(`
      INSERT INTO beta_users (uuid, salt, password_hash, active, failed_attempts, locked_until, created_at, updated_at)
      VALUES (?1, ?2, ?3, 1, 0, 0, ?4, ?4)
      ON CONFLICT(uuid) DO UPDATE SET salt = excluded.salt, password_hash = excluded.password_hash,
        active = 1, failed_attempts = 0, locked_until = 0, updated_at = excluded.updated_at
    `).bind(uuid, toBase64(salt), toBase64(hash), now).run();
    return json({ ok: true });
  }

  if (request.method === "DELETE") {
    const uuid = uuidValue(url.searchParams.get("uuid"));
    if (!uuid) return json({ message: "Enter a valid Minecraft account UUID." }, 400);
    await env.DB.prepare("DELETE FROM beta_users WHERE uuid = ?1").bind(uuid).run();
    return json({ ok: true });
  }

  return json({ message: "Method not allowed." }, 405);
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/" && request.method === "GET") {
      return new Response(ADMIN_HTML, {
        headers: {
          "Content-Type": "text/html; charset=utf-8",
          "Cache-Control": "no-store",
          "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; form-action 'none'; base-uri 'none'; frame-ancestors 'none'",
          "X-Content-Type-Options": "nosniff",
          "X-Frame-Options": "DENY",
          "Referrer-Policy": "no-referrer",
        },
      });
    }
    if (url.pathname === "/api/verify" && request.method === "POST") return verifyPassword(request, env);
    if (url.pathname === "/api/admin/users") return adminUsers(request, env, url);
    if (url.pathname === "/health" && request.method === "GET") return json({ ok: true });
    return json({ message: "Not found." }, 404);
  },
};
