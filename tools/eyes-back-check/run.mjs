// Eyes Back check. 1) Logic: Wyrm's C text (as double) vs NTL VANCED's own
// NTL_EB, fed the very same ticks and server reports. 2) Closed loop: the
// shipped float C and NTL's JS each drive a simulated slither server.
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const load = async name => (await WebAssembly.instantiate(readFileSync(path.join(HERE, name)))).instance.exports;
const CD = await load("eb_double.wasm");
const CF = await load("eb_float.wasm");
const body = readFileSync(path.join(HERE, "ntl_eb_body.js"), "utf8");

function makeNtl() {
  const env = { mr: 100, cv: 0, performance: { now: () => env.T }, T: 0 };
  const api = new Function("env", "with (env) {" + body + "\n return { tick: tick }; }")(env);
  const s = { T: 1, L: 1, R: 0 };
  let ang = 0;
  // the real snake.ang / snake.R setters are installed by NTL's hook() on the first tick
  s.ang = 0;
  return {
    env, s,
    tick(aiming, target, a, scang, spang, now, rtt) {
      env.mr = rtt; env.T = now; s.T = scang; s.L = spang;
      if (!s.__hooked) { s.ang = a; s.R = a; }
      const b = api.tick(aiming ? target : null, s, now);
      s.__hooked = true;
      return b;
    },
    report(a, now) { env.T = now; s.ang = a; s.R = a; },
  };
}

const TAU = Math.PI * 2;
const norm = a => { a %= TAU; if (a > Math.PI) a -= TAU; else if (a < -Math.PI) a += TAU; return a; };
let seed = 12345;
const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;

// A slither server: every 8 ms it turns the heading toward the last byte it got
// at mamu*scang*spang per 8 ms; it reports the heading every few frames.
function scenario({ rtt, jitter, mamu = 0.033, ms = 60_000, targetFn, scang = 1, spang = 1, driver }) {
  const half = rtt / 2;
  let H = rnd() * TAU, W = H; // server heading, last angle received
  const inflight = [];        // bytes on their way to the server {at, w}
  const reports = [];         // reports on their way to the client {at, a}
  let nextTick = 0, nextReport = 16, lastKnown = H;
  const out = { ticks: 0, mism: 0, firstMism: null, eye: [], err: [] };
  for (let t = 0; t <= ms; t += 1) {
    // client receives reports
    while (reports.length && reports[0].at <= t) { const r = reports.shift(); lastKnown = r.a; driver.report(r.a, t); }
    if (t >= nextTick) {
      const target = targetFn(t);
      const aiming = target !== null;
      const b = driver.tick(aiming, aiming ? target : 0, lastKnown, scang, spang, t, rtt, mamu, out);
      out.ticks++;
      const lat = half + (rnd() - 0.5) * jitter;
      inflight.push({ at: t + Math.max(1, lat), w: b * TAU / 251 });
      inflight.sort((x, y) => x.at - y.at);
      nextTick = t + 34 + Math.floor(rnd() * 6);
    }
    while (inflight.length && inflight[0].at <= t) W = inflight.shift().w;
    if (t % 8 === 0) {
      const r = mamu * scang * spang;
      const d = norm(W - H);
      H = norm(H + (d > r ? r : d < -r ? -r : d));
      out.eye.push(Math.abs(Math.PI - Math.abs(norm(W - H))) * 180 / Math.PI);
      const tg = targetFn(t);
      if (tg !== null && t > 3000) out.err.push(Math.abs(norm(tg - H)) * 180 / Math.PI);
    }
    if (t >= nextReport) {
      reports.push({ at: t + Math.max(1, half + (rnd() - 0.5) * jitter), a: H });
      reports.sort((x, y) => x.at - y.at);
      nextReport = t + 16 + Math.floor(rnd() * 40);
    }
  }
  return out;
}

const pct = (arr, p) => { const s = [...arr].sort((a, b) => a - b); return s[Math.floor((s.length - 1) * p)] || 0; };
const avg = arr => arr.reduce((a, b) => a + b, 0) / Math.max(1, arr.length);

const targets = {
  still: () => 1.0,
  slow: t => norm(t / 4000),
  steps: t => [0.3, 2.5, -1.8, 1.2, -2.9][Math.floor(t / 7000) % 5],
  zigzag: t => Math.sin(t / 1500) * 2.2,
  head: t => (Math.floor(t / 5000) % 2 ? null : 0.7), // finger on the head half the time
};

// 1) Logic: both fed the same inputs, the JS bytes drive the server.
let total = 0, mism = 0, worst = [];
for (const [tname, fn] of Object.entries(targets)) {
  for (const rtt of [40, 100, 180, 320, 600]) {
    for (const [scang, spang] of [[1, 1], [0.6, 1], [0.35, 0.8]]) {
      const ntl = makeNtl();
      CD.set_on(1);
      const driver = {
        tick(aiming, target, a, sc, sp, t, r, mamu, out) {
          const bj = ntl.tick(aiming, target, a, sc, sp, t, r);
          const bc = CD.tick(aiming ? 1 : 0, target, a, sc, sp, t, r, mamu);
          if (bj !== bc) { out.mism++; if (!out.firstMism) out.firstMism = { t, bj, bc }; }
          return bj;
        },
        report(a, t) { ntl.report(a, t); CD.report(a, t); },
      };
      const o = scenario({ rtt, jitter: rtt * 0.3, targetFn: fn, scang, spang, driver, ms: 30_000 });
      total += o.ticks; mism += o.mism;
      if (o.mism) worst.push(`${tname} rtt ${rtt} sc ${scang}: ${o.mism}/${o.ticks} first ${JSON.stringify(o.firstMism)}`);
    }
  }
}
console.log(`LOGIC  C(double) vs NTL JS: ${total} ticks, ${mism} different bytes`);
worst.slice(0, 8).forEach(l => console.log("   ", l));

// 2) Closed loop: each implementation drives its own server.
function closed(kind, tname, rtt) {
  seed = 777 + rtt;
  let driver;
  if (kind === "ntl") {
    const ntl = makeNtl();
    driver = { tick: (aiming, target, a, sc, sp, t, r) => ntl.tick(aiming, target, a, sc, sp, t, r), report: (a, t) => ntl.report(a, t) };
  } else {
    CF.set_on(1);
    driver = { tick: (aiming, target, a, sc, sp, t, r, mamu) => CF.tick(aiming ? 1 : 0, target, a, sc, sp, t, r, mamu), report: (a, t) => CF.report(a, t) };
  }
  return scenario({ rtt, jitter: rtt * 0.3, targetFn: targets[tname], driver, ms: 60_000 });
}
console.log("\nCLOSED LOOP (eyes = degrees off straight back; aim = degrees off the target, after 3 s)");
console.log("target  rtt | NTL eyes avg/p95  aim avg/p95 | WYRM eyes avg/p95  aim avg/p95");
for (const tname of ["still", "slow", "steps", "zigzag"]) {
  for (const rtt of [60, 150, 300]) {
    const a = closed("ntl", tname, rtt), b = closed("wyrm", tname, rtt);
    const f = o => `${avg(o.eye).toFixed(1).padStart(5)}/${pct(o.eye, 0.95).toFixed(1).padStart(5)}  ${avg(o.err).toFixed(1).padStart(5)}/${pct(o.err, 0.95).toFixed(1).padStart(5)}`;
    console.log(`${tname.padEnd(7)} ${String(rtt).padStart(4)} | ${f(a)}       | ${f(b)}`);
  }
}
