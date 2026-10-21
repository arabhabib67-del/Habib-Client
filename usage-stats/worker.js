const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
  "Cache-Control": "no-store",
};

function json(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json; charset=utf-8" },
  });
}

async function hashInstallId(installId) {
  const bytes = new TextEncoder().encode(installId);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

function safeVersion(value) {
  return typeof value === "string" && /^[A-Za-z0-9._+-]{1,32}$/.test(value) ? value : null;
}

async function ping(request, env) {
  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: "Expected JSON." }, 400);
  }

  const installId = typeof body.installId === "string" ? body.installId : "";
  const modVersion = safeVersion(body.modVersion);
  const minecraftVersion = safeVersion(body.minecraftVersion);
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(installId)
      || !modVersion || !minecraftVersion) {
    return json({ error: "Invalid usage ping." }, 400);
  }

  const day = new Date().toISOString().slice(0, 10);
  const cutoff = new Date(Date.now() - 35 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
  const installHash = await hashInstallId(installId);
  await env.DB.batch([
    env.DB.prepare("DELETE FROM active_installs WHERE day < ?").bind(cutoff),
    env.DB.prepare(`INSERT INTO active_installs (day, install_hash, mod_version, minecraft_version, updated_at)
      VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
      ON CONFLICT(day, install_hash) DO UPDATE SET
        mod_version = excluded.mod_version,
        minecraft_version = excluded.minecraft_version,
        updated_at = CURRENT_TIMESTAMP`)
      .bind(day, installHash, modVersion, minecraftVersion),
  ]);
  return json({ ok: true }, 202);
}

async function stats(env) {
  const today = new Date().toISOString().slice(0, 10);
  const since = new Date(Date.now() - 6 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
  const [todayRow, weekRow, gameVersions, modVersions] = await Promise.all([
    env.DB.prepare("SELECT COUNT(DISTINCT install_hash) AS count FROM active_installs WHERE day = ?").bind(today).first(),
    env.DB.prepare("SELECT COUNT(DISTINCT install_hash) AS count FROM active_installs WHERE day >= ?").bind(since).first(),
    env.DB.prepare(`SELECT minecraft_version AS version, COUNT(DISTINCT install_hash) AS count
      FROM active_installs WHERE day = ? GROUP BY minecraft_version ORDER BY count DESC LIMIT 5`).bind(today).all(),
    env.DB.prepare(`SELECT mod_version AS version, COUNT(DISTINCT install_hash) AS count
      FROM active_installs WHERE day = ? GROUP BY mod_version ORDER BY count DESC LIMIT 5`).bind(today).all(),
  ]);

  const toCounts = (rows) => Object.fromEntries((rows?.results ?? []).map((row) => [row.version, row.count]));
  return json({
    activeToday: todayRow?.count ?? 0,
    activeLast7Days: weekRow?.count ?? 0,
    gameVersions: toCounts(gameVersions),
    modVersions: toCounts(modVersions),
    updatedAt: new Date().toISOString(),
  });
}

export default {
  async scheduled(_controller, env, context) {
    const cutoff = new Date(Date.now() - 35 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
    context.waitUntil(env.DB.prepare("DELETE FROM active_installs WHERE day < ?").bind(cutoff).run());
  },

  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders });
    const path = new URL(request.url).pathname;
    if (request.method === "POST" && path === "/api/ping") return ping(request, env);
    if (request.method === "GET" && path === "/api/stats") return stats(env);
    return json({ error: "Not found." }, 404);
  },
};
