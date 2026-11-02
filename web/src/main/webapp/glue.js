// Browser glue for the web version of Laser Tag. The game itself is Java compiled to
// JavaScript (lasertag.js); this file gives it a 480x360 canvas, input, sound and storage
// through the global LT object.
(function () {
  "use strict";
  var W = 480, H = 360;
  var canvas = document.getElementById("screen");
  var ctx = canvas.getContext("2d", { alpha: false });
  var crt = document.getElementById("crt");
  var LT = {};
  window.LT = LT;

  // ------------------------------------------------------------ drawing

  var target = ctx;
  var colorCache = new Map();
  function css(c) {
    var s = colorCache.get(c);
    if (s === undefined) {
      s = "rgba(" + ((c >>> 16) & 255) + "," + ((c >>> 8) & 255) + "," + (c & 255) + "," + (((c >>> 24) & 255) / 255) + ")";
      colorCache.set(c, s);
    }
    return s;
  }

  LT.begin = function (shakeX, shakeY) {
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.fillStyle = "#000";
    ctx.fillRect(0, 0, W, H);
    ctx.setTransform(1, 0, 0, 1, shakeX, shakeY);
  };

  LT.fill = function (x, y, w, h, c) {
    target.fillStyle = css(c);
    target.fillRect(x, y, w, h);
  };

  // Hard-edged lines (canvas strokes are anti-aliased): Bresenham with a square brush.
  LT.line = function (x0, y0, x1, y1, width, c) {
    target.fillStyle = css(c);
    var off = Math.floor((width - 1) / 2);
    var dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
    var sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
    for (var guard = 0; guard < 4000; guard++) {
      target.fillRect(x0 - off, y0 - off, width, width);
      if (x0 === x1 && y0 === y1) break;
      var e2 = 2 * err;
      if (e2 >= dy) { err += dy; x0 += sx; }
      if (e2 <= dx) { err += dx; y0 += sy; }
    }
  };

  var caches = new Map();
  LT.hasCache = function (key) { return caches.has(key); };
  LT.beginCache = function (key, w, h) {
    var c = document.createElement("canvas");
    c.width = w;
    c.height = h;
    caches.set(key, c);
    target = c.getContext("2d");
  };
  LT.endCache = function () { target = ctx; };
  LT.drawCache = function (key, x, y) { ctx.drawImage(caches.get(key), x, y); };

  // ------------------------------------------------------------ layout and CRT effect

  var scanlines = true;
  function layout() {
    var dpr = window.devicePixelRatio || 1;
    var fit = Math.min(window.innerWidth * dpr / W, window.innerHeight * dpr / H);
    // Whole-number scaling keeps every pixel the same size; small screens fall back to fractional.
    var k = fit >= 2 ? Math.floor(fit) : fit;
    var cw = W * k / dpr, ch = H * k / dpr;
    canvas.style.width = cw + "px";
    canvas.style.height = ch + "px";
    var on = scanlines && k >= 2;
    crt.style.backgroundImage = on ? "linear-gradient(rgba(0,0,0,0) 62%, rgba(0,0,0,0.32) 62%)" : "none";
    crt.style.backgroundSize = "100% " + (ch / H) + "px";
  }
  window.addEventListener("resize", layout);
  layout();
  LT.scanlines = function (on) { scanlines = on; layout(); };

  // ------------------------------------------------------------ input

  var keys = new Set(), pressed = [];
  var noScroll = new Set(["Space", "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "Tab", "Enter", "Slash", "Quote"]);
  window.addEventListener("keydown", function (e) {
    if (noScroll.has(e.code)) e.preventDefault();
    if (!e.repeat) pressed.push(e.code);
    keys.add(e.code);
    unlockAudio();
  });
  window.addEventListener("keyup", function (e) { keys.delete(e.code); });
  window.addEventListener("blur", function () { keys.clear(); mouseDown = false; touches.clear(); });
  LT.key = function (code) { return keys.has(code); };
  LT.nextKey = function () { return pressed.length ? pressed.shift() : null; };

  var mouseX = -1, mouseY = -1, mouseDown = false, touchMode = false;
  var clicks = [], click = null;
  var touches = new Map();
  function toCanvas(e) {
    var r = canvas.getBoundingClientRect();
    return [(e.clientX - r.left) * W / r.width, (e.clientY - r.top) * H / r.height];
  }
  canvas.addEventListener("pointerdown", function (e) {
    unlockAudio();
    canvas.focus();
    var p = toCanvas(e);
    clicks.push(p);
    if (e.pointerType === "mouse") {
      touchMode = false;
      mouseX = p[0]; mouseY = p[1];
      if (e.button === 0) mouseDown = true;
      return;
    }
    touchMode = true;
    e.preventDefault();
    try { canvas.setPointerCapture(e.pointerId); } catch (ignored) { /* not supported */ }
    touches.set(e.pointerId, { sx: p[0], sy: p[1], x: p[0], y: p[1], left: p[0] < W / 2 });
  });
  canvas.addEventListener("pointermove", function (e) {
    var p = toCanvas(e);
    if (e.pointerType === "mouse") { mouseX = p[0]; mouseY = p[1]; touchMode = false; return; }
    var t = touches.get(e.pointerId);
    if (t) { t.x = p[0]; t.y = p[1]; }
  });
  function pointerUp(e) {
    if (e.pointerType === "mouse") { if (e.button === 0) mouseDown = false; return; }
    touches.delete(e.pointerId);
  }
  window.addEventListener("pointerup", pointerUp);
  window.addEventListener("pointercancel", pointerUp);
  canvas.addEventListener("mouseleave", function () { mouseX = -1; mouseY = -1; });
  canvas.addEventListener("contextmenu", function (e) { e.preventDefault(); });

  LT.mouseX = function () { return mouseX; };
  LT.mouseY = function () { return mouseY; };
  LT.mouseDown = function () { return mouseDown; };
  LT.touchMode = function () { return touchMode; };
  LT.takeClick = function () { click = clicks.length ? clicks.shift() : null; return click !== null; };
  LT.clickX = function () { return click ? click[0] : -1; };
  LT.clickY = function () { return click ? click[1] : -1; };

  function find(left) {
    var found = null;
    touches.forEach(function (t) { if (t.left === left && !found) found = t; });
    return found;
  }
  // Left thumb: a virtual stick centred where the thumb first touched.
  LT.stickActive = function () { return find(true) !== null; };
  LT.stickOriginX = function () { var t = find(true); return t ? t.sx : 0; };
  LT.stickOriginY = function () { var t = find(true); return t ? t.sy : 0; };
  LT.stickX = function () { var t = find(true); return t ? Math.max(-1, Math.min(1, (t.x - t.sx) / 24)) : 0; };
  LT.stickY = function () { var t = find(true); return t ? Math.max(-1, Math.min(1, (t.y - t.sy) / 24)) : 0; };
  // Right thumb: aim at the touched point and fire.
  LT.aimActive = function () { return find(false) !== null; };
  LT.aimX = function () { var t = find(false); return t ? t.x : 0; };
  LT.aimY = function () { var t = find(false); return t ? t.y : 0; };

  // ------------------------------------------------------------ sound (Web Audio)

  var audio = null, muted = false, noiseBuffer = null;
  function unlockAudio() {
    if (!audio) {
      try { audio = new (window.AudioContext || window.webkitAudioContext)(); } catch (e) { return; }
    }
    if (audio.state === "suspended") audio.resume();
  }
  function tone(type, f0, f1, dur, vol, delay) {
    var t = audio.currentTime + (delay || 0);
    var o = audio.createOscillator(), g = audio.createGain();
    o.type = type;
    o.frequency.setValueAtTime(f0, t);
    o.frequency.exponentialRampToValueAtTime(f1, t + dur);
    g.gain.setValueAtTime(vol, t);
    g.gain.linearRampToValueAtTime(0, t + dur);
    o.connect(g);
    g.connect(audio.destination);
    o.start(t);
    o.stop(t + dur + 0.02);
  }
  function noise(dur, vol) {
    if (!noiseBuffer) {
      // Stepped noise like an 8-bit sound chip.
      noiseBuffer = audio.createBuffer(1, audio.sampleRate, audio.sampleRate);
      var data = noiseBuffer.getChannelData(0), v = 1;
      for (var i = 0; i < data.length; i++) {
        if (i % 4 === 0) v = Math.random() < 0.5 ? -1 : 1;
        data[i] = v;
      }
    }
    var t = audio.currentTime;
    var src = audio.createBufferSource(), g = audio.createGain();
    src.buffer = noiseBuffer;
    src.playbackRate.setValueAtTime(1, t);
    src.playbackRate.exponentialRampToValueAtTime(0.15, t + dur);
    g.gain.setValueAtTime(vol, t);
    g.gain.linearRampToValueAtTime(0, t + dur);
    src.connect(g);
    g.connect(audio.destination);
    src.start(t);
    src.stop(t + dur);
  }
  LT.sfx = function (id) {
    if (!audio || muted || audio.state !== "running") return;
    switch (id) {
      case 0: tone("square", 1400, 300, 0.09, 0.05); break;               // zap
      case 1: noise(0.35, 0.18); break;                                    // tagged
      case 2: [523, 659, 784, 1047].forEach(function (f, i) { tone("square", f, f, 0.12, 0.05, i * 0.12); }); break;
      case 3: tone("square", 880, 880, 0.04, 0.04); break;                 // menu blip
      case 4: tone("square", 660, 1320, 0.1, 0.05); break;                 // select
    }
  };
  LT.setMuted = function (m) { muted = m; };

  // ------------------------------------------------------------ storage, links, loop

  LT.load = function (key) {
    try { return window.localStorage.getItem("lasertag." + key); } catch (e) { return null; }
  };
  LT.save = function (key, value) {
    try { window.localStorage.setItem("lasertag." + key, value); } catch (e) { /* private mode */ }
  };
  LT.open = function (url) { window.open(url, "_blank", "noopener"); };
  LT.coarsePointer = function () { return !!(window.matchMedia && window.matchMedia("(pointer: coarse)").matches); };

  LT.start = function (frame) {
    var last = performance.now();
    function step(now) {
      var dt = Math.min(0.05, Math.max(0, (now - last) / 1000));
      last = now;
      try {
        frame(dt);
      } catch (e) {
        LT.fail("Laser Tag crashed: " + e);
        return;
      }
      window.requestAnimationFrame(step);
    }
    window.requestAnimationFrame(step);
    canvas.focus();
  };

  LT.fail = function (message) {
    var el = document.getElementById("error");
    el.textContent = message;
    el.style.display = "flex";
    if (window.console) console.error(message);
  };
})();
