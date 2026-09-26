// Read-only diagnostic client for one explicitly supplied slither arena.
// It joins as "probe", prints packet command/length, then immediately closes.
const net = require("net");
const crypto = require("crypto");

const endpoint = process.argv[2];
if (!/^\d+\.\d+\.\d+\.\d+:\d+$/.test(endpoint || "")) {
  console.error("usage: node tests/arena_live_probe.js ip:port");
  process.exit(2);
}
const [host, portText] = endpoint.split(":");
const port = Number(portText);
const fingerprint = [54, 206, 204, 169, 97, 178, 74, 136, 124, 117,
  14, 210, 106, 236, 8, 208, 136, 213, 140, 111];
let upgraded = false;
let buffer = Buffer.alloc(0);
let finished = false;

function finish(message, code = 0) {
  if (finished) return;
  finished = true;
  console.log(message);
  socket.destroy();
  process.exitCode = code;
}

function sendBinary(bytes) {
  const body = Buffer.from(bytes);
  const mask = crypto.randomBytes(4);
  const header = body.length < 126
    ? Buffer.from([0x82, 0x80 | body.length])
    : Buffer.from([0x82, 0xfe, body.length >> 8, body.length & 255]);
  const masked = Buffer.alloc(body.length);
  for (let i = 0; i < body.length; i++) masked[i] = body[i] ^ mask[i & 3];
  socket.write(Buffer.concat([header, mask, masked]));
}

function decodeSecret(packet) {
  const intermediate = Buffer.alloc(92);
  let out = 0;
  let rolling = 23;
  let nibble = 0;
  let half = 0;
  for (let at = 1; at < 184 && at < packet.length; at++) {
    let value = packet[at];
    if (value <= 96) value += 32;
    value = (value - 97 - rolling) % 26;
    if (value < 0) value += 26;
    nibble = nibble * 16 + value;
    rolling += 17;
    if (half === 1) {
      intermediate[out++] = nibble;
      half = 0;
      nibble = 0;
    } else {
      half++;
    }
  }
  const selected = [...intermediate.subarray(9, 14),
    ...intermediate.subarray(20, 42)];
  let accumulator = 0;
  return Buffer.from(selected.map((raw, i) => {
    let base = 65;
    let value = raw;
    if (value >= 97) {
      base += 32;
      value -= 32;
    }
    value -= 65;
    if (i === 0) accumulator = 3 + value;
    const encoded = (value + accumulator) % 26;
    accumulator += 2 + value;
    return encoded + base;
  }));
}

function handlePacket(packet) {
  if (!packet.length) return;
  const cmd = packet[0];
  console.log(`packet 0x${cmd.toString(16).padStart(2, "0")} length ${packet.length}`);
  if (cmd === 0x36) {
    sendBinary(decodeSecret(packet));
    const nickname = Buffer.from("probe", "ascii");
    sendBinary(Buffer.from([0x73, 30, 1, 35, ...fingerprint, 0,
      nickname.length, ...nickname, 0, 255]));
  } else if (cmd === 0x61) {
    finish("ADMITTED: configuration packet received");
  }
}

function frames() {
  while (buffer.length >= 2) {
    let length = buffer[1] & 0x7f;
    let at = 2;
    if (length === 126) {
      if (buffer.length < 4) return;
      length = buffer.readUInt16BE(2); at = 4;
    } else if (length === 127) {
      if (buffer.length < 10) return;
      const wide = buffer.readBigUInt64BE(2);
      if (wide > BigInt(Number.MAX_SAFE_INTEGER)) return finish("oversized frame", 1);
      length = Number(wide); at = 10;
    }
    if (buffer.length < at + length) return;
    const opcode = buffer[0] & 15;
    const payload = buffer.subarray(at, at + length);
    buffer = buffer.subarray(at + length);
    if (opcode === 2) handlePacket(payload);
    else if (opcode === 8) return finish("CLOSED before admission", 1);
  }
}

const socket = net.connect({host, port}, () => {
  const key = crypto.randomBytes(16).toString("base64");
  socket.write(`GET /slither HTTP/1.1\r\nHost: ${endpoint}\r\n` +
    "Connection: Upgrade\r\nUpgrade: websocket\r\nSec-WebSocket-Version: 13\r\n" +
    `Sec-WebSocket-Key: ${key}\r\nOrigin: https://slither.io\r\n\r\n`);
});
socket.setTimeout(6000);
socket.on("timeout", () => finish("TIMEOUT before admission", 1));
socket.on("error", error => finish(`ERROR: ${error.message}`, 1));
socket.on("close", () => { if (!finished) finish("CLOSED before admission", 1); });
socket.on("data", chunk => {
  buffer = Buffer.concat([buffer, chunk]);
  if (!upgraded) {
    const end = buffer.indexOf("\r\n\r\n");
    if (end < 0) return;
    const response = buffer.subarray(0, end).toString("ascii");
    if (!response.startsWith("HTTP/1.1 101")) return finish(response.split("\r\n")[0], 1);
    upgraded = true;
    buffer = buffer.subarray(end + 4);
    sendBinary([1]);
    sendBinary([0x63, 0]);
  }
  frames();
});
