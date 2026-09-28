(function () {
  const canvas = document.getElementById('space');
  if (!canvas) return;
  canvas.style.zIndex = '-1';
  canvas.style.pointerEvents = 'none';
  canvas.style.position = 'fixed';
  const ctx = canvas.getContext('2d', { alpha: false, desynchronized: true });
  const stars = [];
  let w = 0, h = 0, t = 0, raf = 0, paused = false;

  function dpr() {
    return Math.min(1.15, window.devicePixelRatio || 1);
  }

  function resize() {
    const cw = Math.max(1, Math.min(window.innerWidth || 1, 900));
    const ch = Math.max(1, Math.min(window.innerHeight || 1, 1600));
    const scale = dpr();
    w = canvas.width = Math.floor(cw * scale);
    h = canvas.height = Math.floor(ch * scale);
    canvas.style.width = (window.innerWidth || cw) + 'px';
    canvas.style.height = (window.innerHeight || ch) + 'px';
    stars.length = 0;
    const n = Math.min(90, Math.floor((w * h) / 28000));
    for (let i = 0; i < n; i++) {
      stars.push({
        x: Math.random() * w,
        y: Math.random() * h,
        z: Math.random() * 0.8 + 0.2,
        r: Math.random() * 1.2 + 0.2,
        tw: Math.random() * Math.PI * 2
      });
    }
  }

  function paintStill() {
    if (!ctx || !w) return;
    ctx.fillStyle = '#07080d';
    ctx.fillRect(0, 0, w, h);
  }

  function frame() {
    if (paused) return;
    t += 0.008;
    ctx.fillStyle = '#07080d';
    ctx.fillRect(0, 0, w, h);

    const gx = w * (0.35 + Math.sin(t * 0.15) * 0.08);
    const gy = h * (0.3 + Math.cos(t * 0.12) * 0.06);
    const g = ctx.createRadialGradient(gx, gy, 0, gx, gy, Math.max(w, h) * 0.55);
    g.addColorStop(0, 'rgba(90, 120, 210, 0.22)');
    g.addColorStop(0.45, 'rgba(30, 24, 70, 0.06)');
    g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, w, h);

    for (let i = 0; i < stars.length; i++) {
      const s = stars[i];
      const tw = 0.45 + 0.55 * Math.abs(Math.sin(t * 1.4 + s.tw));
      ctx.beginPath();
      ctx.fillStyle = 'rgba(240,246,255,' + (0.35 + tw * s.z) + ')';
      ctx.arc(s.x, s.y, s.r * s.z * dpr(), 0, Math.PI * 2);
      ctx.fill();
      s.y += s.z * 0.12;
      if (s.y > h) { s.y = 0; s.x = Math.random() * w; }
    }
    raf = requestAnimationFrame(frame);
  }

  function pause() {
    paused = true;
    if (raf) cancelAnimationFrame(raf);
    raf = 0;
    try {
      canvas.width = 1;
      canvas.height = 1;
      w = 1; h = 1;
      stars.length = 0;
    } catch (e) {}
  }

  function resume() {
    if (!paused && raf) return;
    paused = false;
    resize();
    paintStill();
    if (!document.hidden) raf = requestAnimationFrame(frame);
  }

  function onHide() {
    pause();
  }

  function onShow() {
    if (sessionStorage.getItem('jjs-picking')) return;
    resume();
  }

  document.addEventListener('visibilitychange', function () {
    if (document.hidden) onHide();
    else onShow();
  });
  window.addEventListener('pagehide', onHide);
  window.addEventListener('freeze', onHide);
  window.addEventListener('pageshow', onShow);
  window.addEventListener('blur', function () {
    if (sessionStorage.getItem('jjs-picking')) onHide();
  });
  window.addEventListener('resize', function () {
    if (paused || document.hidden) return;
    resize();
  });

  window.JJSSpace = { pause: pause, resume: resume };

  if (document.hidden) {
    paused = true;
    return;
  }
  resize();
  raf = requestAnimationFrame(frame);
})();
