const $ = (id) => document.getElementById(id);
const ICONS = window.JJS_NODE_ICONS || {};

const state = {
  mode: 'convert',
  fileName: '',
  tree: null,
  nodeMap: {},
  browseId: 'root',
  selected: null,
  checked: {},
  search: '',
  duration: 0,
  position: '0, 0, 0',
  skillName: 'converted',
  skillKey: 1,
  packMode: false,
  branchMode: false,
  sectionMark: false,
  runOnServer: true,
  parseCameras: false,
  parseMeshes: false,
  recolor: false,
  colors: { main: '#ff3355', accent: '#f7d7f8', other: '#ffffff' },
  detected: null,
  output: '',
  compact: '',
  packs: [],
  skills: [],
  skillIndex: 0,
  branch: 'Default',
  selectedNode: -1,
  nameCache: {},
  textureNames: {},
  pendingFiles: [],
  parsing: false
};

const MAX_QUEUE_BYTES = 500 * 1024 * 1024;

function toast(msg) {
  if ($('statusText')) $('statusText').textContent = msg;
  if ($('gateStatus')) $('gateStatus').textContent = msg;
}
function escapeHtml(s) {
  return String(s || '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function sanitizeFilename(name) {
  return String(name || 'file').replace(/[<>:"/\\|?*\x00-\x1f]/g, '_').slice(0, 80);
}

function collectNodeParticles(node) {
  const out = [];
  (node.particles || []).forEach((p) => out.push(p));
  (node.children || []).forEach((c) => collectNodeParticles(c).forEach((p) => out.push(p)));
  return out;
}
function collectKind(node, key) {
  const out = [];
  (node[key] || []).forEach((p) => out.push(p));
  (node.children || []).forEach((c) => collectKind(c, key).forEach((p) => out.push(p)));
  return out;
}
function indexTree(node, map) {
  map[node.id] = node;
  (node.children || []).forEach((c) => indexTree(c, map));
}
function isFolder(n) {
  return n && (n.type === 'ParticleFolder' || /^(Folder|Configuration|Actor)$/i.test(n.className || ''));
}
function hostCount(n) {
  if (!n) return 0;
  if (Number.isFinite(Number(n.count))) return Number(n.count);
  return collectNodeParticles(n).length + collectKind(n, 'meshes').length + collectKind(n, 'cameras').length + collectKind(n, 'sounds').length;
}
function collectLeaves(node) {
  const out = [];
  function walk(n) {
    if (!n) return;
    const kids = n.children || [];
    const folder = isFolder(n) && kids.length;
    if (!folder && hostCount(n) > 0) out.push(n);
    kids.forEach(walk);
  }
  walk(node);
  return out;
}

function parseRgbList(str) {
  const out = [];
  const re = /(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})/g;
  let m;
  const s = String(str || '');
  while ((m = re.exec(s))) out.push({ r: +m[1], g: +m[2], b: +m[3] });
  return out;
}
function hexToRgb(hex) {
  const s = String(hex || '').trim().replace('#', '');
  if (/^[0-9a-fA-F]{6}$/.test(s)) return { r: parseInt(s.slice(0, 2), 16), g: parseInt(s.slice(2, 4), 16), b: parseInt(s.slice(4, 6), 16) };
  if (/^[0-9a-fA-F]{3}$/.test(s)) return { r: parseInt(s[0] + s[0], 16), g: parseInt(s[1] + s[1], 16), b: parseInt(s[2] + s[2], 16) };
  const m = String(hex).match(/(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})/);
  if (m) return { r: +m[1], g: +m[2], b: +m[3] };
  return { r: 255, g: 255, b: 255 };
}
function rgbToHex(c) {
  const h = (n) => Math.max(0, Math.min(255, n | 0)).toString(16).padStart(2, '0');
  return '#' + h(c.r) + h(c.g) + h(c.b);
}
function rgbKey(c) { return `${c.r},${c.g},${c.b}`; }
function rgbDist(a, b) {
  const dr = a.r - b.r, dg = a.g - b.g, db = a.b - b.b;
  return dr * dr + dg * dg + db * db;
}
function detectPalette(particles) {
  const counts = {};
  (particles || []).forEach((p) => parseRgbList(p.COLOR).forEach((c) => {
    if (c.r + c.g + c.b < 12) return;
    const k = rgbKey(c);
    counts[k] = counts[k] || { c, n: 0 };
    counts[k].n++;
  }));
  const items = Object.values(counts).sort((a, b) => b.n - a.n);
  if (!items.length) return { main: { r: 255, g: 51, b: 85 }, accent: { r: 247, g: 215, b: 248 }, other: { r: 255, g: 255, b: 255 } };
  const main = items[0].c;
  let accent = items[1] ? items[1].c : main;
  let best = -1;
  items.forEach((it, i) => {
    if (!i) return;
    const score = Math.sqrt(rgbDist(it.c, main));
    if (score > best) { best = score; accent = it.c; }
  });
  const other = items[Math.min(2, items.length - 1)].c;
  return { main, accent, other };
}
function remapColorString(str, detected, next) {
  const parts = parseRgbList(str);
  if (!parts.length) return str;
  return parts.map((c) => {
    const dMain = rgbDist(c, detected.main);
    const dAcc = rgbDist(c, detected.accent);
    const dOth = rgbDist(c, detected.other);
    const role = (dMain <= dAcc && dMain <= dOth) ? 'main' : (dAcc <= dOth ? 'accent' : 'other');
    return rgbKey(next[role]);
  }).join(' ');
}

function sanitizeSpread(v) {
  if (window.JJSParser && typeof window.JJSParser.spreadToStr === 'function') return window.JJSParser.spreadToStr(v);
  const n = String(v || '0, 0, 0').split(/[,\s]+/).map(Number).filter((x) => Number.isFinite(x));
  let x = n[0] || 0, y = n[1] || 0;
  if (Math.abs(x) > 720 && Math.abs(y) < 1) {
    const s = String(Math.round(Math.abs(x)));
    if (s.length >= 4 && s.length % 2 === 0) {
      x = Number(s.slice(0, s.length / 2));
      y = Number(s.slice(s.length / 2));
    }
  }
  if (x > 720) x = 720;
  if (y > 720) y = 720;
  return x.toFixed(2) + ', ' + y.toFixed(2) + ', 0.00';
}

function lineFromParticle(p, i, over) {
  return {
    SIZE: String(p.SIZE || '0.0000'),
    SPEED: String(p.SPEED || '0.00, 0.00'),
    NAME: String(p.NAME || p.Name || ('PARTICLE_' + (i + 1))).slice(0, 80),
    'PART SIZE': String(p['PART SIZE'] || '0, 0, 0'),
    'LIGHT EMISSION': Number(p['LIGHT EMISSION']) || 0,
    'EMIT COUNT': Number(p['EMIT COUNT']) || 0,
    TEXTURE: Number(p.TEXTURE) || 0,
    DURATION: Number(over.duration) || 0,
    BRIGHTNESS: p.BRIGHTNESS != null ? Number(p.BRIGHTNESS) : 1,
    LIFETIME: String(p.LIFETIME || '0.00, 0.00'),
    ZOFFSET: Number(p.ZOFFSET) || 0,
    COLOR: over.recolor ? remapColorString(p.COLOR || '255,255,255 255,255,255', over.detected, over.next) : String(p.COLOR || '255,255,255 255,255,255'),
    ACCELERATION: String(p.ACCELERATION || '0, 0, 0'),
    POSITION: String(over.position || p.POSITION || '0, 0, 0'),
    'RUN ON SERVER': over && over.runOnServer != null ? !!over.runOnServer : p['RUN ON SERVER'] !== false,
    'LIGHT INFLUENCE': Number(p['LIGHT INFLUENCE']) || 0,
    'FLIPBOOK FRAMERATE': String(p['FLIPBOOK FRAMERATE'] || '1.00, 1.00'),
    'FLIPBOOK SIZE': String(p['FLIPBOOK SIZE'] != null ? p['FLIPBOOK SIZE'] : '1, 1'),
    K_NAME: 'PARTICLE',
    TRANSPARENCY: String(p.TRANSPARENCY || '0.00,0.00'),
    ROTATION: String(p.ROTATION || '0.00, 0.00'),
    RATE: p.RATE != null ? Number(p.RATE) : 20,
    'ROT SPEED': String(p['ROT SPEED'] || '0.00, 0.00'),
    'ORIENTATION TYPE': String(p['ORIENTATION TYPE'] || 'FacingCamera'),
    'EMISSION DIRECTION': String(p['EMISSION DIRECTION'] || 'Top'),
    'LOCK TO PART': p['LOCK TO PART'] !== false,
    'SPREAD ANGLE': sanitizeSpread(p['SPREAD ANGLE'] || '0.00, 0.00, 0.00'),
    'FLIPBOOK MODE': String(p['FLIPBOOK MODE'] || 'OneShot'),
    DRAG: Number(p.DRAG) || 0,
    SQUASH: String(p.SQUASH || '0.00,0.00'),
    'BODY PART': p['BODY PART'] || 'HumanoidRootPart',
    'CLIENT SIDED': !!p['CLIENT SIDED'],
    'CANCEL ON INTERRUPT': !!p['CANCEL ON INTERRUPT'],
    'LAST HIT': p['LAST HIT'] != null ? p['LAST HIT'] : -1
  };
}

function applyOverToVisual(node, over) {
  const n = Object.assign({}, node);
  delete n._time;
  delete n._endTime;
  delete n._waitAfter;
  if (over && over.position && (!n.POSITION || n.POSITION === '0, 0, 0')) n.POSITION = over.position;
  if (over && over.recolor && n.COLOR) n.COLOR = remapColorString(n.COLOR, over.detected, over.next);
  if (over && over.recolor && n['ALT COLOR']) n['ALT COLOR'] = remapColorString(n['ALT COLOR'], over.detected, over.next);
  if (over && over.runOnServer != null) n['RUN ON SERVER'] = !!over.runOnServer;
  return n;
}

function textureKeyOf(n) {
  if (!n) return '0';
  if (n.K_NAME === 'VISUAL' && n.EFFECT === 'Mesh') return 'mesh:' + String(n.AMOUNT || n.TEXTURE || 0);
  const t = n.TEXTURE != null ? n.TEXTURE : n.Texture;
  return String(t || 0);
}
function colorWord(n) {
  const rgb = parseRgbList(n && n.COLOR)[0];
  if (!rgb) return 'White';
  const { r, g, b } = rgb;
  const mx = Math.max(r, g, b), mn = Math.min(r, g, b);
  if (mx < 28) return 'Black';
  if (mn > 230) return 'White';
  if (r > 200 && g > 180 && b < 80) return 'Gold';
  if (r > g + 30 && r > b + 30) return r > 180 && g < 80 ? 'Red' : 'Orange';
  if (g > r + 20 && g > b + 20) return 'Green';
  if (b > r + 20 && b > g + 10) return b > 180 && r > 80 ? 'Purple' : 'Blue';
  if (r > 160 && b > 160 && g < 120) return 'Pink';
  return rgbKey(rgb);
}
function sectionNameOf(n) {
  const tex = textureKeyOf(n).replace(/^mesh:/, '');
  const mapped = state.textureNames && state.textureNames[tex];
  if (mapped) return mapped;
  const raw = String(n && (n.NAME || n.Name) || '').trim();
  if (raw && !/^PARTICLE(_\d+)?$/i.test(raw) && raw !== 'Mesh' && raw !== 'Sound') return raw.slice(0, 48);
  return tex && tex !== '0' ? ('Texture ' + tex) : 'Section';
}
function makeConnect(signal) {
  return { RANGE: { m: null, t: 'numeric', v: 'inf' }, SIGNAL: String(signal || ''), TIME: 0.1, K_NAME: 'CONNECT' };
}
function defaultTag() { return { TAG: 'Label', SET: true, 'ADD/REMOVE': true, K_NAME: 'TAG', 'LAST HIT': -1, TIME: 1, CHECK: false }; }
function cleanAssetName(name, id) {
  let s = String(name || '').trim();
  s = s.replace(/^(Images|Decals|Textures|Meshes|Sounds)\//i, '');
  s = s.replace(/\.(png|jpg|jpeg|bmp|tga|webp)$/i, '');
  s = s.replace(/[_]+/g, ' ').replace(/\s+/g, ' ').trim();
  if (!s || /^(texture|decal|image|asset)$/i.test(s)) return id ? ('Texture ' + id) : 'Texture';
  return s.slice(0, 48);
}
function cloudKey() {
  const typed = $('rbxKey') ? String($('rbxKey').value || '').trim() : '';
  if (typed) return typed;
  try {
    const saved = localStorage.getItem('jjsRobloxKey');
    if (saved) return saved;
  } catch (e) {}
  const cfg = window.JJS_CONFIG || {};
  return String(cfg.ROBLOX_API_KEY || cfg.robloxApiKey || '').trim();
}
async function fetchAssetName(id) {
  const assetId = String(id || '');
  if (!assetId || assetId === '0') return '';
  if (state.textureNames[assetId]) return state.textureNames[assetId];
  const apiKey = cloudKey();
  const cfg = window.JJS_CONFIG || {};
  const proxy = String(cfg.ASSET_PROXY || '').trim();
  const urls = [];
  if (proxy) urls.push(proxy.replace(/\/?$/, '/') + assetId);
  urls.push('https://apis.roblox.com/assets/v1/assets/' + assetId);
  urls.push('https://apis.roblox.com/cloud/v2/assets/' + assetId);
  if (!apiKey && !proxy) {
    state.textureNames[assetId] = 'Texture ' + assetId;
    return state.textureNames[assetId];
  }
  for (let i = 0; i < urls.length; i++) {
    try {
      const headers = {};
      if (apiKey) headers['x-api-key'] = apiKey;
      const res = await fetch(urls[i], { method: 'GET', headers: headers, mode: 'cors' });
      if (!res.ok) continue;
      const data = await res.json();
      const raw = data.displayName || data.DisplayName || data.name || data.Name
        || (data.response && (data.response.displayName || data.response.name));
      const name = cleanAssetName(raw, assetId);
      if (name) {
        state.textureNames[assetId] = name;
        return name;
      }
    } catch (e) {}
  }
  state.textureNames[assetId] = 'Texture ' + assetId;
  return state.textureNames[assetId];
}
async function resolveTextureNames(nodes) {
  if (!state.textureNames) state.textureNames = {};
  const ids = {};
  function walk(n) {
    if (!n) return;
    collectNodeParticles(n).forEach((p) => {
      const id = String(p.TEXTURE || p.Texture || 0);
      if (id && id !== '0') ids[id] = true;
    });
    collectKind(n, 'meshes').forEach((m) => {
      const id = String(m.AMOUNT || m.TEXTURE || 0);
      if (id && id !== '0') ids[id] = true;
    });
  }
  (nodes || []).forEach(walk);
  const list = Object.keys(ids);
  if (!list.length) return;
  if (!cloudKey() && !(window.JJS_CONFIG && window.JJS_CONFIG.ASSET_PROXY)) {
    toast('No Open Cloud key — CONNECT labels will use texture ids. Paste a key in config.js or the key field.');
    return;
  }
  toast('Looking up ' + list.length + ' texture name' + (list.length === 1 ? '' : 's') + ' via Cloud API…');
  for (let i = 0; i < list.length; i++) await fetchAssetName(list[i]);
}
function markVfxSections(lines) {
  const src = (lines || []).slice();
  const texCounts = {};
  src.forEach((n) => {
    if (!n) return;
    if (n.K_NAME === 'PARTICLE' || (n.K_NAME === 'VISUAL' && n.EFFECT === 'Mesh')) {
      const k = textureKeyOf(n);
      texCounts[k] = (texCounts[k] || 0) + 1;
    }
  });
  let mainKey = null, mainN = 0;
  Object.keys(texCounts).forEach((k) => {
    if (texCounts[k] > mainN) { mainN = texCounts[k]; mainKey = k; }
  });
  const out = [];
  let lastKey = null;
  src.forEach((n) => {
    const markable = n && (n.K_NAME === 'PARTICLE' || (n.K_NAME === 'VISUAL' && n.EFFECT === 'Mesh'));
    const key = markable ? textureKeyOf(n) : lastKey;
    if (markable && key !== lastKey) {
      const main = key === mainKey;
      const label = sectionNameOf(n) + (main ? ' [MAIN]' : '') + ' [' + colorWord(n) + ']';
      out.push(makeConnect(label));
      lastKey = key;
    }
    out.push(n);
  });
  return out;
}

function linesFromHost(node, over) {
  const lines = [];
  collectNodeParticles(node).forEach((p, i) => lines.push(lineFromParticle(p, i, over)));
  collectKind(node, 'meshes').forEach((m) => lines.push(applyOverToVisual(m, over)));
  const cams = collectKind(node, 'cameras').slice().sort(function (a, b) {
    return (Number(a._time) || 0) - (Number(b._time) || 0);
  });
  cams.forEach((cam, i) => {
    lines.push(applyOverToVisual(cam, over));
    const wait = Number(cam._waitAfter);
    if (i < cams.length - 1 && Number.isFinite(wait) && wait > 0) {
      lines.push({ K_NAME: 'WAIT', TIME: Number(wait.toFixed(3)) });
    } else if (i < cams.length - 1 && cam._waitAfter == null) {
      const next = cams[i + 1];
      const dt = (Number(next._time) || 0) - (Number(cam._endTime != null ? cam._endTime : cam._time) || 0);
      lines.push({ K_NAME: 'WAIT', TIME: dt > 0.02 ? Number(dt.toFixed(3)) : 0.05 });
    }
  });
  collectKind(node, 'sounds').forEach((s) => {
    const n = Object.assign({}, s);
    if (over && over.runOnServer != null) n['RUN ON SERVER'] = !!over.runOnServer;
    lines.push(n);
  });
  return (over && over.sectionMark) ? markVfxSections(lines) : lines;
}

function parseSkillData(skill) {
  if (skill._data) return skill._data;
  let data = skill && skill.DATA;
  if (typeof data === 'string') {
    try { data = JSON.parse(data); } catch (e) { data = {}; }
  }
  data = data || {};
  if (!Array.isArray(data.Line)) data.Line = data.Line ? [].concat(data.Line) : [];
  if (!data.Branch || typeof data.Branch !== 'object') data.Branch = {};
  if (!data.Prop || typeof data.Prop !== 'object' || Array.isArray(data.Prop)) data.Prop = {};
  if (!data.Req || typeof data.Req !== 'object') data.Req = {};
  skill._data = data;
  return data;
}
function currentSkill() { return state.skills[state.skillIndex] || null; }
function currentData() {
  const s = currentSkill();
  return s ? parseSkillData(s) : { Line: [], Branch: {}, Prop: {}, Req: [] };
}
function currentLines() {
  const data = currentData();
  if (state.branch === 'Default') return data.Line;
  const br = data.Branch[state.branch];
  if (!br) return data.Line;
  if (!Array.isArray(br.Line)) br.Line = [];
  return br.Line;
}

function defaultParticle() {
  return {
    SIZE: '1, 0', SPEED: '5, 5', NAME: 'PARTICLE', 'PART SIZE': '0, 0, 0',
    'LIGHT EMISSION': 0, 'EMIT COUNT': 10, TEXTURE: 0, DURATION: 0, BRIGHTNESS: 1,
    LIFETIME: '0.5, 1', ZOFFSET: 0, COLOR: '255,255,255 0,0,0', ACCELERATION: '0, 0, 0',
    POSITION: '0, 0, 0', 'RUN ON SERVER': false, 'LIGHT INFLUENCE': 1,
    'FLIPBOOK FRAMERATE': '10, 10', 'FLIPBOOK SIZE': '0, 0', K_NAME: 'PARTICLE',
    TRANSPARENCY: '0, 1', ROTATION: '0, 0', RATE: 0, 'ROT SPEED': '0, 0',
    'ORIENTATION TYPE': 'FacingCamera', 'EMISSION DIRECTION': 'Top', 'LOCK TO PART': false,
    'SPREAD ANGLE': '0, 0, 0', 'FLIPBOOK MODE': 'OneShot', DRAG: 0, SQUASH: '0, 0',
    'BODY PART': 'HumanoidRootPart', 'CLIENT SIDED': false, 'CANCEL ON INTERRUPT': false, 'LAST HIT': -1
  };
}
function defaultMesh() {
  return {
    K_NAME: 'VISUAL', EFFECT: 'Mesh', AMOUNT: 1, TEXTURE: 0,
    SIZE: 1, 'ALT SIZE': 1, OPACITY: 1, 'ALT OPACITY': 1,
    POSITION: '0, 0, 0', 'ALT POSITION': '0, 0, 0',
    ROTATION: '0, 0, 0', 'ALT ROTATION': '0, 0, 0',
    COLOR: '255, 255, 255', 'ALT COLOR': '255, 255, 255',
    TIME: 0.3, 'PROJECTILE TAG': '', 'VISUAL TAG': '',
    'RUN ON SERVER': false, 'CLIENT SIDED': false, 'CANCEL ON INTERRUPT': false,
    'RELATIVE FROM BRANCH': false, 'BODY PART': 'HumanoidRootPart', 'LAST HIT': -1
  };
}
function defaultCamera() {
  return {
    K_NAME: 'VISUAL', EFFECT: 'Camera',
    POSITION: '0, 0, 0', 'ALT POSITION': '0, 0, 0',
    ROTATION: '0, 0, 0', 'ALT ROTATION': '0, 0, 0',
    COLOR: '255, 255, 255', 'ALT COLOR': '255, 255, 255',
    'EASING STYLE': 'Linear', 'EASING DIRECTION': 'In',
    'PROJECTILE TAG': '', 'VISUAL TAG': '',
    'RUN ON SERVER': false, 'CLIENT SIDED': false, 'CANCEL ON INTERRUPT': false,
    'RELATIVE FROM BRANCH': false, 'BODY PART': 'HumanoidRootPart',
    TIME: 0.1, 'LAST HIT': -1
  };
}
function defaultSfx() {
  return {
    K_NAME: 'SFX', ID: 0, SPEED: 1, VOLUME: 0.5, START: 0, END: 500,
    'FADE IN': 0, 'FADE OUT': 0, CANCEL: false, GLOBAL: false,
    'CLIENT SIDED': false, 'LAST HIT': -1, 'PROJECTILE TAG': ''
  };
}
function defaultWait() { return { K_NAME: 'WAIT', TIME: 0.1 }; }
function defaultConnect() { return makeConnect('Section'); }

function buildSkillJson(lines, meta, extra) {
  const data = {
    Line: lines,
    Prop: (extra && extra.Prop) || {},
    Req: (extra && extra.Req) || [],
    Branch: (extra && extra.Branch) || {}
  };
  const skill = {
    ADD: !!(extra && extra.ADD),
    NAME: String(meta.name || 'converted').slice(0, 80),
    COOLDOWN: Number((extra && extra.COOLDOWN) || 0),
    KEY: Number(meta.key) || 1,
    K_NAME: 'SKILL',
    DATA: JSON.stringify(data),
    'TOOL TIP': (extra && extra.tooltip) || ''
  };
  return JSON.stringify([skill]);
}

function serializeSkills() {
  return state.skills.map((s) => {
    const data = parseSkillData(s);
    return {
      ADD: !!s.ADD,
      NAME: s.NAME,
      COOLDOWN: Number(s.COOLDOWN) || 0,
      KEY: Number(s.KEY) || 1,
      K_NAME: 'SKILL',
      DATA: JSON.stringify(data),
      'TOOL TIP': s['TOOL TIP'] || ''
    };
  });
}

function visibleChildren(node) {
  const q = String(state.search || '').trim().toLowerCase();
  const kids = (node && node.children) || [];
  if (!q) return kids;
  const out = [];
  const seen = new Set();
  function walk(n) {
    if (!n || seen.has(n.id)) return;
    const hay = [n.name, n.className, n.path, n.type].join(' ').toLowerCase();
    if (hay.includes(q)) { seen.add(n.id); out.push(n); return; }
    (n.children || []).forEach(walk);
  }
  kids.forEach(walk);
  return kids.length ? out.length ? out : kids : [];
}

function renderTree() {
  const root = state.tree;
  const box = $('tree');
  box.innerHTML = '';
  if (!root) {
    box.innerHTML = '<div class="hint" style="padding:8px">Open an .rbxl / .rbxm / import code.</div>';
    return;
  }
  const node = state.nodeMap[state.browseId] || root;
  const crumbs = [];
  let walk = node;
  while (walk && walk.id !== root.id) {
    crumbs.unshift(walk);
    walk = state.nodeMap[walk.parentId];
  }
  $('explorerPath').textContent = crumbs.length ? crumbs.map((c) => c.name).join(' / ') : 'Workspace';
  if (node.id !== root.id) {
    const back = document.createElement('div');
    back.className = 'row';
    back.innerHTML = '<span class="dot folder"></span><span class="name">..</span><span class="meta">up</span>';
    back.onclick = () => { state.browseId = node.parentId || root.id; renderTree(); };
    box.appendChild(back);
  }
  visibleChildren(node).forEach((child, i) => {
    const row = document.createElement('div');
    row.className = 'row' + (state.selected && state.selected.id === child.id ? ' on' : '');
    row.style.animationDelay = (i * 12) + 'ms';
    const count = hostCount(child);
    const folder = isFolder(child) && (child.children || []).length;
    const checked = !!state.checked[child.id];
    const bits = [];
    if ((child.particles || []).length) bits.push((child.particles || []).length + 'pe');
    if ((child.meshes || []).length) bits.push((child.meshes || []).length + 'mesh');
    if ((child.cameras || []).length) bits.push((child.cameras || []).length + 'cam');
    if ((child.sounds || []).length) bits.push((child.sounds || []).length + 'sfx');
    if ((child.beams || []).length) bits.push((child.beams || []).length + 'beam');
    const cls = folder ? 'folder' : ((child.meshes || []).length && !(child.particles || []).length ? 'mesh' : '');
    row.innerHTML = `<input type="checkbox" ${checked ? 'checked' : ''} data-id="${child.id}"><span class="dot ${cls}"></span><span class="name">${escapeHtml(child.name || child.className)}</span><span class="meta">${bits.join(' · ') || count}</span>`;
    row.querySelector('input').onclick = (e) => {
      e.stopPropagation();
      if (e.target.checked) state.checked[child.id] = true;
      else delete state.checked[child.id];
    };
    row.onclick = () => {
      if (folder) state.browseId = child.id;
      state.selected = child;
      applyDetected(collectNodeParticles(child));
      renderTree();
      renderHud();
    };
    box.appendChild(row);
  });
}

function applyDetected(particles) {
  state.detected = detectPalette(particles);
  ['main', 'accent', 'other'].forEach((k) => {
    const hex = rgbToHex(state.detected[k]);
    $(k + 'Found').style.background = hex;
    $(k + 'FoundLabel').textContent = rgbKey(state.detected[k]);
    if (!state.recolor) {
      state.colors[k] = hex;
      $(k + 'Color').value = hex;
      $(k + 'Hex').value = hex;
    }
  });
}

function renderHud() {
  const n = state.selected;
  const count = n ? hostCount(n) : (state.tree ? hostCount(state.tree) : 0);
  $('hudTitle').textContent = n ? n.name : (state.fileName || 'No place loaded');
  const extra = n ? [
    collectNodeParticles(n).length + ' particles',
    collectKind(n, 'meshes').length + ' meshes',
    collectKind(n, 'cameras').length + ' cameras',
    collectKind(n, 'sounds').length + ' sounds',
    collectKind(n, 'beams').length + ' beams (ignored)'
  ].join(' · ') : 'Drop a Roblox place or model.';
  $('hudMeta').innerHTML = n ? extra : 'Drop a Roblox place, model, or JJS import code.';
  $('selCount').textContent = count + ' items';
}

function currentTargets() {
  const ids = Object.keys(state.checked);
  if (ids.length) return ids.map((id) => state.nodeMap[id]).filter(Boolean);
  if (state.packMode) {
    const node = state.selected || state.tree;
    const leaves = collectLeaves(node);
    return leaves.length ? leaves : (node ? [node] : []);
  }
  return state.selected ? [state.selected] : (state.tree ? [state.tree] : []);
}
function over() {
  return {
    duration: state.duration,
    position: state.position,
    recolor: state.recolor,
    sectionMark: state.sectionMark,
    runOnServer: state.runOnServer,
    detected: state.detected || detectPalette([]),
    next: { main: hexToRgb(state.colors.main), accent: hexToRgb(state.colors.accent), other: hexToRgb(state.colors.other) }
  };
}

function skillFromLines(lines, name, key) {
  return {
    ADD: false,
    NAME: name,
    COOLDOWN: 0,
    KEY: key,
    K_NAME: 'SKILL',
    DATA: JSON.stringify({ Line: lines, Prop: {}, Req: [], Branch: {} }),
    'TOOL TIP': ''
  };
}

function hostLabel(node, fallback) {
  let n = node && node.name;
  if (n && typeof n === 'object') n = n.name || n.Name || n.className || '';
  n = String(n == null ? '' : n);
  if (!n || n === '[object Object]' || n === '[object object]') {
    n = String((node && node.className) || fallback || 'Branch');
  }
  return n;
}

function uniquePackBranchName(used, raw) {
  let base = sanitizeFilename(hostLabel({ name: raw }, 'Branch')).replace(/_/g, ' ') || 'Branch';
  if (base.toLowerCase() === 'default') base = 'Default 2';
  let name = base, n = 2;
  while (used[name]) { name = base + ' ' + n; n++; }
  used[name] = true;
  return name;
}

async function exportCurrent() {
  const targets = currentTargets();
  if (!targets.length) { toast('Nothing selected.'); return; }
  if (state.sectionMark) {
    try { await resolveTextureNames(targets); } catch (e) {}
  }
  const packs = [];
  const skills = [];
  const ov = over();

  if (state.packMode && state.branchMode && targets.length) {
    const used = {};
    let defaultLines = null;
    const branches = {};
    let total = 0;
    targets.forEach((node, idx) => {
      const lines = linesFromHost(node, ov);
      if (!lines.length) return;
      total += lines.length;
      if (!defaultLines) {
        defaultLines = lines;
        used.Default = true;
      } else {
        const bname = uniquePackBranchName(used, hostLabel(node, 'Branch ' + idx));
        branches[bname] = { Req: [], Line: lines };
      }
    });
    if (!defaultLines) { toast('No VFX in selection.'); return; }
    const name = sanitizeFilename(state.skillName || hostLabel(state.selected, state.fileName || 'pack'));
    const skill = {
      ADD: false,
      NAME: name,
      COOLDOWN: 0,
      KEY: state.skillKey,
      K_NAME: 'SKILL',
      DATA: JSON.stringify({ Line: defaultLines, Prop: {}, Req: [], Branch: branches }),
      'TOOL TIP': ''
    };
    packs.push({ name: name, json: JSON.stringify([skill]), count: total });
    skills.push(skill);
    toast('Packed ' + targets.length + ' hosts into 1 skill / ' + Object.keys(branches).length + ' branches.');
  } else {
    targets.forEach((node, idx) => {
      const lines = linesFromHost(node, ov);
      if (!lines.length) return;
      const name = sanitizeFilename(hostLabel(node, state.skillName || 'pack'));
      const skill = skillFromLines(lines, name, state.skillKey + idx);
      packs.push({ name: name, json: JSON.stringify([skill]), count: lines.length });
      skills.push(skill);
    });
    if (!packs.length) { toast('No VFX in selection.'); return; }
    toast((state.packMode || packs.length > 1)
      ? ('Packed ' + packs.length + ' skills, ' + packs.reduce((n, p) => n + p.count, 0) + ' nodes.')
      : ('Exported ' + packs[0].count + ' nodes from ' + packs[0].name + '.'));
  }

  state.packs = packs;
  state.skills = skills;
  state.skillIndex = 0;
  state.branch = 'Default';
  state.selectedNode = -1;
  state.output = packs.length === 1 ? packs[0].json : JSON.stringify(serializeSkills());
  $('output').textContent = state.output;
  compressMaybe(state.output);
  renderEditor();
}

async function compressMaybe(json) {
  $('compactOut').textContent = 'Compressing with zstd…';
  try {
    if (!window.JJSZstd) throw new Error('zstd helper missing');
    state.compact = await window.JJSZstd.compressText(json, 22);
    $('compactOut').textContent = state.compact;
  } catch (e) {
    console.error(e);
    state.compact = '';
    $('compactOut').textContent = 'zstd failed: ' + (e && e.message ? e.message : e) + ' — copy JSON instead.';
  }
}

function download(name, text) {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([text], { type: 'text/plain' }));
  a.download = name;
  a.click();
  URL.revokeObjectURL(a.href);
}
async function copyText(text) {
  try { await navigator.clipboard.writeText(text); toast('Copied.'); }
  catch (e) { toast('Copy failed — long-press the box.'); }
}

function b64ToU8(s) {
  const clean = String(s || '').replace(/\s+/g, '');
  const bin = atob(clean);
  const u8 = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) u8[i] = bin.charCodeAt(i);
  return u8;
}
function decodeImportText(text) {
  const raw = String(text || '').trim();
  if (!raw) throw new Error('empty file');
  if (/^KLUv\//.test(raw) || raw.indexOf('KLUv') === 0) {
    const fz = window.fzstd || window.FZstd;
    if (!fz || typeof fz.decompress !== 'function') throw new Error('fzstd missing for KLUv');
    const out = fz.decompress(b64ToU8(raw));
    const json = new TextDecoder().decode(out);
    return JSON.parse(json);
  }
  if (raw[0] === '[' || raw[0] === '{') return JSON.parse(raw);
  const start = raw.search(/[\[{]/);
  if (start >= 0) return JSON.parse(raw.slice(start));
  throw new Error('not JSON or KLUv');
}

function linesFromSkill(skill) {
  const data = parseSkillData(skill);
  const out = (data.Line || []).slice();
  Object.keys(data.Branch || {}).forEach((k) => {
    const br = data.Branch[k] || {};
    if (Array.isArray(br.Line)) br.Line.forEach((p) => out.push(p));
  });
  return out;
}

function treeFromSkills(skills, fileName) {
  const children = (skills || []).map(function (skill, i) {
    const data = parseSkillData(skill);
    const lines = (data.Line || []).slice();
    return {
      id: 'pack' + i,
      name: String(skill.NAME || skill.Name || ('Pack ' + (i + 1))),
      className: 'Skill',
      type: 'ParticleHost',
      children: [],
      particles: lines.filter((n) => n && n.K_NAME === 'PARTICLE'),
      meshes: lines.filter((n) => n && n.K_NAME === 'VISUAL' && n.EFFECT === 'Mesh'),
      cameras: lines.filter((n) => n && n.K_NAME === 'VISUAL' && n.EFFECT === 'Camera'),
      sounds: lines.filter((n) => n && n.K_NAME === 'SFX'),
      beams: [],
      count: lines.length,
      parentId: 'root'
    };
  });
  return {
    tree: {
      id: 'root', name: fileName || 'Import', className: 'Folder', type: 'ParticleFolder',
      children: children, particles: [], meshes: [], cameras: [], sounds: [], beams: [],
      count: children.reduce((n, c) => n + c.count, 0)
    },
    hosts: children,
    particles: children.reduce((a, c) => a.concat(c.particles), [])
  };
}

function looksLikePlaceName(name) { return /\.(rbxlx?|rbxmx?)$/i.test(String(name || '')); }
function looksLikeImportName(name) { return /\.(txt|json|particle\.txt|particle\.json)$/i.test(String(name || '')); }

function enterStudio(mode) {
  document.body.classList.remove('gated');
  const gate = $('gate');
  const shell = $('appShell');
  if (gate) gate.classList.add('hidden');
  if (shell) shell.classList.remove('hidden');
  setMode(mode || state.mode || 'convert');
}

function loadSkills(list, fileName) {
  state.skills = list.map((s) => {
    parseSkillData(s);
    return s;
  });
  state.skillIndex = 0;
  state.branch = 'Default';
  state.selectedNode = -1;
  const parsed = treeFromSkills(state.skills, fileName);
  state.fileName = fileName || 'Import';
  state.tree = parsed.tree;
  state.nodeMap = {};
  indexTree(state.tree, state.nodeMap);
  state.browseId = state.tree.id || 'root';
  state.selected = (state.tree.children && state.tree.children[0]) || state.tree;
  state.checked = {};
  state.skillName = sanitizeFilename(fileName || 'converted').replace(/\.[^.]+$/, '') || 'converted';
  if ($('skillName')) $('skillName').value = state.skillName;
  applyDetected(collectNodeParticles(state.selected));
  state.output = JSON.stringify(serializeSkills());
  $('output').textContent = state.output;
  renderTree();
  renderHud();
  renderEditor();
  enterStudio('editor');
  toast('Loaded ' + state.skills.length + ' skill(s)');
}

function queueBytes() {
  return (state.pendingFiles || []).reduce((n, f) => n + (f && f.size ? f.size : 0), 0);
}
function fmtBytes(n) {
  const b = Number(n) || 0;
  if (b < 1024) return b + ' B';
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB';
  return (b / (1024 * 1024)).toFixed(1) + ' MB';
}
function looksLikeKnownFile(file) {
  const name = file && file.name;
  return looksLikePlaceName(name) || looksLikeImportName(name);
}
function renderFileQueue() {
  const files = state.pendingFiles || [];
  const total = queueBytes();
  const over = total > MAX_QUEUE_BYTES;
  const meta = files.length + ' file' + (files.length === 1 ? '' : 's') + ' · ' + fmtBytes(total) + ' / 500 MB';
  ['gateQueueMeta', 'appQueueMeta'].forEach((id) => {
    const el = $(id);
    if (!el) return;
    el.textContent = meta;
    el.classList.toggle('over', over);
  });
  const canParse = files.length > 0 && !over && !state.parsing;
  ['gateParseBtn', 'queueParseBtn'].forEach((id) => {
    const el = $(id);
    if (el) el.disabled = !canParse;
  });
  function paint(box) {
    if (!box) return;
    box.innerHTML = '';
    box.classList.toggle('on', files.length > 0);
    files.forEach((file, i) => {
      const row = document.createElement('div');
      row.className = 'q-row';
      row.innerHTML = '<span class="name"></span><span class="size"></span><button class="q-remove" type="button" aria-label="Remove">×</button>';
      row.querySelector('.name').textContent = file.name;
      row.querySelector('.size').textContent = fmtBytes(file.size);
      row.querySelector('.q-remove').onclick = (e) => {
        e.preventDefault();
        state.pendingFiles.splice(i, 1);
        renderFileQueue();
      };
      box.appendChild(row);
    });
  }
  paint($('gateQueue'));
  paint($('appQueue'));
  const modal = $('queueModal');
  if (modal && !document.body.classList.contains('gated')) {
    if (files.length) modal.classList.add('on');
    else modal.classList.remove('on');
  }
}
function queueFiles(list) {
  const incoming = Array.from(list || []).filter(Boolean);
  if (!incoming.length) return;
  const have = state.pendingFiles || [];
  let total = queueBytes();
  let added = 0;
  let skippedType = 0;
  let skippedCap = 0;
  incoming.forEach((file) => {
    if (!looksLikeKnownFile(file)) { skippedType++; return; }
    const dup = have.some((f) => f.name === file.name && f.size === file.size && f.lastModified === file.lastModified);
    if (dup) return;
    if (total + file.size > MAX_QUEUE_BYTES) { skippedCap++; return; }
    have.push(file);
    total += file.size;
    added++;
  });
  state.pendingFiles = have;
  renderFileQueue();
  if (skippedCap) toast('Skipped ' + skippedCap + ' file(s) — 500 MB cap');
  else if (skippedType) toast('Skipped ' + skippedType + ' unsupported file(s)');
  else if (added) toast('Queued ' + added + ' file' + (added === 1 ? '' : 's') + ' · ' + fmtBytes(total) + ' / 500 MB');
  else toast('No new files added');
}
function clearFileQueue() {
  state.pendingFiles = [];
  renderFileQueue();
  toast('Queue cleared');
}
function restampTree(node, counter, parentId) {
  if (!node) return;
  if (node.id !== 'root') node.id = 'n' + (counter.n++);
  node.parentId = parentId || null;
  (node.children || []).forEach((c) => restampTree(c, counter, node.id));
}
function folderFromParsed(parsed, fileName) {
  const tree = (parsed && parsed.tree) || {
    name: fileName,
    className: 'Folder',
    type: 'ParticleFolder',
    children: (parsed && parsed.hosts) || [],
    particles: (parsed && parsed.particles) || [],
    meshes: [], cameras: [], sounds: [], beams: []
  };
  tree.name = fileName || tree.name || 'File';
  tree.className = tree.className || 'Folder';
  tree.type = 'ParticleFolder';
  tree.count = hostCount(tree);
  return tree;
}
function adoptFolders(folders, opts) {
  opts = opts || {};
  const reset = !!opts.reset || !state.tree;
  const incoming = (folders || []).filter(Boolean);
  if (!incoming.length) return;
  if (reset) {
    const root = {
      id: 'root',
      name: 'Workspace',
      className: 'Folder',
      type: 'ParticleFolder',
      children: incoming,
      particles: [], meshes: [], cameras: [], sounds: [], beams: [],
      count: incoming.reduce((n, f) => n + hostCount(f), 0)
    };
    restampTree(root, { n: 1 }, null);
    root.id = 'root';
    incoming.forEach((f) => { f.parentId = 'root'; });
    state.tree = root;
  } else {
    incoming.forEach((f) => { state.tree.children.push(f); });
    restampTree(state.tree, { n: 1 }, null);
    state.tree.id = 'root';
    state.tree.count = hostCount(state.tree);
  }
  state.nodeMap = {};
  indexTree(state.tree, state.nodeMap);
  state.browseId = 'root';
  state.selected = incoming[0] || state.tree;
  state.checked = {};
  const label = incoming.length === 1 ? incoming[0].name : (incoming.length + ' files');
  state.fileName = reset ? label : (state.fileName ? state.fileName + ' + ' + label : label);
  state.skillName = sanitizeFilename(incoming[0] && incoming[0].name || state.skillName || 'converted').replace(/\.[^.]+$/, '') || 'converted';
  if ($('skillName')) $('skillName').value = state.skillName;
  applyDetected(collectNodeParticles(state.selected));
  renderTree();
  renderHud();
}
async function parseOneFile(file) {
  const buf = new Uint8Array(await file.arrayBuffer());
  const textHead = new TextDecoder().decode(buf.slice(0, 24));
  if (!looksLikePlaceName(file.name) && (looksLikeImportName(file.name) || textHead.indexOf('KLUv') === 0 || textHead[0] === '[' || textHead[0] === '{')) {
    const text = new TextDecoder().decode(buf);
    const skills = decodeImportText(text);
    const list = Array.isArray(skills) ? skills : [skills];
    const parsed = treeFromSkills(list, file.name);
    return { kind: 'skills', fileName: file.name, skills: list, parsed: parsed };
  }
  if (!window.JJSParser) throw new Error('Parser failed to load.');
  const parsed = window.JJSParser.parsePlace(window.JJSParser.Buffer.from(buf), file.name, {
    cameras: !!state.parseCameras,
    meshes: !!state.parseMeshes
  });
  return { kind: 'place', fileName: file.name, parsed: parsed };
}
async function loadFile(file) {
  queueFiles([file]);
}
async function parseQueuedFiles() {
  const files = (state.pendingFiles || []).slice();
  if (!files.length) { toast('Add files first'); return; }
  if (queueBytes() > MAX_QUEUE_BYTES) { toast('Queue is over 500 MB'); return; }
  if (state.parsing) return;
  state.parsing = true;
  renderFileQueue();
  const hadTree = !!state.tree && !document.body.classList.contains('gated');
  const results = [];
  try {
    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      toast('Parsing ' + (i + 1) + '/' + files.length + ' · ' + file.name + ' (' + fmtBytes(file.size) + ')…');
      if ($('hudTitle')) $('hudTitle').textContent = file.name;
      await new Promise((r) => setTimeout(r, 20));
      try {
        results.push(await parseOneFile(file));
      } catch (err) {
        console.error(err);
        toast('Parse failed: ' + file.name + ' — ' + (err && err.message ? err.message : err));
      }
    }
    if (!results.length) throw new Error('Nothing parsed.');
    const allSkills = results.every((r) => r.kind === 'skills');
    if (allSkills && !hadTree) {
      const skills = results.reduce((a, r) => a.concat(r.skills || []), []);
      state.pendingFiles = [];
      state.parsing = false;
      renderFileQueue();
      loadSkills(skills, results.length === 1 ? results[0].fileName : 'Import');
      return;
    }
    const folders = results.map((r) => folderFromParsed(r.parsed, r.fileName));
    adoptFolders(folders, { reset: !hadTree });
    enterStudio('convert');
    toast('Loaded ' + results.length + ' file' + (results.length === 1 ? '' : 's') + ' · ' + hostCount(state.tree) + ' items');
    state.pendingFiles = [];
  } catch (e) {
    console.error(e);
    toast('Parse failed: ' + (e && e.message ? e.message : e));
  } finally {
    state.parsing = false;
    renderFileQueue();
  }
}

function nodeKind(n) {
  if (!n) return 'NODE';
  if (n.K_NAME === 'VISUAL') return n.EFFECT === 'Mesh' ? 'MESH' : 'CAMERA';
  return n.K_NAME || 'NODE';
}
function nodeLabel(n) {
  const k = nodeKind(n);
  if (k === 'PARTICLE') return '[' + (n['EMIT COUNT'] != null ? n['EMIT COUNT'] : '') + '] PARTICLE EMITTER';
  if (k === 'MESH') return '[Mesh] VISUAL';
  if (k === 'CAMERA') return '[Camera] VISUAL';
  if (k === 'SFX') return '[' + (n.ID || '') + '] SOUND';
  if (k === 'WAIT') return 'WAIT';
  if (k === 'CONNECT') return n.SIGNAL ? String(n.SIGNAL).slice(0, 42) : 'CONNECT';
  if (k === 'TAG') return 'TAG';
  return k;
}
function iconFor(n) {
  const k = nodeKind(n);
  const map = { PARTICLE: ICONS.PARTICLE, MESH: ICONS.VISUAL_MESH, CAMERA: ICONS.VISUAL_CAMERA, SFX: ICONS.SFX, WAIT: ICONS.WAIT, CONNECT: ICONS.CONNECT, TAG: ICONS.TAG };
  return map[k] || '';
}

function countsOfLines(lines) {
  const c = { particles: 0, meshes: 0, cameras: 0, sounds: 0, waits: 0, other: 0 };
  (lines || []).forEach((n) => {
    const k = nodeKind(n);
    if (k === 'PARTICLE') c.particles++;
    else if (k === 'MESH') c.meshes++;
    else if (k === 'CAMERA') c.cameras++;
    else if (k === 'SFX') c.sounds++;
    else if (k === 'WAIT') c.waits++;
    else c.other++;
  });
  return c;
}

function setMode(mode) {
  state.mode = mode;
  $('modeConvert').classList.toggle('on', mode === 'convert');
  $('modeEditor').classList.toggle('on', mode === 'editor');
  $('convertView').classList.toggle('hidden', mode !== 'convert');
  $('editorView').classList.toggle('hidden', mode !== 'editor');
  $('modeLabel').textContent = mode === 'editor' ? 'Editor' : 'Convert';
  if ($('exportBtn')) $('exportBtn').textContent = mode === 'editor' ? 'Save' : 'Export';
  if (mode === 'editor') renderEditor();
}

function uniqueBranchName(base) {
  if (base && typeof base === 'object' && !Array.isArray(base)) base = hostLabel(base, 'Branch');
  base = String(base || 'Branch');
  if (base === '[object Object]') base = 'Branch';
  const data = currentData();
  let name = base;
  let i = 2;
  while (name === 'Default' || data.Branch[name]) {
    name = base.replace(/\s+\d+$/, '') + ' ' + i;
    i++;
  }
  return name;
}

function swapWithDefault(name) {
  if (name === 'Default') return;
  const data = currentData();
  const br = data.Branch[name];
  if (!br) return;
  const tmp = data.Line;
  data.Line = br.Line || [];
  br.Line = tmp;
  const cached = state.nameCache[name];
  if (cached && !data.Branch[cached] && cached !== 'Default') {
    data.Branch[cached] = br;
    delete data.Branch[name];
    delete state.nameCache[name];
    state.branch = cached;
  } else {
    const nn = uniqueBranchName('Default 2');
    state.nameCache[nn] = cached || name;
    data.Branch[nn] = br;
    if (nn !== name) delete data.Branch[name];
    state.branch = nn;
  }
  state.selectedNode = -1;
  toast('Swapped with Default');
  renderEditor();
}

function swapNamed(a, b) {
  const data = currentData();
  if (!data.Branch[a] || !data.Branch[b]) return;
  const tmp = data.Branch[a].Line;
  data.Branch[a].Line = data.Branch[b].Line;
  data.Branch[b].Line = tmp;
  toast('Swapped branches');
  renderEditor();
}

const MESH_FIELDS = [
  ['TIME', 'number'], ['POSITION', 'text'], ['ALT POSITION', 'text'],
  ['ROTATION', 'text'], ['ALT ROTATION', 'text'],
  ['SIZE', 'text'], ['ALT SIZE', 'text'], ['OPACITY', 'number'], ['ALT OPACITY', 'number'],
  ['COLOR', 'text'], ['ALT COLOR', 'text'],
  ['AMOUNT', 'text', 'Mesh ID'], ['TEXTURE', 'text'],
  ['PROJECTILE TAG', 'text'], ['VISUAL TAG', 'text'],
  ['RUN ON SERVER', 'bool'], ['CLIENT SIDED', 'bool'], ['CANCEL ON INTERRUPT', 'bool'],
  ['RELATIVE FROM BRANCH', 'bool'], ['BODY PART', 'limb', 'LIMB'], ['LAST HIT', 'number']
];
const CAMERA_FIELDS = [
  ['TIME', 'number'], ['POSITION', 'text'], ['ALT POSITION', 'text'],
  ['ROTATION', 'text'], ['ALT ROTATION', 'text'],
  ['COLOR', 'text'], ['ALT COLOR', 'text'],
  ['EASING STYLE', 'easing'], ['EASING DIRECTION', 'easedir'],
  ['PROJECTILE TAG', 'text'], ['VISUAL TAG', 'text'],
  ['RUN ON SERVER', 'bool'], ['CLIENT SIDED', 'bool'], ['CANCEL ON INTERRUPT', 'bool'],
  ['RELATIVE FROM BRANCH', 'bool'], ['BODY PART', 'limb', 'LIMB'], ['LAST HIT', 'number']
];
const SFX_FIELDS = [
  ['ID', 'text'], ['SPEED', 'number'], ['VOLUME', 'number'], ['START', 'number'], ['END', 'number'],
  ['FADE IN', 'number'], ['FADE OUT', 'number'], ['CANCEL', 'bool'], ['GLOBAL', 'bool'],
  ['CLIENT SIDED', 'bool'], ['LAST HIT', 'number'], ['PROJECTILE TAG', 'text']
];
const PARTICLE_FIELDS = [
  ['NAME', 'text'], ['EMIT COUNT', 'number'], ['TEXTURE', 'text'], ['DURATION', 'number'],
  ['RATE', 'number'], ['BRIGHTNESS', 'number'], ['COLOR', 'text'], ['SIZE', 'text'],
  ['TRANSPARENCY', 'text'], ['SQUASH', 'text'], ['LIFETIME', 'text'], ['SPEED', 'text'],
  ['ROTATION', 'text'], ['ROT SPEED', 'text'], ['DRAG', 'number'], ['ACCELERATION', 'text'],
  ['SPREAD ANGLE', 'text'], ['POSITION', 'text'], ['ZOFFSET', 'number'],
  ['LIGHT EMISSION', 'number'], ['LIGHT INFLUENCE', 'number'],
  ['FLIPBOOK SIZE', 'text'], ['FLIPBOOK FRAMERATE', 'text'], ['FLIPBOOK MODE', 'text'],
  ['ORIENTATION TYPE', 'text'], ['EMISSION DIRECTION', 'text'], ['LOCK TO PART', 'bool'],
  ['RUN ON SERVER', 'bool'], ['CLIENT SIDED', 'bool'], ['CANCEL ON INTERRUPT', 'bool'],
  ['BODY PART', 'limb', 'LIMB'], ['LAST HIT', 'number'], ['PROJECTILE TAG', 'text']
];
const WAIT_FIELDS = [['TIME', 'number']];
const CONNECT_FIELDS = [['SIGNAL', 'text'], ['TIME', 'number']];
const TAG_FIELDS = [['TAG', 'text'], ['SET', 'bool'], ['ADD/REMOVE', 'bool'], ['CHECK', 'bool'], ['TIME', 'number']];

function fieldsFor(n) {
  const k = nodeKind(n);
  if (k === 'MESH') return MESH_FIELDS;
  if (k === 'CAMERA') return CAMERA_FIELDS;
  if (k === 'SFX') return SFX_FIELDS;
  if (k === 'PARTICLE') return PARTICLE_FIELDS;
  if (k === 'WAIT') return WAIT_FIELDS;
  if (k === 'CONNECT') return CONNECT_FIELDS;
  if (k === 'TAG') return TAG_FIELDS;
  return Object.keys(n || {}).filter((x) => x !== 'K_NAME').map((x) => [x, 'text']);
}

const COND_DEFS = [
  ['IN AIR', false], ['IS JUMPING', false], ['HAS TARGET', false],
  ['IS AWAKENED', false], ['HAS AWK BAR', 5], ['DURABILITY', 1], ['HAS HEALTH', 10]
];
const PROP_DEFS = [
  ['DAMAGE MULTIPLIER', 1], ['KNOCKBACK MULTIPLIER', 1],
  ['INVINCIBLE', false], ['REPLACE SKILL IF OCCUPIED', false]
];

function currentProp() {
  const data = currentData();
  if (state.branch === 'Default') return data.Prop;
  const br = data.Branch[state.branch];
  if (!br) return data.Prop;
  if (!br.Prop || typeof br.Prop !== 'object') br.Prop = {};
  return br.Prop;
}
function currentReq() {
  const data = currentData();
  if (state.branch === 'Default') {
    if (Array.isArray(data.Req)) {
      const o = {};
      data.Req.forEach((x) => { if (x && x.NAME) o[x.NAME] = x.VALUE; });
      data.Req = o;
    }
    if (!data.Req || typeof data.Req !== 'object') data.Req = {};
    return data.Req;
  }
  const br = data.Branch[state.branch];
  if (!br) return {};
  if (Array.isArray(br.Req)) {
    const o = {};
    br.Req.forEach((x) => { if (x && x.NAME) o[x.NAME] = x.VALUE; });
    br.Req = o;
  }
  if (!br.Req || typeof br.Req !== 'object') br.Req = {};
  return br.Req;
}

function jjsRow(label, control) {
  const wrap = document.createElement('div');
  wrap.className = 'jjs-row';
  const lab = document.createElement('div');
  lab.className = 'jjs-lab';
  lab.textContent = label;
  wrap.appendChild(lab);
  wrap.appendChild(control);
  return wrap;
}

function fieldControl(node, spec) {
  const key = spec[0], type = spec[1], label = spec[2] || key;
  const val = node[key];
  let control;
  if (type === 'bool') {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'toggle' + (val ? ' on' : '');
    btn.innerHTML = '<i></i>';
    btn.onclick = () => { node[key] = !node[key]; btn.classList.toggle('on', !!node[key]); };
    control = btn;
  } else if (type === 'limb' || type === 'easing' || type === 'easedir') {
    const opts = type === 'limb'
      ? ['HumanoidRootPart', 'Head', 'Torso', 'Right Arm', 'Left Arm', 'Right Leg', 'Left Leg']
      : type === 'easing' ? ['Linear', 'Sine', 'Quad', 'Cubic', 'Exponential'] : ['In', 'Out', 'InOut'];
    const fallback = type === 'limb' ? 'HumanoidRootPart' : type === 'easing' ? 'Linear' : 'In';
    const sel = document.createElement('select');
    sel.className = 'jjs-pill';
    opts.forEach((opt) => {
      const o = document.createElement('option');
      o.value = opt; o.textContent = opt; if (String(val || fallback) === opt) o.selected = true;
      sel.appendChild(o);
    });
    sel.onchange = () => { node[key] = sel.value; };
    control = sel;
  } else {
    const inp = document.createElement('input');
    inp.className = 'jjs-pill';
    inp.type = type === 'number' ? 'number' : 'text';
    inp.step = 'any';
    inp.value = val == null ? '' : val;
    inp.oninput = () => {
      node[key] = type === 'number' ? (inp.value === '' ? 0 : Number(inp.value)) : inp.value;
    };
    control = inp;
  }
  return jjsRow(label, control);
}

function shakeEl(el) {
  if (!el) return;
  el.classList.remove('shake');
  void el.offsetWidth;
  el.classList.add('shake');
}

function enableHoldDrag(container, opts) {
  if (!container) return;
  container._holdOpts = opts;
  if (container._holdBound) return;
  container._holdBound = true;
  const holdMs = opts.holdMs || 1000;
  let timer = null, drag = null, startX = 0, startY = 0, skipClick = false;

  function items() {
    return Array.from(container.querySelectorAll(container._holdOpts.item));
  }
  function itemAtPoint(x, y, ignore) {
    const list = items();
    for (let i = 0; i < list.length; i++) {
      if (list[i] === ignore) continue;
      const b = list[i].getBoundingClientRect();
      if (x >= b.left && x <= b.right && y >= b.top && y <= b.bottom) return list[i];
    }
    return null;
  }
  function stopTimer() {
    if (timer) { clearTimeout(timer); timer = null; }
  }
  function killGhost() {
    if (drag && drag.ghost && drag.ghost.parentNode) drag.ghost.parentNode.removeChild(drag.ghost);
    container.querySelectorAll('.drag-source,.shake').forEach((n) => {
      n.classList.remove('drag-source', 'shake');
    });
    drag = null;
  }
  function beginDrag(row, e) {
    const rect = row.getBoundingClientRect();
    const ghost = row.cloneNode(true);
    ghost.classList.add('drag-ghost');
    ghost.style.left = rect.left + 'px';
    ghost.style.top = rect.top + 'px';
    ghost.style.width = rect.width + 'px';
    document.body.appendChild(ghost);
    row.classList.add('drag-source');
    drag = {
      row: row,
      ghost: ghost,
      ox: e.clientX - rect.left,
      oy: e.clientY - rect.top,
      over: null,
      axis: container._holdOpts.axis || 'y'
    };
    try { container.setPointerCapture(e.pointerId); } catch (err) {}
  }
  function moveGhost(e) {
    if (!drag) return;
    drag.ghost.style.left = (e.clientX - drag.ox) + 'px';
    drag.ghost.style.top = (e.clientY - drag.oy) + 'px';
    const over = itemAtPoint(e.clientX, e.clientY, drag.row);
    if (over && over !== drag.over) {
      drag.over = over;
      shakeEl(over);
      const list = items();
      const from = list.indexOf(drag.row);
      const to = list.indexOf(over);
      if (from < 0 || to < 0 || from === to) return;
      if (from < to) container.insertBefore(drag.row, over.nextSibling);
      else container.insertBefore(drag.row, over);
    }
  }
  function finish() {
    if (!drag) { stopTimer(); return false; }
    const src = drag.row;
    const over = drag.over;
    killGhost();
    skipClick = true;
    setTimeout(() => { skipClick = false; }, 80);
    if (over && src && over !== src && container._holdOpts.onDrop) {
      container._holdOpts.onDrop(src, over);
    } else if (container._holdOpts.onReorder) {
      container._holdOpts.onReorder(items());
    }
    return true;
  }

  container.addEventListener('pointerdown', (e) => {
    const row = e.target.closest(container._holdOpts.item);
    if (!row || e.target.closest('input,select,textarea,button.toggle')) return;
    startX = e.clientX; startY = e.clientY;
    stopTimer();
    timer = setTimeout(() => beginDrag(row, e), holdMs);
  });
  container.addEventListener('pointermove', (e) => {
    if (timer && (Math.abs(e.clientX - startX) > 10 || Math.abs(e.clientY - startY) > 10)) stopTimer();
    if (!drag) return;
    e.preventDefault();
    moveGhost(e);
  }, { passive: false });
  container.addEventListener('pointerup', (e) => {
    const did = !!drag;
    finish();
    if (did) e.preventDefault();
  });
  container.addEventListener('pointercancel', () => { stopTimer(); killGhost(); });
  container.addEventListener('click', (e) => {
    if (!skipClick) return;
    e.stopPropagation();
    e.preventDefault();
  }, true);
}

function renderLeft() {
  const pane = $('leftPane');
  if (!pane) return;
  pane.innerHTML = '';
  const skill = currentSkill();
  if (!skill) {
    pane.innerHTML = '<p class="hint">Paste an import code or convert a place first.</p>';
    if ($('leftTitle')) $('leftTitle').textContent = 'Skill';
    return;
  }
  const lines = currentLines();
  if (state.selectedNode >= 0 && lines[state.selectedNode]) {
    const node = lines[state.selectedNode];
    if ($('leftTitle')) $('leftTitle').textContent = nodeLabel(node);
    fieldsFor(node).forEach((spec) => pane.appendChild(fieldControl(node, spec)));
    return;
  }
  if ($('leftTitle')) $('leftTitle').textContent = skill.NAME || 'Skill';
  const stats = countsOfLines(lines);
  const grid = document.createElement('div');
  grid.className = 'stat-grid';
  [[stats.particles, 'Particles'], [stats.meshes, 'Meshes'], [stats.cameras, 'Cameras'], [stats.sounds, 'Sounds']].forEach((pair) => {
    const d = document.createElement('div');
    d.className = 'stat';
    d.innerHTML = '<b>' + pair[0] + '</b><span>' + pair[1] + '</span>';
    grid.appendChild(d);
  });
  pane.appendChild(grid);
  [['NAME', 'text', 'NAME'], ['COOLDOWN', 'number', 'COOLDOWN'], ['KEY', 'number', 'KEY'], ['ADD', 'bool', 'ADD'], ['TOOL TIP', 'text', 'TOOL TIP']].forEach((spec) => {
    pane.appendChild(fieldControl(skill, spec));
  });
  const prop = currentProp();
  PROP_DEFS.forEach((pair) => {
    if (prop[pair[0]] == null) prop[pair[0]] = pair[1];
    pane.appendChild(fieldControl(prop, [pair[0], typeof pair[1] === 'boolean' ? 'bool' : 'number']));
  });
  const reqLab = document.createElement('label');
  reqLab.textContent = 'Conditions';
  pane.appendChild(reqLab);
  const req = currentReq();
  COND_DEFS.forEach((pair) => {
    if (req[pair[0]] == null) req[pair[0]] = pair[1];
    pane.appendChild(fieldControl(req, [pair[0], typeof pair[1] === 'boolean' ? 'bool' : 'number']));
  });
  const addTitle = document.createElement('label');
  addTitle.textContent = 'Add';
  pane.appendChild(addTitle);
  const adds = document.createElement('div');
  adds.className = 'add-grid';
  [['Particle', defaultParticle], ['Sound', defaultSfx], ['Mesh', defaultMesh], ['Camera', defaultCamera], ['Wait', defaultWait], ['Connect', defaultConnect], ['Tag', defaultTag]].forEach((pair) => {
    const b = document.createElement('button');
    b.className = 'btn add-btn';
    b.textContent = pair[0];
    b.onclick = () => {
      lines.push(pair[1]());
      state.selectedNode = lines.length - 1;
      renderEditor();
    };
    adds.appendChild(b);
  });
  pane.appendChild(adds);
}

function renderEditor() {
  const strip = $('skillStrip');
  if (!strip) return;
  strip.innerHTML = '';
  state.skills.forEach((s, i) => {
    const b = document.createElement('button');
    b.className = 'skill-chip' + (i === state.skillIndex ? ' on' : '');
    b.textContent = (s.NAME || ('Skill ' + (i + 1)));
    b.onclick = () => { state.skillIndex = i; state.branch = 'Default'; state.selectedNode = -1; renderEditor(); };
    strip.appendChild(b);
  });
  const addS = document.createElement('button');
  addS.className = 'skill-chip';
  addS.textContent = '+ Skill';
  addS.onclick = () => {
    state.skills.push(skillFromLines([], 'New Skill', state.skills.length + 1));
    state.skillIndex = state.skills.length - 1;
    state.branch = 'Default';
    state.selectedNode = -1;
    renderEditor();
  };
  strip.appendChild(addS);

  const tabs = $('branchTabs');
  tabs.innerHTML = '';
  const data = currentData();
  const names = ['Default'].concat(Object.keys(data.Branch || {}));
  names.forEach((name) => {
    const b = document.createElement('button');
    b.className = 'tab' + (state.branch === name ? ' on' : '');
    b.textContent = name;
    b.setAttribute('data-branch', name);
    b.onclick = () => {
      state.branch = name;
      state.selectedNode = -1;
      renderEditor();
    };
    tabs.appendChild(b);
  });
  const addB = document.createElement('button');
  addB.className = 'tab add';
  addB.textContent = '+ Branch';
  addB.onclick = () => {
    const name = uniqueBranchName('Branch');
    data.Branch[name] = { Line: [], Prop: {}, Req: {} };
    state.branch = name;
    state.selectedNode = -1;
    renderEditor();
  };
  tabs.appendChild(addB);
  enableHoldDrag(tabs, {
    item: '.tab[data-branch]',
    holdMs: 1000,
    axis: 'x',
    onDrop: function (srcEl, destEl) {
      const src = srcEl.getAttribute('data-branch');
      const dest = destEl.getAttribute('data-branch');
      if (!src || !dest || src === dest) return;
      if (src === 'Default' || dest === 'Default') swapWithDefault(src === 'Default' ? dest : src);
      else swapNamed(src, dest);
    }
  });

  const lines = currentLines();
  if ($('timelineMeta')) $('timelineMeta').textContent = lines.length + ' nodes · hold 1s to drag';
  const tl = $('timeline');
  tl.innerHTML = '';
  lines.forEach((n, i) => {
    const row = document.createElement('div');
    const kind = nodeKind(n);
    row.className = 'node node-' + (n.K_NAME || 'NODE') + (state.selectedNode === i ? ' on' : '');
    row.setAttribute('data-index', String(i));
    const ic = iconFor(n);
    row.innerHTML = (ic ? '<img src="' + escapeHtml(ic) + '" width="16" height="16">' : '') +
      '<span class="kind">' + escapeHtml(nodeLabel(n)) + '</span>' +
      '<span class="sub">' + escapeHtml(n.EFFECT || n.K_NAME || '') + '</span>';
    row.onclick = () => {
      state.selectedNode = state.selectedNode === i ? -1 : i;
      renderEditor();
    };
    tl.appendChild(row);
  });
  if (!lines.length) {
    const empty = document.createElement('p');
    empty.className = 'hint';
    empty.textContent = 'No nodes on this branch yet.';
    tl.appendChild(empty);
  }
  enableHoldDrag(tl, {
    item: '.node[data-index]',
    holdMs: 1000,
    axis: 'y',
    onDrop: function (srcEl, destEl) {
      const a = Number(srcEl.getAttribute('data-index'));
      const b = Number(destEl.getAttribute('data-index'));
      if (!Number.isFinite(a) || !Number.isFinite(b) || a === b) return;
      const item = lines.splice(a, 1)[0];
      lines.splice(b, 0, item);
      state.selectedNode = b;
      renderEditor();
    }
  });

  const tools = $('editorTools');
  if (tools) {
    tools.innerHTML = '';
    function tool(txt, cls, fn) {
      const b = document.createElement('button');
      b.className = 'tool' + (cls ? ' ' + cls : '');
      b.textContent = txt;
      b.onclick = fn;
      tools.appendChild(b);
    }
    tool('−', 'danger', () => {
      if (state.selectedNode < 0) return;
      lines.splice(state.selectedNode, 1);
      state.selectedNode = -1;
      renderEditor();
    });
    tool('⧉', '', () => {
      if (state.selectedNode < 0) return;
      lines.splice(state.selectedNode + 1, 0, JSON.parse(JSON.stringify(lines[state.selectedNode])));
      state.selectedNode += 1;
      renderEditor();
    });
    tool('↓', '', () => {
      if (state.selectedNode < 0 || state.selectedNode >= lines.length - 1) return;
      const i = state.selectedNode;
      const t = lines[i]; lines[i] = lines[i + 1]; lines[i + 1] = t;
      state.selectedNode = i + 1;
      renderEditor();
    });
    tool('↑', '', () => {
      if (state.selectedNode <= 0) return;
      const i = state.selectedNode;
      const t = lines[i]; lines[i] = lines[i - 1]; lines[i - 1] = t;
      state.selectedNode = i - 1;
      renderEditor();
    });
  }
  renderLeft();
}

function exportEditor() {
  if (!state.skills.length) { exportCurrent(); return; }
  const json = JSON.stringify(serializeSkills());
  state.output = json;
  $('output').textContent = json;
  compressMaybe(json);
  toast('Exported ' + state.skills.length + ' skill(s) from live JSON.');
}

function armFilePick(input) {
  if (!input) return;
  const startPick = () => {
    try { sessionStorage.setItem('jjs-picking', '1'); } catch (e) {}
    if (window.JJSSpace && window.JJSSpace.pause) window.JJSSpace.pause();
  };
  const endPick = () => {
    try { sessionStorage.removeItem('jjs-picking'); } catch (e) {}
    if (window.JJSSpace && window.JJSSpace.resume) window.JJSSpace.resume();
  };
  input.addEventListener('click', startPick);
  input.addEventListener('focus', startPick);
  input.addEventListener('cancel', endPick);
  input.addEventListener('change', (e) => {
    endPick();
    const list = e.target.files;
    if (list && list.length) queueFiles(list);
    try { e.target.value = ''; } catch (err) {}
  });
}

function bind() {
  armFilePick($('fileInput'));
  armFilePick($('gateFile'));
  if ($('gatePasteBtn')) {
    $('gatePasteBtn').addEventListener('click', () => {
      try {
        const skills = decodeImportText($('gatePaste').value);
        loadSkills(Array.isArray(skills) ? skills : [skills], 'pasted');
      } catch (err) {
        toast('Paste failed: ' + (err && err.message ? err.message : err));
      }
    });
  }
  if ($('gateParseBtn')) $('gateParseBtn').addEventListener('click', () => parseQueuedFiles());
  if ($('queueParseBtn')) $('queueParseBtn').addEventListener('click', () => parseQueuedFiles());
  if ($('queueClear')) $('queueClear').addEventListener('click', () => clearFileQueue());
  document.addEventListener('dragover', (e) => e.preventDefault());
  document.addEventListener('drop', (e) => {
    e.preventDefault();
    const list = e.dataTransfer && e.dataTransfer.files;
    if (list && list.length) queueFiles(list);
  });
  $('search').addEventListener('input', (e) => { state.search = e.target.value; renderTree(); });
  $('skillName').addEventListener('input', (e) => { state.skillName = e.target.value; });
  $('skillKey').addEventListener('input', (e) => { state.skillKey = Number(e.target.value) || 1; });
  $('duration').addEventListener('input', (e) => { state.duration = Number(e.target.value) || 0; });
  $('position').addEventListener('input', (e) => { state.position = e.target.value || '0, 0, 0'; });
  $('packMode').addEventListener('change', (e) => {
    state.packMode = e.target.checked;
    if ($('branchModeWrap')) $('branchModeWrap').classList.toggle('hidden', !state.packMode);
    if (!state.packMode) {
      state.branchMode = false;
      if ($('branchMode')) $('branchMode').checked = false;
    }
  });
  if ($('branchMode')) {
    $('branchMode').addEventListener('change', (e) => { state.branchMode = e.target.checked; });
  }
  if ($('sectionMark')) {
    $('sectionMark').checked = false;
    $('sectionMark').disabled = true;
    state.sectionMark = false;
  }
  if ($('rbxKey')) {
    try {
      const saved = localStorage.getItem('jjsRobloxKey');
      const cfg = (window.JJS_CONFIG && window.JJS_CONFIG.ROBLOX_API_KEY) || '';
      if (saved) $('rbxKey').value = saved;
      else if (cfg) $('rbxKey').placeholder = 'Using key from config.js';
    } catch (e) {}
    $('rbxKey').addEventListener('change', (e) => {
      try { localStorage.setItem('jjsRobloxKey', e.target.value || ''); } catch (err) {}
    });
  }
  function bindParseToggle(ids, key) {
    ids.forEach((id) => {
      const el = $(id);
      if (!el) return;
      el.addEventListener('change', () => {
        state[key] = !!el.checked;
        ids.forEach((other) => { if ($(other)) $(other).checked = el.checked; });
      });
    });
  }
  bindParseToggle(['gateParseCam', 'parseCam'], 'parseCameras');
  bindParseToggle(['gateParseMesh', 'parseMesh'], 'parseMeshes');
  if ($('runOnServer')) {
    $('runOnServer').addEventListener('change', (e) => { state.runOnServer = e.target.checked; });
  }
  $('recolor').addEventListener('change', (e) => {
    state.recolor = e.target.checked;
    $('recolorBox').classList.toggle('dim', !state.recolor);
    $('recolorBox').classList.toggle('off', state.recolor);
  });
  ['main', 'accent', 'other'].forEach((k) => {
    $(k + 'Color').addEventListener('input', (e) => { state.colors[k] = e.target.value; $(k + 'Hex').value = e.target.value; });
    $(k + 'Hex').addEventListener('input', (e) => { state.colors[k] = e.target.value; });
    $(k + 'Found').addEventListener('click', () => {
      if (!state.detected) return;
      const hex = rgbToHex(state.detected[k]);
      state.colors[k] = hex;
      $(k + 'Color').value = hex;
      $(k + 'Hex').value = hex;
    });
  });
  $('exportBtn').addEventListener('click', () => {
    if (state.mode === 'editor') exportEditor();
    else exportCurrent().catch((err) => toast('Export failed: ' + (err && err.message ? err.message : err)));
  });
  $('copyJson').addEventListener('click', () => copyText(state.output || $('output').textContent));
  $('copyCompact').addEventListener('click', () => copyText(state.compact || $('compactOut').textContent));
  $('dlJson').addEventListener('click', () => {
    if (state.packs.length > 1 && state.mode === 'convert') state.packs.forEach((p) => download(p.name + '.particle.json', p.json));
    else download((state.skillName || 'skill') + '.json', state.output || $('output').textContent);
  });
  $('dlCompact').addEventListener('click', () => download((state.skillName || 'skill') + '.txt', state.compact || $('compactOut').textContent));
  $('modeConvert').onclick = () => setMode('convert');
  $('modeEditor').onclick = () => setMode('editor');
  $('importTextBtn').onclick = () => $('pasteModal').classList.add('on');
  $('pasteCancel').onclick = () => $('pasteModal').classList.remove('on');
  $('pasteOk').onclick = () => {
    try {
      const skills = decodeImportText($('pasteBox').value);
      loadSkills(Array.isArray(skills) ? skills : [skills], 'pasted');
      $('pasteModal').classList.remove('on');
      setMode('editor');
    } catch (e) {
      toast('Paste failed: ' + (e && e.message ? e.message : e));
    }
  };
}

bind();
renderFileQueue();
renderTree();
renderHud();
if (sessionStorage.getItem('jjs-picking')) {
  try { sessionStorage.removeItem('jjs-picking'); } catch (e) {}
  toast('Page was backgrounded in the file picker. Choose the file again.');
} else {
  toast('Ready.');
}
window.addEventListener('pageshow', () => {
  if (window.JJSSpace && window.JJSSpace.resume && !sessionStorage.getItem('jjs-picking')) {
    window.JJSSpace.resume();
  }
});
