// Анонимный счётчик запусков приложения. Cloudflare Worker + база D1.
// Принимает POST /ping с телом {"v":"0.8.2","w":0|1,"m":0|1,"f":0|1} и прибавляет счётчики за сегодня.
// Не читает ни IP, ни заголовков, ни куки, ничего не логирует. GET /stats отдаёт сводку владельцу по секретному токену.

const MAX_BODY_CHARS = 512;
// «?» клиент шлёт, когда версию определить не удалось или она необычная (см. StatsPolicy.cleanVersion)
const VERSION_RE = /^[0-9A-Za-z.\-?]{1,20}$/;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === "/ping") {
      if (request.method !== "POST") return new Response("Method Not Allowed", { status: 405, headers: { Allow: "POST" } });
      return handlePing(request, env);
    }
    if (url.pathname === "/stats") {
      if (request.method !== "GET") return new Response("Method Not Allowed", { status: 405, headers: { Allow: "GET" } });
      return handleStats(request, env, url);
    }
    return new Response("Not found", { status: 404 });
  },
};

async function handlePing(request, env) {
  const text = await request.text();
  if (text.length > MAX_BODY_CHARS) return new Response("Too large", { status: 413 });

  let body;
  try {
    body = JSON.parse(text);
  } catch (e) {
    return new Response("Bad request", { status: 400 });
  }
  if (body === null || typeof body !== "object" || Array.isArray(body)) return new Response("Bad request", { status: 400 });

  const { v, w, m, f } = body;
  const bit = (x) => x === 0 || x === 1;
  if (typeof v !== "string" || !VERSION_RE.test(v) || !bit(w) || !bit(m) || !bit(f)) {
    return new Response("Bad request", { status: 400 });
  }

  const day = new Date().toISOString().slice(0, 10); // день ставит сервер, по UTC
  try {
    await env.DB.prepare(
      `INSERT INTO daily (day, version, d, w, m, f) VALUES (?1, ?2, 1, ?3, ?4, ?5)
       ON CONFLICT (day, version) DO UPDATE SET d = d + 1, w = w + ?3, m = m + ?4, f = f + ?5`
    ).bind(day, v, w, m, f).run();
  } catch (e) {
    return new Response("Error", { status: 500 });
  }
  return new Response(null, { status: 204 });
}

async function isOwner(request, env) {
  if (!env.STATS_TOKEN) return false; // токен не задан: сводка закрыта для всех
  const enc = new TextEncoder();
  const digest = async (s) => new Uint8Array(await crypto.subtle.digest("SHA-256", enc.encode(s)));
  const [a, b] = await Promise.all([
    digest(request.headers.get("Authorization") || ""),
    digest("Bearer " + env.STATS_TOKEN),
  ]);
  let diff = 0; // сравнение хешей без раннего выхода: время ответа не выдаёт токен
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}

async function handleStats(request, env, url) {
  if (!(await isOwner(request, env))) return new Response("Unauthorized", { status: 401 });

  const [daily, weekly, monthly, versions, total] = await env.DB.batch([
    env.DB.prepare("SELECT day, SUM(d) AS opens, SUM(f) AS new_participants FROM daily GROUP BY day ORDER BY day DESC LIMIT 60"),
    // Понедельник недели: ближайшее воскресенье минус 6 дней
    env.DB.prepare("SELECT date(day, 'weekday 0', '-6 days') AS week, SUM(w) AS wau FROM daily GROUP BY week ORDER BY week DESC LIMIT 12"),
    env.DB.prepare("SELECT substr(day, 1, 7) AS month, SUM(m) AS mau FROM daily GROUP BY month ORDER BY month DESC LIMIT 12"),
    env.DB.prepare("SELECT version, SUM(d) AS opens FROM daily WHERE day >= date('now', '-6 days') GROUP BY version ORDER BY opens DESC"),
    env.DB.prepare("SELECT COALESCE(SUM(f), 0) AS participants FROM daily"),
  ]);

  const data = {
    participants_total: total.results[0].participants,
    daily: daily.results,
    weekly: weekly.results,
    monthly: monthly.results,
    versions_last_7_days: versions.results,
  };

  if (url.searchParams.get("format") === "text") {
    const lines = [];
    lines.push(`Участников за всё время: ${data.participants_total}`, "", "По дням (DAU = открытий за день):");
    for (const r of data.daily) lines.push(`  ${r.day}   DAU ${r.opens}   новых ${r.new_participants}`);
    lines.push("", "По неделям (WAU, неделя с понедельника):");
    for (const r of data.weekly) lines.push(`  ${r.week}   WAU ${r.wau}`);
    lines.push("", "По месяцам (MAU):");
    for (const r of data.monthly) lines.push(`  ${r.month}   MAU ${r.mau}`);
    lines.push("", "Версии за последние 7 дней:");
    for (const r of data.versions_last_7_days) lines.push(`  ${r.version}   ${r.opens}`);
    return new Response(lines.join("\n") + "\n", { headers: { "Content-Type": "text/plain; charset=utf-8" } });
  }
  return Response.json(data);
}
