// Wyrm's C (wasm, the text in callback.c) against NTL 9.68's own JS slices.
import fs from "node:fs";
const dir = new URL(".", import.meta.url);
const S = JSON.parse(fs.readFileSync(new URL("ntl_slices.json", dir), "utf8"));
const { instance } = await WebAssembly.instantiate(fs.readFileSync(new URL("harness.wasm", dir)));
const X = instance.exports;
const mem = () => new Uint8Array(X.memory.buffer);
const ids = S.ids;
const FSTAGS = { length: 104 }; // Wyrm's public sheet 200..303

// ---- NTL's writer, from its own source ----
const ntlBlock = new Function("tagNtl", "skin", "custom", `
  var Er = !1, tn = 18, PA = 60, Ce = !1, playing = !1, snake = null, O1 = [];
  var fstags = { length: ${FSTAGS.length} };
  var Bt = tagNtl >= 0, bt = tagNtl;
  var store = { want_custom_skin: custom ? "1" : "0", snakercv: String(skin) };
  var localStorage = { getItem: k => store[k] };
  for (const k in store) localStorage[k] = store[k];
  var ni = function () { return 255; }, Y3 = function () {};
  var ${S.zf};
  var ${S.xa};
  var ${S.ff};
  var ${S.ha};
  var P, eb, ab, gb = HA(), ib = skin, db = custom;
  var cb = custom ? Uint8Array.from([255,255,255,0,0,0,0,0, 3,7,2,9]) : [];
  ${S.build};
  return Array.from(cb);
`);

// ---- NTL's reader, from its own source ----
const ntlRead = new Function("b0", "b1", "b2", "b6", `
  var tn = 18, PA = 60, fstags = { length: ${FSTAGS.length} };
  var cb = [b0, b1, b2, 0, 0, 0, b6, 0], bb = { P: -1, gf: !0, A: 5 }, ab;
  ${S.read};
  return [bb.P, bb.gf ? -1 : bb.A];
`);

const sheet = n => { const i = ids.indexOf(n); return i < 0 ? 0 : i + 1; };
let cases = 0, bad = 0;
const fail = (...a) => { if (bad++ < 12) console.log("MISMATCH", ...a); };

// Writer: every tag on Wyrm's sheet + none, every preset, preset and custom.
for (let t = -1; t < ids.length; t++) {
  for (let skin = 0; skin < 66; skin++) {
    for (const custom of [0, 1]) {
      cases++;
      const n = X.build(t, skin, custom);
      const ours = Array.from(mem().slice(X.out(), X.out() + n));
      const ntl = ntlBlock(t < 0 ? -1 : ids[t], skin, custom === 1);
      if (t < 0 && custom) {
        // No tag, custom skin: Wyrm's block is unchanged (random 6 and 7).
        const want = [255,255,255,0,0,0,0x5A,0x5A,3,7,2,9];
        if (JSON.stringify(ours) !== JSON.stringify(want)) fail("untagged custom", skin, ours);
        continue;
      }
      if (JSON.stringify(ours) !== JSON.stringify(ntl)) fail({ t, ntl: ids[t], skin, custom, ours, ntl_block: ntl });
    }
  }
}
const writer = cases;

// Reader: every corner byte combination that matters.
for (const b0 of [0, 7, 253, 254, 255]) for (const b1 of [0, 17, 18, 19, 37, 38, 39, 255])
  for (const b2 of [0, 5, 24, 65, 66, 200, 254, 255]) for (let b6 = 0; b6 < 256; b6++) {
    cases++;
    X.read(b0, b1, b2, b6);
    const r = new Int32Array(X.memory.buffer, X.rd(), 3);
    const [P, preset] = ntlRead(b0, b1, b2, b6);
    const ntlSheet = P >= 0 ? sheet(P) : 0;
    const ntlCv = preset >= 0 ? preset % 66 : -1;
    if (r[2] !== ntlSheet || r[1] !== ntlCv) fail("read", { b0, b1, b2, b6, ours: [r[0], r[1], r[2]], ntl: [P, preset] });
  }
console.log(`writer cases ${writer}, reader cases ${cases - writer}, mismatches ${bad}`);
// A few joins to read by eye.
for (const [t, skin, custom] of [[ids.indexOf(5), 9, 0], [ids.indexOf(14), 24, 0], [ids.indexOf(13), 24, 0], [ids.indexOf(250), 3, 1], [ids.indexOf(59), 65, 0]]) {
  const n = X.build(t, skin, custom);
  console.log(`tag ${ids[t]} skin ${skin} custom ${custom}:`, n ? Array.from(mem().slice(X.out(), X.out() + n)).join(" ") : "(no block)");
}
process.exit(bad ? 1 : 0);
