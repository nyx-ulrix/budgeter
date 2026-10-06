// Budgeter test server. No dependencies: `node tools/test-server.js [version]`
//
// 1. Fake GitHub releases API, so the in-app updater can be tested without publishing a release.
//    Give a version (e.g. 0.9.0) and it builds a debug APK with that version number and offers it as the latest release.
//    Without one it offers the newest APK already in test-release/.
//    In the app (debug build): Profile → Updates → Test server → http://10.0.2.2:8787 (emulator) or http://<this PC>:8787.
// 2. Fake AI provider (OpenAI-style), so receipt reading can be tested with no API key.
//    In the app: Profile → AI → Custom, base URL http://<this PC>:8787/v1, any key, model "test".
//    It returns whatever is in tools/test-receipt.json.

const http = require("http");
const fs = require("fs");
const path = require("path");
const os = require("os");
const { execFileSync } = require("child_process");

const PORT = Number(process.env.PORT) || 8787;
const ROOT = path.join(__dirname, "..");
const RELEASES = path.join(ROOT, "test-release");
fs.mkdirSync(RELEASES, { recursive: true });

const want = process.argv[2];
if (want) build(want);

function build(version) {
  console.log(`Building Budgeter ${version} (debug, signed with the release key)...`);
  const win = process.platform === "win32";
  const env = { ...process.env };
  const studioJbr = "C:\\Program Files\\Android\\Android Studio\\jbr";
  if (!env.JAVA_HOME && fs.existsSync(studioJbr)) env.JAVA_HOME = studioJbr;
  const args = [":app:assembleDebug", "-q", `-PappVersion=${version}`];
  if (win) execFileSync("cmd.exe", ["/c", path.join(ROOT, "gradlew.bat"), ...args], { cwd: ROOT, env, stdio: "inherit" });
  else execFileSync(path.join(ROOT, "gradlew"), args, { cwd: ROOT, env, stdio: "inherit" });
  fs.copyFileSync(path.join(ROOT, "app/build/outputs/apk/debug/app-debug.apk"), path.join(RELEASES, `budgeter-${version}.apk`));
  console.log(`Ready: test-release/budgeter-${version}.apk`);
}

function newer(a, b) {
  const p = (s) => s.split(".").map((x) => parseInt(x, 10) || 0);
  const x = p(a), y = p(b);
  for (let i = 0; i < Math.max(x.length, y.length); i++) if ((x[i] || 0) !== (y[i] || 0)) return (x[i] || 0) > (y[i] || 0);
  return false;
}

function latestApk() {
  const apks = fs.readdirSync(RELEASES).map((f) => /^budgeter-(.+)\.apk$/.exec(f)).filter(Boolean);
  if (!apks.length) return null;
  const best = apks.reduce((a, b) => (newer(b[1], a[1]) ? b : a));
  return { version: best[1], file: path.join(RELEASES, best[0]) };
}

function lanAddresses() {
  return Object.values(os.networkInterfaces()).flat().filter((i) => i && i.family === "IPv4" && !i.internal).map((i) => i.address);
}

function send(res, code, body, type = "application/json") {
  res.writeHead(code, { "Content-Type": type });
  res.end(typeof body === "string" ? body : JSON.stringify(body, null, 2));
}

http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  console.log(new Date().toISOString().slice(11, 19), req.method, url.pathname);

  // GitHub: latest release
  if (/^\/repos\/[^/]+\/[^/]+\/releases\/latest$/.test(url.pathname)) {
    const apk = latestApk();
    if (!apk) return send(res, 404, { message: "Not Found (no APK in test-release/; run with a version, e.g. node tools/test-server.js 0.9.0)" });
    return send(res, 200, {
      tag_name: `v${apk.version}`,
      name: `Budgeter v${apk.version} (test)`,
      body: "Test release served from this PC.",
      assets: [{ name: path.basename(apk.file), size: fs.statSync(apk.file).size, browser_download_url: `http://${req.headers.host}/download/${path.basename(apk.file)}` }],
    });
  }
  if (url.pathname.startsWith("/download/")) {
    const file = path.join(RELEASES, path.basename(url.pathname));
    if (!fs.existsSync(file)) return send(res, 404, { message: "no such APK" });
    res.writeHead(200, { "Content-Type": "application/vnd.android.package-archive", "Content-Length": fs.statSync(file).size });
    return fs.createReadStream(file).pipe(res);
  }

  // Fake AI provider
  if (url.pathname === "/v1/models") return send(res, 200, { data: [{ id: "test" }] });
  if (url.pathname === "/v1/chat/completions" && req.method === "POST") {
    let body = "";
    req.on("data", (c) => (body += c));
    return req.on("end", () => {
      let text = "";
      try { text = JSON.parse(body).messages.at(-1).content; } catch {}
      console.log("  receipt text received:\n  " + String(text).split("\n").join("\n  "));
      const reply = fs.readFileSync(path.join(__dirname, "test-receipt.json"), "utf8");
      send(res, 200, { choices: [{ message: { role: "assistant", content: reply } }] });
    });
  }

  // Status page
  if (url.pathname === "/") {
    const apk = latestApk();
    const ips = lanAddresses();
    return send(res, 200, `<!doctype html><meta name=viewport content="width=device-width"><title>Budgeter test server</title>
<body style="font-family:monospace;background:#E7D6AD;color:#2B1D12;padding:16px;max-width:720px">
<h2 style="background:#F4512A;color:#F1E4C6;padding:8px;border:3px solid #2B1D12">BUDGETER TEST SERVER</h2>
<p><b>Offered update:</b> ${apk ? "v" + apk.version : "none (start with a version: node tools/test-server.js 0.9.0)"}</p>
<p><b>Update server for the app</b> (Profile → Updates → Test server):<br>emulator: http://10.0.2.2:${PORT}<br>${ips.map((i) => `phone on Wi-Fi: http://${i}:${PORT}`).join("<br>")}</p>
<p><b>Fake AI</b> (Profile → AI → Custom): base URL http://&lt;address above&gt;:${PORT}/v1, any key, model "test".</p>
</body>`, "text/html");
  }
  send(res, 404, { message: "Not Found" });
}).listen(PORT, "0.0.0.0", () => {
  const apk = latestApk();
  console.log(`Budgeter test server on port ${PORT}`);
  console.log(`  offering: ${apk ? "v" + apk.version : "nothing yet"}`);
  console.log(`  emulator: http://10.0.2.2:${PORT}`);
  for (const ip of lanAddresses()) console.log(`  phone:    http://${ip}:${PORT}`);
  const usb = reverseUsb();
  if (usb.length) console.log(`  USB (${usb.join(", ")}): http://localhost:${PORT}`);
});

/** Forwards the port to every USB-connected device, so a plugged-in phone can use http://localhost:PORT. */
function reverseUsb() {
  const adb = process.env.LOCALAPPDATA ? path.join(process.env.LOCALAPPDATA, "Android/Sdk/platform-tools/adb.exe") : "adb";
  try {
    const out = execFileSync(fs.existsSync(adb) ? adb : "adb", ["devices"], { encoding: "utf8" });
    const ids = out.split("\n").slice(1).map((l) => l.trim().split(/\s+/)).filter((p) => p[1] === "device").map((p) => p[0]);
    for (const id of ids) execFileSync(fs.existsSync(adb) ? adb : "adb", ["-s", id, "reverse", `tcp:${PORT}`, `tcp:${PORT}`]);
    return ids;
  } catch { return []; }
}
