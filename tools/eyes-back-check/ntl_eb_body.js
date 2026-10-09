
  var cfg = {
    key: "u",        // toggle key (lowercase, as in event.key)
    eps: 8,          // how far from exact 180° the sent angle sits, in angle
                     // steps (251 steps = 360°, so 8 ≈ 11.5°). Smaller = eyes
                     // more exactly back, less tolerance to ping jitter.
    turnMargin: 4,   // extra steps added to eps while turning hard (covers
                     // ping jitter during sustained turns)
    epsMax: 22,      // cap for the adaptive part of eps (steps; 22 ≈ 32°)
    snap: 0.55,      // rad: if the server heading is this far from our
                     // prediction, the model is wrong → flush and re-anchor
    band: 2.5,       // steering proportional band, in ticks of max turn.
                     // Bigger = smoother/slower, smaller = snappier/jittery.
    turnRate: 0.033  // server turn rate, rad per 8ms at scang=spang=1 (NTL "tw")
  };
  var TWO_PI = 2 * Math.PI, on = true, err = 0, lastT = 0, hud = null, mode = "back";   // "back" = Eyes Back, "side" = Center eyes broadcast
  var anchor = null;   // {t: client time the server heading refers to, a: heading}
  var log = [];        // commands sent: {te: time it takes effect on server, w: target angle sent (rad), r: rad/ms}
  var hooked = null, hookedVal = 0, hookedR = 0, lastSeen = null, resMax = 0, pending = null, hold = null;

  function norm(a) { a %= TWO_PI; if (a > Math.PI) a -= TWO_PI; else if (a < -Math.PI) a += TWO_PI; return a; }
  function rtt() { var v = typeof mr === "number" && mr > 0 ? mr : typeof cv === "number" && cv > 0 ? cv : 100; return Math.min(v, 600); }

  /* Precise timestamp of every heading update from the server: hook snake.ang. */
  function hook(s) {
    if (hooked === s) return;
    hooked = s; hookedVal = s.ang; hookedR = s.R; lastSeen = s.ang; pending = null;
    anchor = null; log.length = 0; resMax = 0; sample(s.ang, null, performance.now());
    try {
      Object.defineProperty(s, "ang", {
        configurable: true, enumerable: true,
        get: function () { return hookedVal; },
        set: function (v) { hookedVal = v; if (on && v !== lastSeen) { lastSeen = v; pending = { v: v, t: performance.now() }; } }
      });
      // the packet handler writes ang then (if present) R right after it, synchronously:
      // when R arrives we know exactly which of our commands the server was executing.
      Object.defineProperty(s, "R", {
        configurable: true, enumerable: true,
        get: function () { return hookedR; },
        set: function (v) { hookedR = v; if (on && pending) { sample(pending.v, v, pending.t); pending = null; } }
      });
    } catch (e) { hooked = null; }
  }
  function flushPending() { if (pending) { sample(pending.v, null, pending.t); pending = null; } }

  /* A fresh server heading arrived at client time now (it describes the server
     state ~rtt/2 ago). Measure how far it is from what our model predicted for
     that moment (residual), adapt eps to it, and re-anchor. */
  function sample(v, R, now) {                        // R (echoed target) currently unused
    var tau = now - rtt() / 2;
    if (anchor !== null) {
      var res = Math.abs(norm(v - headingAt(tau)));
      resMax = Math.max(res, resMax * 0.92);
      if (res > cfg.snap) log.length = 0;          // model diverged → drop assumed turns
    }
    anchor = { t: tau, a: v };
  }

  /* Predicted server heading at (client-clock) time tau: replay the server
     from the anchor — for each command in effect, turn toward its target angle
     at max rate (exactly what the server does), so the model also knows when
     a command would land on the "wrong" side. */
  function turn(a, w, r, ms) {
    var d = norm(w - a), mx = r * ms;
    return a + (d > mx ? mx : d < -mx ? -mx : d);
  }
  function headingAt(tau) {
    var a = anchor.a, t = anchor.t, cmd = null, i = 0;
    for (; i < log.length && log[i].te <= t; i++) cmd = log[i];
    for (; i < log.length && log[i].te < tau; i++) { if (cmd) a = turn(a, cmd.w, cmd.r, log[i].te - t); t = log[i].te; cmd = log[i]; }
    return cmd ? turn(a, cmd.w, cmd.r, tau - t) : a;
  }

  /* Called every send tick (~33ms). target = mouse angle (rad) or null when the
     mouse is on the head. s = own snake. Returns the 0-250 angle byte to send. */
  function tick(target, s, now) {
    hook(s);
    if (hooked !== s) {                                  // hook failed → fall back to tick-time detection
      if (anchor === null || s.ang !== lastSeen) { lastSeen = s.ang; sample(s.ang, null, now - 16); }
    } else flushPending();                               // packet without wang → plain heading sample
    var dt = lastT ? now - lastT : 33; lastT = now; if (dt > 200) dt = 200;
    var half = rtt() / 2, te = now + half;               // when this command reaches the server
    var r = cfg.turnRate * s.T * s.L / 8;                // rad per ms
    if (r < 1e-6) r = 1e-6;
    var est = headingAt(te);                             // server heading when this command lands
    if (target === null) { if (hold === null) hold = est; target = hold; } else hold = null; // mouse on head → keep heading
    var d = norm(target - est);                          // how much we still want to turn
    var k = d / (r * dt * cfg.band); if (k > 1) k = 1; else if (k < -1) k = -1;
    err += (1 + k) / 2;                                  // sigma-delta: share of ticks turning in +direction
    var plus = err >= 1; if (plus) err -= 1;
    var eps = cfg.eps + Math.abs(k) * cfg.turnMargin + Math.min(cfg.epsMax, 1.3 * resMax * 251 / TWO_PI); // adaptive: widen with measured prediction error
    var b;
    if (mode === "side") {                               // +90° → server turns +dir, −90° → −dir; the viewer's pupil averages to the middle
      b = Math.round(251 * (((est + (plus ? Math.PI / 2 : -Math.PI / 2)) % TWO_PI + TWO_PI) % TWO_PI) / TWO_PI);
    } else {
      var base = 251 * (((est + Math.PI) % TWO_PI + TWO_PI) % TWO_PI) / TWO_PI;
      b = Math.round(plus ? base - eps : base + eps);    // 180°-eps → server turns +dir, 180°+eps → server turns -dir
    }
    b = ((b % 251) + 251) % 251;
    log.push({ te: te, w: b * TWO_PI / 251, r: r });    // remember exactly what the server will receive
    while (log.length > 1 && log[1].te < now - 2000) log.shift();
    return b;
  }

