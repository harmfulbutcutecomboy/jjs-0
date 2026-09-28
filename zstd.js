/* Browser zstd: utf-8 bytes -> zstd frame -> base64 (KLUv/… like jsontotable.org). */
(function (root) {
  let compressFn = null;
  let ready = null;

  function u8ToB64(u8) {
    const chunk = 0x8000;
    let bin = '';
    for (let i = 0; i < u8.length; i += chunk) {
      bin += String.fromCharCode.apply(null, u8.subarray(i, i + chunk));
    }
    return btoa(bin);
  }

  function loadScript(src) {
    return new Promise(function (resolve, reject) {
      const s = document.createElement('script');
      s.src = src;
      s.onload = function () { resolve(); };
      s.onerror = function () { reject(new Error('script ' + src)); };
      document.head.appendChild(s);
    });
  }

  function fromZstdCodec() {
    return new Promise(function (resolve, reject) {
      if (!root.ZstdCodec || typeof root.ZstdCodec.run !== 'function') {
        reject(new Error('no ZstdCodec'));
        return;
      }
      try {
        root.ZstdCodec.run(function (zstd) {
          try {
            const simple = new zstd.Simple();
            compressFn = function (u8, level) {
              try { return simple.compress(u8, level || 22); }
              catch (e) { return simple.compress(u8); }
            };
            resolve(compressFn);
          } catch (e) { reject(e); }
        });
      } catch (e) { reject(e); }
    });
  }

  async function fromEsm() {
    const mod = await import('https://cdn.jsdelivr.net/npm/@bokuweb/zstd-wasm@0.0.27/+esm');
    if (typeof mod.default === 'function') await mod.default();
    else if (typeof mod.init === 'function') await mod.init();
    const fn = mod.compress || (mod.default && mod.default.compress);
    if (typeof fn !== 'function') throw new Error('esm zstd has no compress');
    compressFn = function (u8, level) { return fn(u8, level || 22); };
    return compressFn;
  }

  function ensure() {
    if (compressFn) return Promise.resolve(compressFn);
    if (ready) return ready;
    ready = (async function () {
      try { return await fromZstdCodec(); } catch (e) {}
      const cdns = [
        'https://cdn.jsdelivr.net/npm/zstd-codec@0.1.4/index.js',
        'https://unpkg.com/zstd-codec@0.1.4/index.js'
      ];
      for (let i = 0; i < cdns.length; i++) {
        try {
          await loadScript(cdns[i]);
          return await fromZstdCodec();
        } catch (e) {}
      }
      return fromEsm();
    })();
    return ready;
  }

  root.JJSZstd = {
    ready: ensure,
    async compressText(text, level) {
      const fn = await ensure();
      const bytes = new TextEncoder().encode(String(text || ''));
      const out = fn(bytes, level || 22);
      if (!out) throw new Error('compress returned empty');
      const u8 = out instanceof Uint8Array ? out : new Uint8Array(out);
      return u8ToB64(u8);
    }
  };
})(window);
