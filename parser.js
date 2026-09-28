/* JJS Studio parser — browser build of rbxl particle walk */
(function (root) {
'use strict';


class Buffer extends Uint8Array {
  static alloc(n) { return new Buffer(n); }
  static isBuffer(x) { return x instanceof Buffer || x instanceof Uint8Array; }
  static from(x, enc, len) {
    if (x instanceof Buffer) return x;
    if (x instanceof Uint8Array) return new Buffer(x);
    if (x instanceof ArrayBuffer) {
        if (typeof enc === 'number') return new Buffer(new Uint8Array(x, enc, len));
        return new Buffer(new Uint8Array(x));
      }
    if (typeof x === 'string') {
      if (enc === 'hex') {
        const out = new Buffer(x.length / 2);
        for (let i = 0; i < out.length; i++) out[i] = parseInt(x.slice(i * 2, i * 2 + 2), 16);
        return out;
      }
      if (enc === 'base64') {
        const bin = atob(x);
        const out = new Buffer(bin.length);
        for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
        return out;
      }
      return new Buffer(new TextEncoder().encode(x));
    }
    if (ArrayBuffer.isView(x)) return new Buffer(new Uint8Array(x.buffer, x.byteOffset, x.byteLength));
    if (Array.isArray(x)) return new Buffer(Uint8Array.from(x));
    return new Buffer(0);
  }
  static concat(list) {
    const parts = (list || []).map(function (p) {
      return p instanceof Uint8Array ? p : Buffer.from(p);
    });
    let n = 0;
    parts.forEach(function (p) { n += p.length; });
    const out = new Buffer(n);
    let o = 0;
    parts.forEach(function (p) { out.set(p, o); o += p.length; });
    return out;
  }
  slice(a, b) { return new Buffer(Uint8Array.prototype.slice.call(this, a, b)); }
  copy(dest, destStart, srcStart, srcEnd) {
    destStart = destStart || 0;
    srcStart = srcStart || 0;
    srcEnd = srcEnd == null ? this.length : srcEnd;
    const slice = this.subarray(srcStart, srcEnd);
    dest.set(slice, destStart);
    return slice.length;
  }
  toString(enc) {
    if (enc === 'hex') {
      let s = '';
      for (let i = 0; i < this.length; i++) s += this[i].toString(16).padStart(2, '0');
      return s;
    }
    if (enc === 'base64') {
      let bin = '';
      for (let i = 0; i < this.length; i++) bin += String.fromCharCode(this[i]);
      return btoa(bin);
    }
    if (enc === 'latin1' || enc === 'binary' || enc === 'ascii') {
      const chunk = 0x8000;
      let s = '';
      for (let i = 0; i < this.length; i += chunk) {
        s += String.fromCharCode.apply(null, this.subarray(i, i + chunk));
      }
      return s;
    }
    return new TextDecoder('utf-8').decode(this);
  }
  indexOf(needle, from) {
    const start = Math.max(0, from || 0);
    if (typeof needle === 'number') return Uint8Array.prototype.indexOf.call(this, needle, start);
    const n = needle instanceof Uint8Array ? needle : Buffer.from(needle);
    if (!n.length) return start;
    outer: for (let i = start; i <= this.length - n.length; i++) {
      for (let j = 0; j < n.length; j++) if (this[i + j] !== n[j]) continue outer;
      return i;
    }
    return -1;
  }
  readUInt8(off) { return this[off]; }
  readUInt32LE(off) {
    return (this[off] | (this[off + 1] << 8) | (this[off + 2] << 16) | (this[off + 3] << 24)) >>> 0;
  }
  readInt32LE(off) { return this.readUInt32LE(off) | 0; }
  readUInt32BE(off) {
    return ((this[off] << 24) | (this[off + 1] << 16) | (this[off + 2] << 8) | this[off + 3]) >>> 0;
  }
  readFloatLE(off) { return new DataView(this.buffer, this.byteOffset + off, 4).getFloat32(0, true); }
  readDoubleLE(off) { return new DataView(this.buffer, this.byteOffset + off, 8).getFloat64(0, true); }
  writeUInt32LE(v, off) {
    off = off || 0;
    v = v >>> 0;
    this[off] = v & 255;
    this[off + 1] = (v >>> 8) & 255;
    this[off + 2] = (v >>> 16) & 255;
    this[off + 3] = (v >>> 24) & 255;
    return off + 4;
  }
  writeUInt32BE(v, off) {
    off = off || 0;
    v = v >>> 0;
    this[off] = (v >>> 24) & 255;
    this[off + 1] = (v >>> 16) & 255;
    this[off + 2] = (v >>> 8) & 255;
    this[off + 3] = v & 255;
    return off + 4;
  }
}

let fzstd = root.fzstd || root.FZstd || null;
function sanitizeFilename(name) {
  return String(name || 'file').replace(/[<>:"/\\|?*\x00-\x1f]/g, '_').slice(0, 80);
}
function decodeXml(text) {
  return String(text || '')
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
    .replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'").replace(/&amp;/g, '&')
    .trim();
}
const zlib = {
  inflateSync: function (buf) {
    const u8 = buf instanceof Uint8Array ? buf : Buffer.from(buf);
    if (root.pako && typeof root.pako.inflate === 'function') {
      return Buffer.from(root.pako.inflate(u8));
    }
    throw new Error('zlib unavailable');
  }
};

function lz4Block(src, destLen) {
  const dest = Buffer.alloc(destLen);
  let ip = 0;
  let op = 0;
  try {
    while (ip < src.length && op < destLen) {
      const token = src[ip++];
      let lit = token >>> 4;
      if (lit === 15) {
        let s = 255;
        while (s === 255) {
          if (ip >= src.length) return null;
          s = src[ip++];
          lit += s;
        }
      }
      if (ip + lit > src.length || op + lit > destLen) return null;
      src.copy(dest, op, ip, ip + lit);
      ip += lit;
      op += lit;
      if (op === destLen) break;
      if (ip + 2 > src.length) return null;
      const offset = src[ip] | (src[ip + 1] << 8);
      ip += 2;
      if (!offset || offset > op) return null;
      let match = (token & 15) + 4;
      if ((token & 15) === 15) {
        let s = 255;
        while (s === 255) {
          if (ip >= src.length) return null;
          s = src[ip++];
          match += s;
        }
      }
      for (let i = 0; i < match && op < destLen; i++) {
        dest[op] = dest[op - offset];
        op++;
      }
    }
    return dest;
  } catch (e) {
    return null;
  }
}

function isXmlRbxl(buf) {
  const head = buf.slice(0, 64).toString('utf8');
  return head.indexOf('<roblox!') !== 0 && /<roblox[\s>]/i.test(head);
}

function listChunks(buf) {
  if (isXmlRbxl(buf) || buf.slice(0, 8).toString('latin1') !== '<roblox!') return [];
  const starts = [32, 26, 18, 24];
  let best = [];
  let bestOk = -1;
  for (let s = 0; s < starts.length; s++) {
    const chunks = [];
    let offset = starts[s];
    let ok = 0;
    while (offset + 16 <= buf.length) {
      const name = buf.slice(offset, offset + 4).toString('latin1').replace(/\0/g, '');
      const compSize = buf.readUInt32LE(offset + 4);
      const rawSize = buf.readUInt32LE(offset + 8);
      offset += 16;
      if (name === 'END') break;
      if (!/^[A-Z]{3,4}$/.test(name)) break;
      if (compSize > 80 * 1024 * 1024 || rawSize > 80 * 1024 * 1024) break;
      let payload = null;
      if (compSize === 0) {
        if (offset + rawSize > buf.length) break;
        payload = buf.slice(offset, offset + rawSize);
        offset += rawSize;
      } else {
        if (offset + compSize > buf.length) break;
        const packed = buf.slice(offset, offset + compSize);
        offset += compSize;
        payload = inflateChunk(packed, rawSize);
      }
      if (payload && payload.length) {
        chunks.push({ name: name, data: payload });
        if (name === 'INST' || name === 'PROP' || name === 'SSTR') ok++;
      }
    }
    if (ok > bestOk) {
      bestOk = ok;
      best = chunks;
    }
  }
  return best;
}

function expandRbxl(buf) {
  if (isXmlRbxl(buf)) return buf;
  const chunks = listChunks(buf);
  if (!chunks.length) return buf;
  return Buffer.concat([buf].concat(chunks.map(function (c) { return c.data; })));
}

function readLenString(buf, off) {
  if (off + 4 > buf.length) return null;
  const len = buf.readInt32LE(off);
  if (len < 0 || len > 12 * 1024 * 1024 || off + 4 + len > buf.length) return null;
  return { value: buf.slice(off + 4, off + 4 + len).toString('utf8'), offset: off + 4 + len };
}

function readStringArray(buf, off, count) {
  const out = [];
  for (let i = 0; i < count; i++) {
    const s = readLenString(buf, off);
    if (!s) break;
    out.push(s.value);
    off = s.offset;
  }
  return out;
}

function idsInText(text) {
  const ids = [];
  const re = /(?:rbxassetid:\/\/|assetdelivery\.roblox\.com\/v1\/asset\/\?id=|www\.roblox\.com\/asset\/?\?id=|roblox\.com\/asset\/?\?id=)(\d{3,})/gi;
  let m;
  while ((m = re.exec(String(text || '')))) {
    if (m[1] !== '0') ids.push(m[1]);
  }
  return ids;
}

function isZstd(buf) {
  return buf && buf.length >= 4 && buf[0] === 0x28 && buf[1] === 0xb5 && buf[2] === 0x2f && buf[3] === 0xfd;
}

function fileLooksZstd(buf) {
  if (!buf || buf.length < 52) return false;
  const starts = [32, 26, 18, 24];
  for (let i = 0; i < starts.length; i++) {
    const off = starts[i] + 16;
    if (off + 4 <= buf.length && isZstd(buf.slice(off, off + 4))) return true;
  }
  return buf.indexOf(Buffer.from([0x28, 0xb5, 0x2f, 0xfd])) !== -1;
}

function inflateChunk(packed, rawSize) {
  if (!packed || !packed.length) return packed;
  if (isZstd(packed)) {
    if (!fzstd || typeof fzstd.decompress !== 'function') return packed;
    try {
      const out = fzstd.decompress(packed);
      return Buffer.from(out.buffer, out.byteOffset, out.byteLength);
    } catch (e) {
      console.error('[RBXL] zstd decompress failed', e && e.message);
    }
  }
  if (rawSize) {
    const lz = lz4Block(packed, rawSize);
    if (lz && lz.length) return lz;
  }
  return packed;
}

function typeFromContext(propName, className) {
  const p = String(propName || '').toLowerCase();
  const c = String(className || '').toLowerCase();
  if (p === 'soundid' || c === 'sound') return { type: 'Audio', typeId: 3 };
  if (p === 'animationid' || c === 'animation') return { type: 'Animation', typeId: 24 };
  if (p === 'meshid' || p === 'meshid0' || c === 'specialmesh' || c === 'filemesh') return { type: 'Mesh', typeId: 4 };
  if (p === 'shirttemplate') return { type: 'Shirt', typeId: 11 };
  if (p === 'pantstemplate') return { type: 'Pants', typeId: 12 };
  if (c === 'videoframe' || p === 'videoid') return { type: 'Video', typeId: 61 };
  if (p === 'textureid' || p === 'texture' || p === 'graphic' || p === 'image' || p.indexOf('skybox') === 0) {
    if (c === 'decal' || c === 'texture') return { type: 'Decal', typeId: 13 };
    return { type: 'Image', typeId: 1 };
  }
  if (c === 'soundeffect' || c === 'soundgroup') return { type: 'Audio', typeId: 3 };
  if (c === 'particleemitter' || p === 'texture') {
    if (c === 'particleemitter') return { type: 'Particle', typeId: 0 };
  }
  return { type: 'Content', typeId: 0 };
}

const EMISSION_DIR = ['Right', 'Top', 'Back', 'Left', 'Bottom', 'Front'];
const PE_SHAPE = ['Box', 'Sphere', 'Cylinder', 'Disc'];
const PE_SHAPE_INOUT = ['Outward', 'Inward', 'InAndOut'];
const PE_ORIENT = ['FacingCamera', 'FacingCameraWorldUp', 'VelocityParallel', 'VelocityPerpendicular'];
const PE_FLIP_LAYOUT = ['None', 'Grid2x2', 'Grid4x4', 'Grid8x8'];
const PE_FLIP_MODE = ['Loop', 'OneShot', 'PingPong', 'Random'];

function enumName(list, value, fallback) {
  if (value == null || value === '') return fallback;
  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (list.indexOf(trimmed) !== -1) return trimmed;
    const n = Number(trimmed);
    if (Number.isFinite(n) && list[n]) return list[n];
    return fallback;
  }
  const n = Number(value);
  if (Number.isFinite(n) && list[n]) return list[n];
  return fallback;
}

function bodyPartFromName(name) {
  name = String(name || '').toLowerCase().replace(/[^a-z0-9]/g, '');
  if (/left(hand|arm|upperarm|lowerarm)/.test(name)) return 'Left Arm';
  if (/right(hand|arm|upperarm|lowerarm)/.test(name)) return 'Right Arm';
  if (/left(foot|leg|upperleg|lowerleg)/.test(name)) return 'Left Leg';
  if (/right(foot|leg|upperleg|lowerleg)/.test(name)) return 'Right Leg';
  if (/torso|upper|lower|waist/.test(name)) return 'Torso';
  if (/head|face/.test(name)) return 'Head';
  if (/root|humanoid|hrp/.test(name)) return 'HumanoidRootPart';
  return 'HumanoidRootPart';
}

function partForBody(inst) {
  let p = inst;
  let guard = 0;
  const seen = new Set();
  while (p && guard++ < 64 && !seen.has(p)) {
    seen.add(p);
    const n = String(p.className || '');
    if (/^(Part|MeshPart|WedgePart|CornerWedgePart|TrussPart|UnionOperation|NegateOperation|VehicleSeat|Seat|SpawnLocation|BasePart)$/i.test(n)) return p;
    p = p.parent;
  }
  return inst && inst.parent;
}

function textureIdFrom(value) {
  if (value && typeof value === 'object') {
    if (value.uri != null) return textureIdFrom(value.uri);
    if (value.value != null) return textureIdFrom(value.value);
    if (value.url != null) return textureIdFrom(value.url);
  }
  const s = String(value == null ? '' : value);
  let m = s.match(/id=(\d+)/i) || s.match(/rbxassetid:\/\/(\d+)/i) || s.match(/(\d{5,})/);
  return m ? Number(m[1]) : 0;
}

let PARSE_OPTS = { meshes: false, cameras: false };
function setParseOpts(opts) {
  PARSE_OPTS = {
    meshes: !!(opts && opts.meshes),
    cameras: !!(opts && opts.cameras)
  };
}

function wantsTypedProps(className) {
  const cn = String(className || '');
  if (/^ParticleEmitter$/i.test(cn)) return true;
  if (/^(Sound|SoundEffect|Beam)$/i.test(cn)) return true;
  if (/^(MeshPart|SpecialMesh|FileMesh)$/i.test(cn)) return !!PARSE_OPTS.meshes;
  if (/^(Camera|Keyframe|Pose|CFrameValue|NumberValue|IntValue|Vector3Value|StringValue)$/i.test(cn)) return !!PARSE_OPTS.cameras;
  if (/^(Attachment|Part|BasePart|WedgePart|CornerWedgePart|TrussPart|UnionOperation|NegateOperation|VehicleSeat|Seat|SpawnLocation)$/i.test(cn)) {
    return !!(PARSE_OPTS.meshes || PARSE_OPTS.cameras);
  }
  return false;
}

function cleanNum(n, digits) {
  n = Number(n);
  if (!Number.isFinite(n)) return 0;
  if (Math.abs(n) < 1e-5) return 0;
  if (Math.abs(n) > 100000) return 0;
  const d = digits == null ? 3 : digits;
  return Number(n.toFixed(d));
}

function vec3FromAny(v) {
  if (v == null) return '0, 0, 0';
  let x = 0, y = 0, z = 0;
  if (typeof v === 'string') {
    const n = v.split(/[,\s]+/).map(Number).filter(function (q) { return Number.isFinite(q); });
    if (n.length >= 3) { x = n[0]; y = n[1]; z = n[2]; }
    else if (n.length === 1) x = n[0];
  } else if (typeof v === 'number') {
    x = v;
  } else if (typeof v === 'object') {
    x = Number(v.x != null ? v.x : v[0]) || 0;
    y = Number(v.y != null ? v.y : v[1]) || 0;
    z = Number(v.z != null ? v.z : v[2]) || 0;
  }
  return cleanNum(x) + ', ' + cleanNum(y) + ', ' + cleanNum(z);
}

function particleExtrasFromInst(inst) {
  const base = partForBody(inst);
  let partSize = '0, 0, 0';
  let position = '0, 0, 0';
  let cur = inst;
  let guard = 0;
  const seen = new Set();
  while (cur && guard++ < 64 && !seen.has(cur)) {
    seen.add(cur);
    const props = cur.props || {};
    if (partSize === '0, 0, 0' && props.Size) partSize = vec3FromAny(props.Size);
    if (String(cur.className || '') === 'Attachment' && props.Position) {
      position = vec3FromAny(props.Position);
    }
    cur = cur.parent;
  }
  return {
    body: bodyPartFromName(base && base.name),
    partSize: partSize,
    position: position
  };
}

function parseSstrChunk(data) {
  const out = [];
  if (!data || data.length < 8) return out;
  const count = data.readUInt32LE(4);
  let off = 8;
  for (let i = 0; i < count && off + 4 <= data.length; i++) {
    if (off + 16 + 4 <= data.length) off += 16;
    if (off + 4 > data.length) break;
    const len = data.readInt32LE(off);
    off += 4;
    if (len < 0 || off + len > data.length) break;
    out.push(data.slice(off, off + len).toString('utf8'));
    off += len;
  }
  return out;
}

function fmtSeqNum(n, digits) {
  n = Number(n);
  if (!Number.isFinite(n)) n = 0;
  const d = digits == null ? 4 : digits;
  let s = n.toFixed(d);
  if (d === 4) s = s.replace(/0+$/, '').replace(/\.$/, '');
  if (s === '-0') s = '0';
  return s;
}

function numSeqToStr(kps, digits) {
  if (!kps || !kps.length) return digits === 2 ? '0.00,0.00' : '0.0000';
  const vals = kps.map(function (k) { return k && typeof k === 'object' ? k.v : k; });
  const d = digits == null ? 4 : digits;
  const join = d === 2 ? ',' : ',';
  return vals.map(function (v) { return fmtSeqNum(v, d); }).join(join);
}

function saneNum(n, fallback) {
  n = Number(n);
  if (!Number.isFinite(n) || Math.abs(n) > 1e7) return fallback != null ? fallback : 0;
  return n;
}

function numRangeToStr(nr) {
  if (!nr || typeof nr !== 'object') {
    if (typeof nr === 'number') return Number(saneNum(nr, 0)).toFixed(2) + ', ' + Number(saneNum(nr, 0)).toFixed(2);
    return '0.00, 0.00';
  }
  const a = saneNum(nr.min != null ? nr.min : nr[0], 0);
  const b = saneNum(nr.max != null ? nr.max : nr[1], a);
  return Number(a).toFixed(2) + ', ' + Number(b).toFixed(2);
}

function parseAttributesBlob(raw) {
  const out = {};
  if (raw == null) return out;
  let buf;
  if (Buffer.isBuffer(raw)) buf = raw;
  else if (typeof raw === 'string') {
    if (!raw) return out;
    if (/^[A-Za-z0-9+/=]+$/.test(raw) && raw.length >= 8) {
      try { buf = Buffer.from(raw, 'base64'); } catch (e) { buf = Buffer.from(raw, 'binary'); }
    } else buf = Buffer.from(raw, 'binary');
  } else return out;
  if (buf.length < 8) return out;
  try {
    let off = 0;
    const count = buf.readUInt32LE(off); off += 4;
    if (count > 256) return out;
    for (let i = 0; i < count && off + 5 <= buf.length; i++) {
      const nlen = buf.readUInt32LE(off); off += 4;
      if (nlen < 0 || nlen > 200 || off + nlen + 1 > buf.length) break;
      const name = buf.slice(off, off + nlen).toString('utf8');
      off += nlen;
      const typeId = buf[off]; off += 1;
      let value = null;
      if (typeId === 0x02) {
        if (off + 4 > buf.length) break;
        const sl = buf.readUInt32LE(off); off += 4;
        if (off + sl > buf.length) break;
        value = buf.slice(off, off + sl).toString('utf8');
        off += sl;
      } else if (typeId === 0x03) {
        value = !!buf[off]; off += 1;
      } else if (typeId === 0x04) {
        if (off + 4 > buf.length) break;
        value = buf.readInt32LE(off); off += 4;
      } else if (typeId === 0x05) {
        if (off + 4 > buf.length) break;
        value = buf.readFloatLE(off); off += 4;
      } else if (typeId === 0x06) {
        if (off + 8 > buf.length) break;
        value = buf.readDoubleLE(off); off += 8;
      } else if (typeId === 0x11 || typeId === 0x0e) {
        if (off + 12 > buf.length) break;
        value = { x: buf.readFloatLE(off), y: buf.readFloatLE(off + 4), z: buf.readFloatLE(off + 8) };
        off += 12;
      } else if (typeId === 0x1b) {
        if (off + 8 > buf.length) break;
        value = { min: buf.readFloatLE(off), max: buf.readFloatLE(off + 4) };
        off += 8;
      } else {
        break;
      }
      if (name) out[name] = value;
    }
  } catch (e) {}
  function scanNum(name) {
    if (out[name] != null) return;
    const needle = Buffer.from(name);
    let idx = buf.indexOf(needle);
    while (idx >= 4) {
      const nlen = buf.readUInt32LE(idx - 4);
      if (nlen === name.length && idx + name.length < buf.length) {
        const typeId = buf[idx + name.length];
        const o = idx + name.length + 1;
        if (typeId === 4 && o + 4 <= buf.length) out[name] = buf.readInt32LE(o);
        else if (typeId === 5 && o + 4 <= buf.length) out[name] = buf.readFloatLE(o);
        else if (typeId === 6 && o + 8 <= buf.length) out[name] = buf.readDoubleLE(o);
        return;
      }
      idx = buf.indexOf(needle, idx + 1);
    }
  }
  scanNum('EmitCount');
  scanNum('EmitDuration');
  scanNum('EmitDelay');
  scanNum('Duration');
  scanNum('EffectDuration');
  scanNum('Scale_Start');
  scanNum('Scale_End');
  scanNum('MinSize');
  scanNum('MaxSize');
  scanNum('Mesh_StartTransparency');
  scanNum('Mesh_EndTransparency');
  scanNum('Part_Transparency_Start');
  scanNum('Part_Transparency_End');
  scanNum('Size_Duration');
  scanNum('EmitDelay');
  return out;
}

function clamp255(n) {
  n = Number(n);
  if (!Number.isFinite(n)) return 255;
  if (n < 0) return 0;
  if (n > 255) return 255;
  return Math.round(n);
}

function colorSeqToStr(kps) {
  function chan(v) {
    v = Number(v);
    if (!Number.isFinite(v)) return 255;
    if (v >= 0 && v <= 1) return Math.round(v * 255);
    return clamp255(v);
  }
  function col(c) {
    if (!c || typeof c !== 'object') return '255,255,255';
    if (c.r == null && c.g == null && c.b == null && c.v != null) {
      const g = chan(c.v);
      return g + ',' + g + ',' + g;
    }
    return chan(c.r) + ',' + chan(c.g) + ',' + chan(c.b);
  }
  if (!Array.isArray(kps) || !kps.length) return '255,255,255 255,255,255';
  const first = col(kps[0]);
  const last = col(kps[kps.length - 1]);
  if (last === '0,0,0' && first !== '0,0,0') return first + ' ' + first;
  return first + ' ' + last;
}

function saneZ(n) {
  n = Number(n);
  if (!Number.isFinite(n) || Math.abs(n) > 50) return 0;
  return n;
}

function saneSpread(v) {
  const x = saneNum(v && v.x, 0);
  const y = saneNum(v && v.y, 0);
  const yy = Math.abs(y) < 0.05 && Math.abs(x) >= 1 ? 0 : y;
  return x + ', ' + yy + ', 0';
}

function accelComp(n) {
  n = Number(n);
  if (!Number.isFinite(n) || Math.abs(n) < 1e-6) return 0;
  if (Math.abs(n) >= 1) {
    const r = Math.round(n);
    return Math.abs(n - r) < 0.2 ? r : Number(n.toFixed(3));
  }
  // Studio stores tiny studs/s^2; JJS plugin exports tens so specks fall.
  return Math.round(n * 100);
}

function vec3Str(v) {
  if (!v) return '0, 0, 0';
  return accelComp(v.x) + ', ' + accelComp(v.y) + ', ' + accelComp(v.z);
}

function defaultParticle(over) {
  return Object.assign({
    ACCELERATION: '0, 0, 0',
    'BODY PART': 'HumanoidRootPart',
    BRIGHTNESS: 1,
    CANCEL: false,
    'CANCEL ON INTERRUPT': false,
    'CANCEL TAG': '',
    'CLIENT SIDED': false,
    COLOR: '255,255,255 255,255,255',
    DRAG: 0,
    DURATION: 5,
    'EMISSION DIRECTION': 'Top',
    'EMIT COUNT': 0,
    'FLIPBOOK FRAMERATE': '1, 1',
    'FLIPBOOK MODE': 'OneShot',
    'FLIPBOOK SIZE': '0',
    K_NAME: 'PARTICLE',
    'LAST HIT': -1,
    LIFETIME: '0, 0',
    'LIGHT EMISSION': 0,
    'LIGHT INFLUENCE': 0,
    'LOCK TO PART': false,
    'ORIENTATION TYPE': 'FacingCamera',
    'PART SIZE': '0, 0, 0',
    POSITION: '0, 0, 0',
    'PROJECTILE TAG': '',
    RATE: 20,
    'ROT SPEED': '0, 0',
    ROTATION: '0, 0',
    'RUN ON SERVER': false,
    SHAPE: 'Box',
    'SHAPE INOUT': 'Outward',
    'SHAPE PARTIAL': 1,
    SIZE: '0, 0',
    SPEED: '0, 0',
    'SPREAD ANGLE': '0, 0, 0',
    SQUASH: '0, 0',
    TEXTURE: 0,
    TRANSPARENCY: '0, 0',
    ZOFFSET: 0
  }, over || {});
}

function flipbookSizeFromLayout(layout) {
  const name = enumName(PE_FLIP_LAYOUT, layout, 'None');
  if (name === 'Grid2x2') return '2, 2';
  if (name === 'Grid4x4') return '4, 4';
  if (name === 'Grid8x8') return '8, 8';
  return '1, 1';
}

const flipbookImageCache = {};

function textureIdDigits(tex) {
  return String(tex == null ? '' : tex).replace(/\D/g, '');
}

function paeth(a, b, c) {
  const p = a + b - c;
  const pa = Math.abs(p - a);
  const pb = Math.abs(p - b);
  const pc = Math.abs(p - c);
  if (pa <= pb && pa <= pc) return a;
  if (pb <= pc) return b;
  return c;
}

function decodePngRgba(buf) {
  try {
    if (!buf || buf.length < 24 || buf[0] !== 0x89 || buf[1] !== 0x50) return null;
    let off = 8;
    let w = 0;
    let h = 0;
    let depth = 8;
    let ctype = 6;
    const parts = [];
    while (off + 12 <= buf.length) {
      const len = buf.readUInt32BE(off);
      const type = buf.slice(off + 4, off + 8).toString('ascii');
      if (off + 12 + len > buf.length) break;
      const data = buf.slice(off + 8, off + 8 + len);
      off += 12 + len;
      if (type === 'IHDR') {
        w = data.readUInt32BE(0);
        h = data.readUInt32BE(4);
        depth = data[8];
        ctype = data[9];
      } else if (type === 'IDAT') {
        parts.push(data);
      } else if (type === 'IEND') {
        break;
      }
    }
    if (!w || !h || w > 4096 || h > 4096 || depth !== 8) return null;
    const raw = zlib.inflateSync(Buffer.concat(parts));
    const bpp = ctype === 6 ? 4 : ctype === 2 ? 3 : ctype === 4 ? 2 : ctype === 0 ? 1 : 0;
    if (!bpp) return null;
    const stride = w * bpp;
    const rgba = Buffer.alloc(w * h * 4);
    let src = 0;
    const prev = Buffer.alloc(stride);
    const row = Buffer.alloc(stride);
    for (let y = 0; y < h; y++) {
      const filt = raw[src++];
      raw.copy(row, 0, src, src + stride);
      src += stride;
      if (filt === 1) {
        for (let i = 0; i < stride; i++) row[i] = (row[i] + (i >= bpp ? row[i - bpp] : 0)) & 255;
      } else if (filt === 2) {
        for (let i = 0; i < stride; i++) row[i] = (row[i] + prev[i]) & 255;
      } else if (filt === 3) {
        for (let i = 0; i < stride; i++) {
          const a = i >= bpp ? row[i - bpp] : 0;
          row[i] = (row[i] + ((a + prev[i]) >> 1)) & 255;
        }
      } else if (filt === 4) {
        for (let i = 0; i < stride; i++) {
          const a = i >= bpp ? row[i - bpp] : 0;
          const b = prev[i];
          const c = i >= bpp ? prev[i - bpp] : 0;
          row[i] = (row[i] + paeth(a, b, c)) & 255;
        }
      }
      row.copy(prev, 0, 0, stride);
      for (let x = 0; x < w; x++) {
        const si = x * bpp;
        const di = (y * w + x) * 4;
        if (ctype === 6) {
          rgba[di] = row[si]; rgba[di + 1] = row[si + 1]; rgba[di + 2] = row[si + 2]; rgba[di + 3] = row[si + 3];
        } else if (ctype === 2) {
          rgba[di] = row[si]; rgba[di + 1] = row[si + 1]; rgba[di + 2] = row[si + 2]; rgba[di + 3] = 255;
        } else if (ctype === 4) {
          rgba[di] = row[si]; rgba[di + 1] = row[si]; rgba[di + 2] = row[si]; rgba[di + 3] = row[si + 1];
        } else {
          rgba[di] = row[si]; rgba[di + 1] = row[si]; rgba[di + 2] = row[si]; rgba[di + 3] = 255;
        }
      }
    }
    return { w: w, h: h, rgba: rgba };
  } catch (e) {
    return null;
  }
}

function downsampleRgba(img, size) {
  const out = Buffer.alloc(size * size * 4);
  for (let y = 0; y < size; y++) {
    const sy = Math.min(img.h - 1, Math.floor(y * img.h / size));
    for (let x = 0; x < size; x++) {
      const sx = Math.min(img.w - 1, Math.floor(x * img.w / size));
      img.rgba.copy(out, (y * size + x) * 4, (sy * img.w + sx) * 4, (sy * img.w + sx) * 4 + 4);
    }
  }
  return { w: size, h: size, rgba: out };
}

function pxEmpty(rgba, i, thr) {
  const a = rgba[i + 3];
  if (a <= thr) return true;
  return (rgba[i] + rgba[i + 1] + rgba[i + 2]) / 3 <= thr;
}

function detectFlipbookFromRgba(img) {
  if (!img || !img.rgba || img.w < 16 || img.h < 16) return '1, 1';
  const sample = (img.w === 256 && img.h === 256) ? img : downsampleRgba(img, 256);
  const w = sample.w;
  const h = sample.h;
  const rgba = sample.rgba;
  function stats(n) {
    const cw = (w / n) | 0;
    const ch = (h / n) | 0;
    let ge = 0;
    let gt = 0;
    for (let k = 1; k < n; k++) {
      const x = k * cw;
      for (let y = 0; y < h; y++) {
        ge += pxEmpty(rgba, (y * w + x) * 4, 20) ? 1 : 0;
        ge += pxEmpty(rgba, (y * w + Math.max(x - 1, 0)) * 4, 20) ? 1 : 0;
        gt += 2;
      }
      const y = k * ch;
      for (let x2 = 0; x2 < w; x2++) {
        ge += pxEmpty(rgba, (y * w + x2) * 4, 20) ? 1 : 0;
        ge += pxEmpty(rgba, (Math.max(y - 1, 0) * w + x2) * 4, 20) ? 1 : 0;
        gt += 2;
      }
    }
    let live = 0;
    const step = Math.max(1, (cw / 16) | 0);
    for (let gy = 0; gy < n; gy++) {
      for (let gx = 0; gx < n; gx++) {
        let fill = 0;
        let tot = 0;
        for (let y = gy * ch; y < gy * ch + ch; y += step) {
          for (let x = gx * cw; x < gx * cw + cw; x += step) {
            tot++;
            if (!pxEmpty(rgba, (y * w + x) * 4, 20)) fill++;
          }
        }
        if (tot && fill / tot > 0.01) live++;
      }
    }
    return { gutter: ge / Math.max(gt, 1), liveR: live / (n * n) };
  }
  let pick = '1, 1';
  const s2 = stats(2);
  const s4 = stats(4);
  const s8 = stats(8);
  if (s2.gutter >= 0.88 && s2.liveR >= 0.9) pick = '2, 2';
  if (s4.gutter >= 0.90 && s4.liveR >= 0.70) pick = '4, 4';
  if (s8.gutter >= 0.90 && s8.liveR >= 0.45) pick = '8, 8';
  return pick;
}

async function fetchTexturePng(assetId) {
  const id = textureIdDigits(assetId);
  if (!id) return null;
  async function grab(url) {
    try {
      const res = await fetch(url, { timeout: 15000, headers: HEADERS, redirect: 'follow' });
      if (!res || !res.ok) return null;
      const raw = res.buffer ? await res.buffer() : Buffer.from(await res.arrayBuffer());
      const buf = Buffer.isBuffer(raw) ? raw : Buffer.from(raw);
      if (buf.length > 8 && buf[0] === 0x89 && buf[1] === 0x50) return buf;
      return null;
    } catch (e) {
      return null;
    }
  }
  try {
    const metaRes = await fetch('https://thumbnails.roblox.com/v1/assets?assetIds=' + id + '&size=420x420&format=Png', {
      timeout: 15000,
      headers: HEADERS
    });
    const meta = metaRes && metaRes.ok ? await metaRes.json().catch(function () { return null; }) : null;
    const thumb = meta && meta.data && meta.data[0] && meta.data[0].imageUrl;
    if (thumb) {
      const png = await grab(thumb);
      if (png) return png;
    }
  } catch (e) {}
  return await grab('https://assetdelivery.roblox.com/v1/asset/?id=' + id);
}

async function resolveFlipbookFromTexture(assetId) {
  const id = textureIdDigits(assetId);
  if (!id) return '1, 1';
  if (Object.prototype.hasOwnProperty.call(flipbookImageCache, id)) return flipbookImageCache[id];
  flipbookImageCache[id] = '1, 1';
  try {
    const png = await fetchTexturePng(id);
    const decoded = png ? decodePngRgba(png) : null;
    const size = decoded ? detectFlipbookFromRgba(decoded) : '1, 1';
    flipbookImageCache[id] = size;
    return size;
  } catch (e) {
    return '1, 1';
  }
}

async function applyDetectedFlipbooks(particles) {
  const list = particles || [];
  const pending = {};
  list.forEach(function (p) {
    const id = textureIdDigits(p && p.TEXTURE);
    if (!id) return;
    const cur = String(p['FLIPBOOK SIZE'] || '1, 1');
    if (cur !== '1, 1' && cur !== '0' && cur !== '1,1') return;
    pending[id] = true;
  });
  const ids = Object.keys(pending);
  const limit = 8;
  for (let i = 0; i < ids.length; i += limit) {
    const slice = ids.slice(i, i + limit);
    await Promise.all(slice.map(function (id) { return resolveFlipbookFromTexture(id); }));
  }
  list.forEach(function (p) {
    const id = textureIdDigits(p && p.TEXTURE);
    if (!id) return;
    const cur = String(p['FLIPBOOK SIZE'] || '1, 1');
    if (cur !== '1, 1' && cur !== '0' && cur !== '1,1') return;
    if (flipbookImageCache[id]) p['FLIPBOOK SIZE'] = flipbookImageCache[id];
  });
  return list;
}

function flipbookSizeFromProps(props) {
  props = props || {};
  const fromLayout = flipbookSizeFromLayout(props.FlipbookLayout);
  // FlipbookLayout token is authoritative when set.
  // FlipbookSizeX/Y stay at default 1 even on grid layouts.
  if (fromLayout && fromLayout !== '1, 1') return fromLayout;
  return '1, 1';
}

function clampSpread(n) {
  n = Number(n);
  if (!Number.isFinite(n)) return 0;
  if (n > 720) n = 720;
  if (n < -720) n = -720;
  return n;
}
function unglueSpread(n) {
  n = Number(n);
  if (!Number.isFinite(n)) return null;
  const sign = n < 0 ? -1 : 1;
  const s = String(Math.round(Math.abs(n)));
  if (Math.abs(n) <= 720) return null;
  if (s.length >= 4 && s.length % 2 === 0) {
    const half = s.length / 2;
    return { x: sign * Number(s.slice(0, half)), y: sign * Number(s.slice(half)) };
  }
  return null;
}
function spreadToStr(v) {
  let x = 0;
  let y = 0;
  if (typeof v === 'number') {
    const g = unglueSpread(v);
    if (g) { x = g.x; y = g.y; }
    else x = v;
  } else if (v && typeof v === 'object') {
    x = saneNum(v.x != null ? v.x : v[0], 0);
    y = saneNum(v.y != null ? v.y : v[1], 0);
    if (Math.abs(x) > 720 && Math.abs(y) < 1) {
      const g = unglueSpread(x);
      if (g) { x = g.x; y = g.y; }
    }
  } else if (typeof v === 'string') {
    const n = v.split(/[,\s]+/).map(Number).filter(function (x) { return Number.isFinite(x); });
    if (n.length >= 2) { x = n[0]; y = n[1]; }
    else if (n.length === 1) {
      const g = unglueSpread(n[0]);
      if (g) { x = g.x; y = g.y; } else x = n[0];
    }
  }
  return clampSpread(x).toFixed(2) + ', ' + clampSpread(y).toFixed(2) + ', 0.00';
}

function particleFromProps(props, extra) {
  extra = extra || {};
  const attrs = Object.assign(
    {},
    parseAttributesBlob(props.AttributesSerialize),
    parseAttributesBlob(extra.attributes),
    extra.attrs || {}
  );
  const rawEmit = attrs.EmitCount != null ? Number(attrs.EmitCount) : (props.EmitCount != null ? Number(props.EmitCount) : NaN);
  const rawDur = attrs.EmitDuration != null ? Number(attrs.EmitDuration) : (attrs.Duration != null ? Number(attrs.Duration) : 0);
  const rateNum = Number(props.Rate);
  const life = props.Lifetime || {};
  const avgLife = ((Number(life.min) || 0) + (Number(life.max) || 0)) / 2;
  let emitCount = Number.isFinite(rawEmit) && rawEmit > 0 ? Math.round(rawEmit) : 0;
  if (emitCount <= 0) {
    // VFX Forge default EmitCount is 1 and always calls Emit(count).
    // Plugin JJS lines use DURATION 0, so emit 0 is invisible.
    if (Number.isFinite(rateNum) && rateNum >= 0.05) {
      emitCount = Math.round(rateNum * Math.min(avgLife > 0 ? avgLife : 0.2, 0.25));
      if (emitCount < 1) emitCount = 1;
      if (emitCount > 15) emitCount = 15;
    } else {
      emitCount = 1;
    }
  }
  const duration = 0;
  const spread = props.SpreadAngle || { x: 0, y: 0 };
  const z = (function () {
    let v = Number(props.ZOffset);
    if (!Number.isFinite(v)) return 0;
    if (v < -2) return -1;
    if (v > 6) return 6;
    if (Math.abs(v) < 0.05) return 0;
    return Number(v.toFixed(3));
  })();
  return defaultParticle({
    ACCELERATION: vec3Str(props.Acceleration),
    BRIGHTNESS: (function () {
      const b = Number(props.Brightness);
      if (!Number.isFinite(b)) return 1;
      if (Math.abs(b) < 1e-6) return 0;
      if (b < 0) return 0;
      return b;
    })(),
    COLOR: colorSeqToStr(props.Color),
    DRAG: props.Drag != null ? props.Drag : 0,
    DURATION: 0,
    'EMISSION DIRECTION': enumName(EMISSION_DIR, props.EmissionDirection, 'Top'),
    'EMIT COUNT': Number.isFinite(emitCount) ? Math.round(emitCount) : 0,
    'FLIPBOOK FRAMERATE': numRangeToStr(props.FlipbookFramerate || { min: 1, max: 1 }),
    'FLIPBOOK MODE': enumName(PE_FLIP_MODE, props.FlipbookMode, 'OneShot'),
    'FLIPBOOK SIZE': flipbookSizeFromProps(props),
    LIFETIME: numRangeToStr(props.Lifetime),
    'LIGHT EMISSION': (function () {
      const v = Number(props.LightEmission);
      if (!Number.isFinite(v) || v < 0 || v > 2) return 0;
      return v;
    })(),
    'LIGHT INFLUENCE': (function () {
      const v = Number(props.LightInfluence);
      if (!Number.isFinite(v) || v < 0 || v > 2) return 0;
      return v;
    })(),
    'LOCK TO PART': !!props.LockedToPart,
    'ORIENTATION TYPE': enumName(PE_ORIENT, props.Orientation, 'FacingCamera'),
    'PART SIZE': extra.partSize || '0, 0, 0',
    POSITION: extra.position || '0, 0, 0',
    RATE: (function () {
      const r = Number(props.Rate);
      if (!Number.isFinite(r) || r < 0 || r > 100000) return 0;
      if (r > 0 && r < 1e-6) return 0;
      return r;
    })(),
    'ROT SPEED': numRangeToStr(props.RotSpeed),
    ROTATION: numRangeToStr(props.Rotation),
    'RUN ON SERVER': true,
    SIZE: numSeqToStr(props.Size, 4),
    SPEED: numRangeToStr(props.Speed),
    'SPREAD ANGLE': spreadToStr(spread),
    SQUASH: numSeqToStr(props.Squash, 2),
    TEXTURE: textureIdFrom(props.Texture != null ? props.Texture : props.TextureId),
    TRANSPARENCY: numSeqToStr(props.Transparency, 2),
    ZOFFSET: Number.isFinite(z) && Math.abs(z) <= 200 ? z : 0,
    NAME: extra.name || extra.Name || undefined
  });
}

function buildJjsJson(particles, fileName) {
  const skillData = {
    Req: {},
    Line: {},
    Prop: {},
    Branch: {
      converted: {
        Req: {},
        Line: particles || []
      }
    }
  };
  const skill = {
    ADD: false,
    NAME: String(fileName || 'converted').replace(/\.[^.]+$/, ''),
    K_NAME: 'SKILL',
    KEY: 1,
    COOLDOWN: 0,
    DATA: JSON.stringify(skillData),
    'TOOL TIP': ''
  };
  return JSON.stringify([skill]);
}

function deinterleave(buf, off, count, size) {
  if (!buf || off + count * size > buf.length) return null;
  const out = Buffer.alloc(count * size);
  for (let i = 0; i < count; i++) {
    for (let j = 0; j < size; j++) out[i * size + j] = buf[off + j * count + i];
  }
  return out;
}

function floatScore(v) {
  if (!Number.isFinite(v)) return -1;
  const a = Math.abs(v);
  if (a === 0) return 2;
  if (a > 1e7 || a < 1e-8) return 0;
  if (a >= 0.001 && a <= 10000) return 4;
  if (a <= 1e6) return 1;
  return 0;
}

function pickCframeFloat(buf, index) {
  let raw = 0;
  try { raw = buf.readFloatLE(index); } catch (e) { raw = 0; }
  const zig = rotatedFloatAt(buf, index);
  const sRaw = floatScore(raw);
  const sZig = floatScore(zig);
  const v = sZig > sRaw ? zig : raw;
  if (!Number.isFinite(v) || Math.abs(v) > 100000) return 0;
  if (Math.abs(v) < 1e-5) return 0;
  return v;
}

function matrixToEuler(m) {
  if (!m || m.length < 9) return '0, 0, 0';
  const r00 = m[0], r01 = m[1], r02 = m[2];
  const r10 = m[3], r11 = m[4], r12 = m[5];
  const r20 = m[6], r21 = m[7], r22 = m[8];
  // Roblox CFrame:ToEulerAnglesYXZ (pitch X, yaw Y, roll Z)
  let x, y, z;
  const sy = Math.max(-1, Math.min(1, -r20));
  y = Math.asin(sy);
  if (Math.abs(r20) < 0.999999) {
    x = Math.atan2(r21, r22);
    z = Math.atan2(r10, r00);
  } else {
    x = Math.atan2(-r12, r11);
    z = 0;
  }
  const deg = function (r) { return cleanNum(r * 180 / Math.PI, 2); };
  return deg(x) + ', ' + deg(y) + ', ' + deg(z);
}

function rotatedFloatAt(buf, index) {
  function from(u) {
    const tmp = Buffer.alloc(4);
    tmp.writeUInt32LE(rotateI32(u) >>> 0);
    return tmp.readFloatLE(0);
  }
  let le = NaN;
  let be = NaN;
  try { le = from(buf.readUInt32LE(index)); } catch (e) {}
  try { be = from(buf.readUInt32BE(index)); } catch (e) {}
  const sL = floatScore(le);
  const sB = floatScore(be);
  if (sB > sL) return be;
  if (sL > sB) return le;
  if (sB >= 0) return be;
  if (sL >= 0) return le;
  return 0;
}

function rotatedU32At(buf, index) {
  // Token/enum arrays are byte-interleaved raw integers, NOT rotate-encoded.
  // After deinterleave the value sits in the last byte ([0,0,0,N] => N).
  let rawBe = 0;
  let rawLe = 0;
  try { rawBe = buf.readUInt32BE(index) >>> 0; } catch (e) {}
  try { rawLe = buf.readUInt32LE(index) >>> 0; } catch (e) {}
  if (rawBe <= 32) return rawBe;
  if (rawLe <= 32) return rawLe;
  const hi = buf[index];
  const lo = buf[index + 3];
  if (hi <= 32) return hi;
  if (lo <= 32) return lo;
  let le = 0;
  let be = 0;
  try { le = rotateI32(rawLe) >>> 0; } catch (e) {}
  try { be = rotateI32(rawBe) >>> 0; } catch (e) {}
  if (le <= 32) return le;
  if (be <= 32) return be;
  return rawBe <= rawLe ? rawBe : rawLe;
}

function parseTypedProp(data, typeId, start, count, sharedStrings) {
  const values = new Array(count);
  sharedStrings = sharedStrings || [];
  try {
    if (typeId === 1) return readStringArray(data, start, count);
    if (typeId === 2) {
      for (let i = 0; i < count; i++) values[i] = !!data[start + i];
      return values;
    }
    if (typeId === 4) {
      const raw = deinterleave(data, start, count, 4);
      for (let i = 0; i < count; i++) {
        const inter = raw ? rotatedFloatAt(raw, i * 4) : NaN;
        const seq = rotatedFloatAt(data, start + i * 4);
        const okI = Number.isFinite(inter) && Math.abs(inter) < 1000000;
        const okS = Number.isFinite(seq) && Math.abs(seq) < 1000000;
        if (okI && Math.abs(inter) >= 1e-8) values[i] = inter;
        else if (okS && Math.abs(seq) >= 1e-8) values[i] = seq;
        else if (okI) values[i] = inter;
        else if (okS) values[i] = seq;
        else values[i] = 0;
      }
      return values;
    }
    if (typeId === 5) {
      const raw = deinterleave(data, start, count, 8);
      if (!raw) return values;
      for (let i = 0; i < count; i++) values[i] = raw.readDoubleLE(i * 8);
      return values;
    }
    if (typeId === 0x0c) {
      const raw = deinterleave(data, start, count, 12);
      if (!raw) return values;
      for (let i = 0; i < count; i++) {
        values[i] = [{
          t: 0,
          r: rotatedFloatAt(raw, i * 12),
          g: rotatedFloatAt(raw, i * 12 + 4),
          b: rotatedFloatAt(raw, i * 12 + 8),
          e: 0
        }];
      }
      return values;
    }
    if (typeId === 0x0d) {
      const xs = deinterleave(data, start, count, 4);
      const ys = deinterleave(data, start + count * 4, count, 4);
      if (!xs || !ys) return values;
      for (let i = 0; i < count; i++) values[i] = { x: rotatedFloatAt(xs, i * 4), y: rotatedFloatAt(ys, i * 4) };
      return values;
    }
    if (typeId === 0x0e) {
      const xs = deinterleave(data, start, count, 4);
      const ys = deinterleave(data, start + count * 4, count, 4);
      const zs = deinterleave(data, start + count * 8, count, 4);
      if (!xs || !ys || !zs) return values;
      for (let i = 0; i < count; i++) {
        values[i] = {
          x: rotatedFloatAt(xs, i * 4),
          y: rotatedFloatAt(ys, i * 4),
          z: rotatedFloatAt(zs, i * 4)
        };
      }
      return values;
    }
    if (typeId === 0x10) {
      let off = start;
      const mats = new Array(count);
      for (let i = 0; i < count; i++) {
        if (off >= data.length) break;
        const rid = data[off];
        off += 1;
        if (rid === 0 && off + 36 <= data.length) {
          const m = [];
          for (let k = 0; k < 9; k++) {
            m.push(data.readFloatLE(off));
            off += 4;
          }
          mats[i] = m;
        }
      }
      const xs = deinterleave(data, off, count, 4);
      const ys = deinterleave(data, off + count * 4, count, 4);
      const zs = deinterleave(data, off + count * 8, count, 4);
      if (xs && ys && zs) {
        for (let i = 0; i < count; i++) {
          const pos = {
            x: pickCframeFloat(xs, i * 4),
            y: pickCframeFloat(ys, i * 4),
            z: pickCframeFloat(zs, i * 4)
          };
          const rot = mats[i] ? matrixToEuler(mats[i]) : '0, 0, 0';
          values[i] = { pos: pos, rot: rot, x: pos.x, y: pos.y, z: pos.z };
        }
        return values;
      }
    }
    if (typeId === 0x12) {
      const raw = deinterleave(data, start, count, 4);
      if (!raw) return values;
      for (let i = 0; i < count; i++) values[i] = rotatedU32At(raw, i * 4);
      return values;
    }
    if (typeId === 3) {
      const raw = deinterleave(data, start, count, 4);
      if (!raw) return values;
      for (let i = 0; i < count; i++) {
        let be = 0, le = 0;
        try { be = rotateI32(raw.readUInt32BE(i * 4)); } catch (e) {}
        try { le = rotateI32(raw.readUInt32LE(i * 4)); } catch (e) {}
        if (Math.abs(be) <= 100000 && (Math.abs(le) > 100000 || Math.abs(be) < Math.abs(le) || Math.abs(be) <= 32)) values[i] = be;
        else values[i] = le;
      }
      return values;
    }
    if (typeId === 0x17) {
      for (let i = 0; i < count; i++) {
        const o = start + i * 8;
        if (o + 8 > data.length) break;
        values[i] = { min: data.readFloatLE(o), max: data.readFloatLE(o + 4) };
      }
      return values;
    }
    if (typeId === 0x15) {
      let off = start;
      for (let i = 0; i < count; i++) {
        if (off + 4 > data.length) break;
        const n = data.readUInt32LE(off);
        off += 4;
        const kps = [];
        for (let k = 0; k < n && off + 12 <= data.length; k++) {
          kps.push({ t: data.readFloatLE(off), v: data.readFloatLE(off + 4), e: data.readFloatLE(off + 8) });
          off += 12;
        }
        values[i] = kps;
      }
      return values;
    }
    if (typeId === 0x16) {
      let off = start;
      for (let i = 0; i < count; i++) {
        if (off + 4 > data.length) break;
        const n = data.readUInt32LE(off);
        off += 4;
        const kps = [];
        for (let k = 0; k < n && off + 20 <= data.length; k++) {
          kps.push({
            t: data.readFloatLE(off),
            r: data.readFloatLE(off + 4),
            g: data.readFloatLE(off + 8),
            b: data.readFloatLE(off + 12),
            e: data.readFloatLE(off + 16)
          });
          off += 20;
        }
        values[i] = kps;
      }
      return values;
    }
    if (typeId === 0x1a) {
      for (let i = 0; i < count; i++) {
        const o = start + i * 3;
        if (o + 3 > data.length) break;
        values[i] = [{ t: 0, r: data[o] / 255, g: data[o + 1] / 255, b: data[o + 2] / 255, e: 0 }];
      }
      return values;
    }
    if (typeId === 0x1d || typeId === 0x1b) {
      const out = [];
      let off = start;
      for (let i = 0; i < count; i++) {
        if (off + 4 > data.length) break;
        const len = data.readInt32LE(off);
        off += 4;
        if (len < 0 || off + len > data.length) break;
        out[i] = data.slice(off, off + len);
        off += len;
      }
      return out;
    }
    if (typeId === 0x1c) {
      const raw = deinterleave(data, start, count, 4);
      if (!raw) return values;
      for (let i = 0; i < count; i++) {
        const idx = rotatedU32At(raw, i * 4);
        values[i] = sharedStrings[idx] != null ? sharedStrings[idx] : idx;
      }
      return values;
    }
    if (typeId === 0x22 || typeId === 0x23) {
      return readStringArray(data, start, count);
    }
    if (typeId === 0x21 || typeId === 0x24 || typeId === 0x25 || typeId === 0x26) {
      const strs = readStringArray(data, start, count);
      if (strs && strs.length) return strs;
      let off = start;
      for (let i = 0; i < count && off < data.length; i++) {
        const srcType = data[off];
        off += 1;
        if (srcType === 1 || srcType === 2) {
          const s = readLenString(data, off);
          if (!s) break;
          values[i] = s.value;
          off = s.offset;
        } else {
          values[i] = '';
        }
      }
      return values;
    }
  } catch (e) {}
  return values;
}

function xmlTagValue(block, tag, name) {
  const re = new RegExp('<' + tag + ' name="' + name + '"[^>]*>([\\s\\S]*?)</' + tag + '>', 'i');
  const m = re.exec(block);
  return m ? decodeXml(m[1]).trim() : null;
}

function parseXmlNumberSeq(text) {
  const nums = String(text || '').trim().split(/\s+/).map(Number).filter(function (n) { return Number.isFinite(n); });
  const kps = [];
  for (let i = 0; i + 2 < nums.length; i += 3) kps.push({ t: nums[i], v: nums[i + 1], e: nums[i + 2] });
  return kps;
}

function parseXmlColorSeq(text) {
  const nums = String(text || '').trim().split(/\s+/).map(Number).filter(function (n) { return Number.isFinite(n); });
  const kps = [];
  for (let i = 0; i + 4 < nums.length; i += 5) {
    kps.push({ t: nums[i], r: nums[i + 1], g: nums[i + 2], b: nums[i + 3], e: nums[i + 4] });
  }
  return kps;
}

function parseXmlVec(text) {
  const inner = String(text || '');
  const x = /<X>([^<]+)<\/X>/i.exec(inner);
  const y = /<Y>([^<]+)<\/Y>/i.exec(inner);
  const z = /<Z>([^<]+)<\/Z>/i.exec(inner);
  if (x || y) return { x: Number(x && x[1]) || 0, y: Number(y && y[1]) || 0, z: Number(z && z[1]) || 0 };
  const parts = inner.replace(/<[^>]+>/g, ' ').trim().split(/[\s,]+/).map(Number);
  return { x: parts[0] || 0, y: parts[1] || 0, z: parts[2] || 0 };
}

function parseXmlRange(text) {
  const inner = String(text || '');
  const min = /<(?:Min|min)>([^<]+)<\//i.exec(inner);
  const max = /<(?:Max|max)>([^<]+)<\//i.exec(inner);
  if (min || max) return { min: Number(min && min[1]) || 0, max: Number(max && max[1]) || 0 };
  const parts = inner.replace(/<[^>]+>/g, ' ').trim().split(/[\s,]+/).map(Number);
  return { min: parts[0] || 0, max: parts[1] != null ? parts[1] : parts[0] || 0 };
}

function parseXmlPropsInto(inst, chunk) {
  if (!chunk || !inst) return;
  const name = xmlTagValue(chunk, 'string', 'Name');
  if (name) {
    inst.name = name;
    inst.props.Name = name;
  }
  const pairs = [
    ['Content', 'Texture'], ['string', 'Texture'],
    ['float', 'Brightness'], ['float', 'Drag'], ['float', 'Rate'],
    ['float', 'LightEmission'], ['float', 'LightInfluence'],
    ['float', 'ShapePartial'], ['float', 'ZOffset'],
    ['token', 'EmissionDirection'], ['token', 'Orientation'],
    ['token', 'Shape'], ['token', 'ShapeInOut'],
    ['token', 'FlipbookLayout'], ['token', 'FlipbookMode'],
    ['int', 'FlipbookSizeX'], ['int', 'FlipbookSizeY'],
    ['BinaryString', 'AttributesSerialize'],
    ['ColorSequence', 'Color'], ['NumberSequence', 'Size'],
    ['NumberSequence', 'Transparency'], ['NumberSequence', 'Squash'],
    ['NumberRange', 'Lifetime'], ['NumberRange', 'Speed'],
    ['NumberRange', 'RotSpeed'], ['NumberRange', 'Rotation'],
    ['NumberRange', 'FlipbookFramerate'],
    ['Vector3', 'Acceleration'], ['Vector2', 'SpreadAngle'],
    ['Vector3', 'Size'], ['Vector3', 'Position']
  ];
  pairs.forEach(function (pair) {
    const raw = xmlTagValue(chunk, pair[0], pair[1]);
    if (raw == null || raw === '') return;
    if (pair[0] === 'float' || pair[0] === 'int') inst.props[pair[1]] = Number(raw);
    else if (pair[0] === 'BinaryString') {
      try { inst.props[pair[1]] = Buffer.from(String(raw).replace(/\s+/g, ''), 'base64'); }
      catch (e) { inst.props[pair[1]] = raw; }
    }
    else if (pair[0] === 'ColorSequence') inst.props[pair[1]] = parseXmlColorSeq(raw);
    else if (pair[0] === 'NumberSequence') inst.props[pair[1]] = parseXmlNumberSeq(raw);
    else if (pair[0] === 'NumberRange') inst.props[pair[1]] = parseXmlRange(raw);
    else if (pair[0] === 'Vector3' || pair[0] === 'Vector2') inst.props[pair[1]] = parseXmlVec(raw);
    else inst.props[pair[1]] = raw;
  });
  if (/name="LockedToPart"[^>]*>\s*true/i.test(chunk)) inst.props.LockedToPart = true;
}

function parseXmlItemAt(text, start) {
  const gt = text.indexOf('>', start);
  if (gt === -1) return { inst: null, end: start };
  const header = text.slice(start, gt + 1);
  const classM = /class="([^"]+)"/i.exec(header);
  const refM = /referent="([^"]+)"/i.exec(header);
  const inst = {
    className: (classM && classM[1]) || 'Instance',
    referent: refM && refM[1],
    name: (classM && classM[1]) || 'Instance',
    props: {},
    children: [],
    parent: null
  };
  if (/\/>\s*$/.test(header)) return { inst: inst, end: gt + 1 };
  let i = gt + 1;
  while (i < text.length) {
    const open = text.indexOf('<Item', i);
    const close = text.indexOf('</Item>', i);
    if (close === -1) {
      parseXmlPropsInto(inst, text.slice(i));
      return { inst: inst, end: text.length };
    }
    if (open !== -1 && open < close) {
      parseXmlPropsInto(inst, text.slice(i, open));
      const child = parseXmlItemAt(text, open);
      if (child.inst) {
        child.inst.parent = inst;
        inst.children.push(child.inst);
      }
      i = child.end;
    } else {
      parseXmlPropsInto(inst, text.slice(i, close));
      return { inst: inst, end: close + 7 };
    }
  }
  return { inst: inst, end: i };
}

function parseXmlTree(text) {
  const roots = [];
  let i = 0;
  while (i < text.length) {
    const start = text.indexOf('<Item', i);
    if (start === -1) break;
    const parsed = parseXmlItemAt(text, start);
    if (parsed.inst) roots.push(parsed.inst);
    i = parsed.end;
  }
  return roots;
}

function color3ToRgb(v) {
  if (v == null) return '255, 255, 255';
  if (Array.isArray(v)) {
    const first = v[0];
    if (first && typeof first === 'object') return color3ToRgb(first);
  }
  if (typeof v === 'string') {
    const n = v.split(/[,\s]+/).map(Number).filter(function (x) { return Number.isFinite(x); });
    if (n.length >= 3) {
      const scale = (n[0] <= 1 && n[1] <= 1 && n[2] <= 1) ? 255 : 1;
      return Math.round(n[0] * scale) + ', ' + Math.round(n[1] * scale) + ', ' + Math.round(n[2] * scale);
    }
  }
  const r = v.r != null ? v.r : (v.R != null ? v.R : 1);
  const g = v.g != null ? v.g : (v.G != null ? v.G : 1);
  const b = v.b != null ? v.b : (v.B != null ? v.B : 1);
  const scale = (r <= 1 && g <= 1 && b <= 1) ? 255 : 1;
  return Math.round(r * scale) + ', ' + Math.round(g * scale) + ', ' + Math.round(b * scale);
}

function sizeScalar(v) {
  let nums = [];
  if (v && typeof v === 'object' && !Array.isArray(v)) {
    nums = [v.x, v.y, v.z, v.X, v.Y, v.Z].map(Number).filter(function (n) { return Number.isFinite(n); });
  } else if (typeof v === 'string') {
    nums = v.split(/[,\s]+/).map(Number).filter(function (n) { return Number.isFinite(n); });
  } else if (typeof v === 'number' && Number.isFinite(v)) {
    nums = [v];
  }
  if (!nums.length) return 1;
  const m = Math.max.apply(null, nums.map(function (n) { return Math.abs(n); }));
  return m > 0 ? Number(m.toFixed(3)) : 1;
}

function localOffsetOnly(inst) {
  let cur = inst;
  let guard = 0;
  const seen = new Set();
  while (cur && guard++ < 64 && !seen.has(cur)) {
    seen.add(cur);
    const cn = String(cur.className || '');
    const props = cur.props || {};
    if (cn === 'Attachment') {
      if (props.Position) return vec3FromAny(props.Position);
      if (props.CFrame && props.CFrame.pos) return vec3FromAny(props.CFrame.pos);
    }
    cur = cur.parent;
  }
  return '0, 0, 0';
}

function attachOffset(inst) {
  return localOffsetOnly(inst);
}

function clampLocalPos(str) {
  const n = String(str || '0, 0, 0').split(/[,\s]+/).map(Number);
  const x = n[0] || 0, y = n[1] || 0, z = n[2] || 0;
  const mag = Math.hypot(x, y, z);
  if (!Number.isFinite(mag) || mag > 12) return '0, 0, 0';
  return (Math.abs(x) < 1e-4 ? 0 : Number(x.toFixed(3))) + ', '
    + (Math.abs(y) < 1e-4 ? 0 : Number(y.toFixed(3))) + ', '
    + (Math.abs(z) < 1e-4 ? 0 : Number(z.toFixed(3)));
}

function rotFromAny(v) {
  if (v == null) return '0, 0, 0';
  if (typeof v === 'object' && v.x != null) return vec3FromAny(v);
  return vec3FromAny(v);
}

function meshAttrs(inst) {
  const props = (inst && inst.props) || {};
  const parent = inst && inst.parent;
  return Object.assign(
    {},
    parseAttributesBlob(props.AttributesSerialize),
    parseAttributesBlob(parent && parent.props && parent.props.AttributesSerialize)
  );
}

function meshFromInstRaw(inst) {
  const props = (inst && inst.props) || {};
  const parent = inst && inst.parent;
  const parentProps = (parent && parent.props) || {};
  const attrs = meshAttrs(inst);
  const id = textureIdFrom(
    props.MeshId || props.MeshID || props.MeshId0 ||
    parentProps.MeshId || parentProps.MeshID ||
    props.Mesh || props.meshId
  );
  const tex = textureIdFrom(props.TextureId || props.TextureID || props.Texture);
  const sizeSrc = props.Scale || props.Size || parentProps.Size || parentProps.Scale || 1;
  let startScale = attrs.Scale_Start != null ? Number(attrs.Scale_Start) : (attrs.MinSize != null ? Number(attrs.MinSize) : NaN);
  let endScale = attrs.Scale_End != null ? Number(attrs.Scale_End) : (attrs.MaxSize != null ? Number(attrs.MaxSize) : NaN);
  if (!Number.isFinite(startScale) || startScale <= 0) {
    const raw = sizeScalar(props.Scale || parentProps.Scale || 1);
    startScale = raw > 8 ? 1 : raw;
  }
  if (!Number.isFinite(endScale) || endScale <= 0) endScale = startScale;
  if (startScale > 12) startScale = 1;
  if (endScale > 12) endScale = startScale;
  const pos = clampLocalPos(attrs.OffsetPosition ? vec3FromAny(attrs.OffsetPosition) : localOffsetOnly(inst));
  const altPos = clampLocalPos(attrs.Offset_End ? vec3FromAny(attrs.Offset_End) : pos);
  const rot = rotFromAny(attrs.OffsetRotation || '0, 0, 0');
  const startOp = jjsOpacity(attrs.Mesh_StartTransparency != null ? attrs.Mesh_StartTransparency : attrs.Part_Transparency_Start);
  const endOp = jjsOpacity(attrs.Mesh_EndTransparency != null ? attrs.Mesh_EndTransparency : (attrs.Part_Transparency_End != null ? attrs.Part_Transparency_End : startOp));
  const time = Number(attrs.Duration || attrs.EffectDuration || attrs.EmitDuration || attrs.Size_Duration);
  const col = color3ToRgb(props.Color3uint8 || props.VertexColor || props.Color || parentProps.Color || parentProps.Color3uint8);
  return {
    K_NAME: 'VISUAL',
    EFFECT: 'Mesh',
    AMOUNT: id || 1,
    TEXTURE: tex || 0,
    SIZE: Number.isFinite(startScale) && startScale > 0 ? Number(startScale.toFixed(3)) : 1,
    'ALT SIZE': Number.isFinite(endScale) && endScale > 0 ? Number(endScale.toFixed(3)) : (Number.isFinite(startScale) ? Number(startScale.toFixed(3)) : 1),
    'SIZE 2': '-1, -1, -1',
    'ALT SIZE 2': '-1, -1, -1',
    OPACITY: startOp,
    'ALT OPACITY': endOp,
    POSITION: pos,
    'ALT POSITION': altPos,
    ROTATION: rot,
    'ALT ROTATION': rot,
    COLOR: col,
    'ALT COLOR': col,
    TIME: Number.isFinite(time) && time > 0 ? Number(time.toFixed(3)) : 0.3,
    'BODY PART': bodyPartFromName((partForBody(inst) && partForBody(inst).name) || (inst && inst.name)),
    'RUN ON SERVER': false,
    'CLIENT SIDED': false,
    'CANCEL ON INTERRUPT': false,
    'RELATIVE FROM BRANCH': false,
    'LAST HIT': -1,
    NAME: (inst && inst.name) || 'Mesh'
  };
}

function findPartCf(inst) {
  if (!inst) return null;
  const cn = String(inst.className || '');
  const nm = String(inst.name || '');
  if (/^(Start|End)$/i.test(nm) && cn === 'MeshPart') return null;
  if (/^(Part|BasePart)$/i.test(cn) && inst.props && inst.props.CFrame && inst.props.CFrame.pos) {
    return inst.props.CFrame;
  }
  return null;
}

function collectHostParts(inst, out, depth) {
  if (!inst || depth > 6) return;
  if (findPartCf(inst)) out.push(inst);
  (inst.children || []).forEach(function (c) { collectHostParts(c, out, depth + 1); });
}

function ancestorPartCf(inst, worldPos) {
  let cur = inst;
  let guard = 0;
  let root = inst;
  while (cur && guard++ < 24) {
    const direct = findPartCf(cur);
    if (direct) return direct;
    root = cur;
    cur = cur.parent;
  }
  const hosts = [];
  collectHostParts(root, hosts, 0);
  if (!hosts.length) return null;
  const named = hosts.find(function (c) { return String(c.name) === 'Main'; });
  if (named) return findPartCf(named);
  if (!worldPos) return findPartCf(hosts[0]);
  let best = hosts[0], bestD = Infinity;
  hosts.forEach(function (h) {
    const p = h.props.CFrame.pos;
    const d = Math.hypot((worldPos.x || 0) - p.x, (worldPos.y || 0) - p.y, (worldPos.z || 0) - p.z);
    if (d < bestD) { bestD = d; best = h; }
  });
  return findPartCf(best);
}

function rangeVal(v) {
  if (v == null) return NaN;
  if (typeof v === 'number') return v;
  if (typeof v === 'object') {
    const a = Number(v.min), b = Number(v.max);
    if (Number.isFinite(a) && Number.isFinite(b)) return (a + b) / 2;
    if (Number.isFinite(a)) return a;
    if (Number.isFinite(b)) return b;
  }
  return Number(v);
}

function jjsOpacity(t) {
  const n = rangeVal(t);
  if (!Number.isFinite(n) || n < 0) return 0;
  // JJS OPACITY is Roblox Transparency: 0 visible, 1 invisible.
  // Forge/studio templates store 1 or 1.8+ just to hide the editor mesh.
  if (n >= 1) return 0;
  return Number(n.toFixed(3));
}

function opacityFromForge(t) {
  return jjsOpacity(t);
}

function relPos(world, origin) {
  if (!world) return '0, 0, 0';
  const ox = origin && origin.x != null ? origin.x : 0;
  const oy = origin && origin.y != null ? origin.y : 0;
  const oz = origin && origin.z != null ? origin.z : 0;
  const x = (world.x || 0) - ox;
  const y = (world.y || 0) - oy;
  const z = (world.z || 0) - oz;
  return Number(x.toFixed(3)) + ', ' + Number(y.toFixed(3)) + ', ' + Number(z.toFixed(3));
}

function sizeToScalar(sz) {
  if (!sz) return 1;
  const x = Math.abs(Number(sz.x) || 0), y = Math.abs(Number(sz.y) || 0), z = Math.abs(Number(sz.z) || 0);
  const vals = [x, y, z].filter(function (n) { return n > 0.0001 && n < 80; });
  const m = vals.length ? Math.max.apply(null, vals) : Math.max(x, y, z);
  if (!Number.isFinite(m) || m <= 0 || m > 80) return 1;
  return Number(m.toFixed(3));
}

function poseSane(p) {
  if (!p) return false;
  const x = Number(p.x), y = Number(p.y), z = Number(p.z);
  if (![x, y, z].every(Number.isFinite)) return false;
  return Math.abs(x) < 60 && Math.abs(y) < 60 && Math.abs(z) < 60;
}

function isMeshInst(inst) {
  return inst && /^(MeshPart|SpecialMesh|FileMesh)$/i.test(String(inst.className || ''));
}

function partHasMesh(inst) {
  if (!inst) return false;
  if (isMeshInst(inst)) return true;
  if (!/^(Part|MeshPart|WedgePart)$/i.test(String(inst.className || ''))) return false;
  return !!(inst.props && (inst.props.MeshId || inst.props.MeshID)) ||
    (inst.children || []).some(isMeshInst);
}

function meshIdOf(inst) {
  if (!inst) return '';
  const p = inst.props || {};
  let id = textureIdFrom(p.MeshId || p.MeshID || p.MeshId0);
  if (id) return String(id);
  const sm = (inst.children || []).find(isMeshInst);
  if (sm) return String(textureIdFrom((sm.props || {}).MeshId || (sm.props || {}).MeshID) || '');
  return '';
}

function forgePair(model) {
  if (!model) return null;
  const kids = model.children || [];
  const namedStart = kids.find(function (c) { return /^(start|begin|from|meshstart)$/i.test(String(c.name || '')) && partHasMesh(c); });
  const namedEnd = kids.find(function (c) { return /^(end|finish|to|meshend)$/i.test(String(c.name || '')) && partHasMesh(c); });
  if (namedStart && namedEnd && namedStart !== namedEnd) return { start: namedStart, end: namedEnd };
  const meshes = kids.filter(partHasMesh);
  if (meshes.length === 2) return { start: meshes[0], end: meshes[1] };
  const byId = {};
  meshes.forEach(function (m) {
    const id = meshIdOf(m) || m.name || 'x';
    if (!byId[id]) byId[id] = [];
    byId[id].push(m);
  });
  const ids = Object.keys(byId).filter(function (k) { return byId[k].length >= 2; });
  if (ids.length === 1 && byId[ids[0]].length === 2) {
    const a = byId[ids[0]][0], b = byId[ids[0]][1];
    const sa = sizeToScalar((a.props || {}).size), sb = sizeToScalar((b.props || {}).size);
    return sa <= sb ? { start: a, end: b } : { start: b, end: a };
  }
  return null;
}

function meshFromForgeModel(model) {
  const pair = forgePair(model);
  if (!pair) return null;
  const start = pair.start, end = pair.end;
  const startMesh = isMeshInst(start) ? start : ((start.children || []).find(isMeshInst) || start);
  const endMesh = isMeshInst(end) ? end : ((end.children || []).find(isMeshInst) || end);
  const sp = Object.assign({}, startMesh.props || {}, start.props || {});
  const ep = Object.assign({}, endMesh.props || {}, end.props || {});
  const attrs = Object.assign({}, meshAttrs(model), meshAttrs(start), meshAttrs(end));
  const startCfGuess = (start.props && start.props.CFrame) || {};
  const originCf = ancestorPartCf(model.parent || model, poseSane(startCfGuess.pos) ? startCfGuess.pos : ((end.props && end.props.CFrame && end.props.CFrame.pos) || startCfGuess.pos));
  const origin = originCf && originCf.pos ? originCf.pos : { x: 0, y: 0, z: 0 };
  const startCf = sp.CFrame || start.props.CFrame || {};
  const endCf = ep.CFrame || end.props.CFrame || {};
  const id = textureIdFrom(sp.MeshId || ep.MeshId || sp.MeshID || ep.MeshID || meshIdOf(start) || meshIdOf(end));
  const decal = (start.children || []).concat(end.children || []).find(function (c) { return c && c.className === 'Decal'; });
  const tex = textureIdFrom((decal && decal.props && (decal.props.Texture || decal.props.TextureId)) || sp.TextureId || ep.TextureId || sp.TextureID || ep.TextureID);
  const startSize = sizeToScalar(sp.size || sp.Size);
  const endSize = sizeToScalar(ep.size || ep.Size);
  let time = rangeVal(attrs.EffectDuration);
  if (!Number.isFinite(time) || time <= 0) time = rangeVal(attrs.Duration);
  if (!Number.isFinite(time) || time <= 0) time = rangeVal(attrs.Size_Duration);
  if (!Number.isFinite(time) || time <= 0) time = 0.3;
  const startOp = opacityFromForge(attrs.Part_Transparency_Start != null ? attrs.Part_Transparency_Start : attrs.Mesh_StartTransparency);
  const endOp = opacityFromForge(attrs.Part_Transparency_End != null ? attrs.Part_Transparency_End : attrs.Mesh_EndTransparency);
  const startCol = color3ToRgb(sp.Color3uint8 || sp.Color || sp.VertexColor);
  const endCol = color3ToRgb(ep.Color3uint8 || ep.Color || ep.VertexColor || startCol);
  const mesh = {
    K_NAME: 'VISUAL',
    EFFECT: 'Mesh',
    AMOUNT: id || 1,
    TEXTURE: tex || 0,
    SIZE: startSize,
    'ALT SIZE': endSize,
    'SIZE 2': '-1, -1, -1',
    'ALT SIZE 2': '-1, -1, -1',
    OPACITY: startOp,
    'ALT OPACITY': endOp,
    POSITION: poseSane(startCf.pos) ? relPos(startCf.pos, origin) : (poseSane(endCf.pos) ? relPos(endCf.pos, origin) : '0, 0, 0'),
    'ALT POSITION': poseSane(endCf.pos) ? relPos(endCf.pos, origin) : (poseSane(startCf.pos) ? relPos(startCf.pos, origin) : '0, 0, 0'),
    ROTATION: rotFromAny(startCf.rot || '0, 0, 0'),
    'ALT ROTATION': rotFromAny(endCf.rot || startCf.rot || '0, 0, 0'),
    COLOR: startCol,
    'ALT COLOR': endCol,
    TIME: Number(time.toFixed(3)),
    'BODY PART': 'HumanoidRootPart',
    'RUN ON SERVER': false,
    'CLIENT SIDED': false,
    'CANCEL ON INTERRUPT': false,
    'RELATIVE FROM BRANCH': false,
    'LAST HIT': -1,
    NAME: (model && model.name && model.name !== 'Model') ? model.name : ((start.name || 'Mesh'))
  };
  if (Number(mesh.OPACITY) >= 1 && Number(mesh['ALT OPACITY']) >= 1) {
    mesh.OPACITY = 0;
    mesh['ALT OPACITY'] = 0;
  }
  if (!(Number(mesh.SIZE) > 0)) mesh.SIZE = 1;
  if (!(Number(mesh['ALT SIZE']) > 0)) mesh['ALT SIZE'] = mesh.SIZE;
  if (Number(mesh.SIZE) > 40) mesh.SIZE = 40;
  if (Number(mesh['ALT SIZE']) > 40) mesh['ALT SIZE'] = 40;
  return mesh;
}

function meshFromInst(inst) {
  if (!inst) return null;
  const host = (inst.className === 'SpecialMesh' || inst.className === 'FileMesh') ? (inst.parent || inst) : inst;
  const model = host && host.parent;
  const pair = model && forgePair(model);
  if (pair) {
    if (host === pair.start || inst === pair.start) return meshFromForgeModel(model);
    return null;
  }
  return meshFromInstRaw(inst);
}

function poseLooksCamera(inst) {
  const n = String((inst && inst.name) || '');
  const cn = String((inst && inst.className) || '');
  if (cn === 'Camera') return true;
  return /camera|cutscene|currentcamera|campart|fovpart|\bcam\d*\b/i.test(n);
}

function lineageLooksCamera(inst) {
  let cur = inst;
  let guard = 0;
  while (cur && guard++ < 24) {
    if (poseLooksCamera(cur)) return true;
    const cn = String(cur.className || '');
    const n = String(cur.name || '');
    if (cn === 'KeyframeSequence' && /camera|cutscene|\bcam\b/i.test(n)) return true;
    cur = cur.parent;
  }
  return false;
}

function keyframeIsCamera(inst) {
  if (!inst) return false;
  if (lineageLooksCamera(inst)) return true;
  const kids = inst.children || [];
  for (let i = 0; i < kids.length; i++) {
    if (poseLooksCamera(kids[i])) return true;
  }
  return false;
}

function instTime(inst, fallback) {
  const props = (inst && inst.props) || {};
  const cands = [props.Time, props.time, inst && inst.name];
  for (let i = 0; i < cands.length; i++) {
    const n = Number(cands[i]);
    if (Number.isFinite(n)) return n;
  }
  const parent = inst && inst.parent;
  if (parent && parent.props && Number.isFinite(Number(parent.props.Time))) return Number(parent.props.Time);
  return fallback;
}

function cframeFromInst(inst) {
  if (!inst) return null;
  const props = inst.props || {};
  const cf = props.CFrame || props.Value || props.CoordinateFrame;
  if (cf && typeof cf === 'object' && (cf.pos || cf.x != null)) return cf;
  if (props.Position) return { pos: props.Position, rot: props.Orientation || '0, 0, 0' };
  return null;
}

function cameraFromInst(inst, index) {
  const props = (inst && inst.props) || {};
  const cf = cframeFromInst(inst);
  let pos = vec3FromAny((cf && cf.pos) || props.Position || props.Value || '0, 0, 0');
  const rot = rotFromAny((cf && cf.rot) || props.Orientation || props.Rotation || '0, 0, 0');
  const bits = String(pos).split(/[,\s]+/).map(Number);
  const mag = Math.hypot(bits[0] || 0, bits[1] || 0, bits[2] || 0);
  if (bits.some(function (n) { return !Number.isFinite(n) || Math.abs(n) > 14; }) || mag > 12) pos = '0, 0, 0';
  const t = instTime(inst, NaN);
  return {
    K_NAME: 'VISUAL',
    EFFECT: 'Camera',
    POSITION: pos,
    'ALT POSITION': pos,
    ROTATION: rot,
    'ALT ROTATION': rot,
    COLOR: '255, 255, 255',
    'ALT COLOR': '255, 255, 255',
    TIME: 0.05,
    'EASING STYLE': 'Quad',
    'EASING DIRECTION': 'Out',
    'BODY PART': 'HumanoidRootPart',
    'RUN ON SERVER': false,
    'CLIENT SIDED': false,
    'CANCEL ON INTERRUPT': false,
    'RELATIVE FROM BRANCH': false,
    'LAST HIT': -1,
    NAME: (inst && inst.name) || ('Camera_' + (index + 1)),
    _time: Number.isFinite(t) ? t : (index * 0.08)
  };
}

function posKey(cam) {
  return String(cam && cam.POSITION || '') + '|' + String(cam && cam.ROTATION || '');
}

function normalizeKeyframeTimes(keys) {
  if (!keys || !keys.length) return keys;
  keys.forEach(function (k) {
    let t = Number(k._time);
    if (!Number.isFinite(t) || t < 0) t = 0;
    // Frame indexes leaked into Time (547, 648...). Seconds stay as-is.
    if (t > 40) t = t / 60;
    k._time = t;
  });
  return keys;
}

function posDist(a, b) {
  function xyz(s) {
    const n = String(s || '0, 0, 0').split(/[,\s]+/).map(Number);
    return [n[0] || 0, n[1] || 0, n[2] || 0];
  }
  const p = xyz(a), q = xyz(b);
  const dx = p[0] - q[0], dy = p[1] - q[1], dz = p[2] - q[2];
  return Math.sqrt(dx * dx + dy * dy + dz * dz);
}

function parseVec(s) {
  const n = String(s || '0, 0, 0').split(/[,\s]+/).map(Number);
  return [n[0] || 0, n[1] || 0, n[2] || 0];
}

function fmtVec(p) {
  return cleanNum(p[0]) + ', ' + cleanNum(p[1]) + ', ' + cleanNum(p[2]);
}

function smoothCameraKeys(list) {
  const pts = list.map(function (c) { return parseVec(c.POSITION); });
  const out = [];
  for (let i = 0; i < list.length; i++) {
    const win = [];
    for (let j = Math.max(0, i - 2); j <= Math.min(list.length - 1, i + 2); j++) {
      if (j === i) continue;
      win.push(pts[j]);
    }
    if (!win.length) { out.push(list[i]); continue; }
    const med = [0, 1, 2].map(function (axis) {
      const col = win.map(function (p) { return p[axis]; }).sort(function (a, b) { return a - b; });
      return col[Math.floor(col.length / 2)];
    });
    const cur = pts[i];
    const dx = cur[0] - med[0], dy = cur[1] - med[1], dz = cur[2] - med[2];
    const d = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (d > 10) continue;
    out.push(list[i]);
  }
  return out;
}

function isCinematicCam(cam) {
  const p = parseVec(cam && cam.POSITION);
  const r = parseVec(cam && cam.ROTATION);
  const mag = Math.hypot(p[0], p[1], p[2]);
  const pitch = r[0], yaw = r[1], roll = r[2];
  if (mag < 1.2 || mag > 12) return false;
  if (p[1] < -1.2 || p[1] > 6.5) return false;
  if (Math.abs(roll) > 25) return false;
  if (Math.abs(pitch) > 55 || Math.abs(yaw) > 85) return false;
  return true;
}

function buildCameraSequence(keys) {
  const raw = normalizeKeyframeTimes((keys || []).slice()).sort(function (a, b) { return a._time - b._time; });
  if (!raw.length) return [];
  const uniq = [];
  const seen = {};
  raw.forEach(function (cam) {
    const stamp = Number(cam._time || 0).toFixed(3) + '|' + posKey(cam);
    if (seen[stamp]) return;
    seen[stamp] = true;
    uniq.push(cam);
  });
  function isZeroPos(s) {
    return !s || String(s).replace(/\s+/g, '') === '0,0,0';
  }
  const cinematic = uniq.filter(isCinematicCam);
  const usable = smoothCameraKeys((cinematic.length >= 4 ? cinematic : uniq).filter(function (c) { return !isZeroPos(c.POSITION); }));
  const src = usable.length >= 2 ? usable : uniq;
  const MAX_SHOTS = 80;
  const runs = [];
  let run = [src[0]];
  for (let i = 1; i < src.length; i++) {
    const prev = src[i - 1];
    const cur = src[i];
    const dt = cur._time - prev._time;
    const jump = posDist(prev.POSITION, cur.POSITION);
    const speed = dt > 0.0001 ? jump / dt : (jump > 10 ? 999 : 0);
    const cut = jump > 10 && speed > 80;
    const pause = dt > 0.35;
    if (cut || pause) { runs.push(run); run = [cur]; }
    else run.push(cur);
  }
  runs.push(run);
  const merged = [];
  runs.forEach(function (r) {
    if (!merged.length) { merged.push(r); return; }
    const last = merged[merged.length - 1];
    const a = last[last.length - 1];
    const b = r[0];
    const span = (r[r.length - 1]._time - last[0]._time);
    if (span < 1.15 && posDist(a.POSITION, b.POSITION) < 2.2 && posDist(a.ROTATION, b.ROTATION) < 18) {
      last.push.apply(last, r);
    } else merged.push(r);
  });
  let chosen = merged;
  if (chosen.length > MAX_SHOTS) {
    const step = (chosen.length - 1) / (MAX_SHOTS - 1);
    const pick = [];
    for (let i = 0; i < MAX_SHOTS; i++) pick.push(chosen[Math.round(i * step)]);
    chosen = pick;
  }
  const out = [];
  function isZeroPos(s) {
    return !s || String(s).replace(/\s+/g, '') === '0,0,0';
  }
  function pushShot(start, end, ri) {
    if (!start) return;
    if (isZeroPos(start.POSITION) && (!end || isZeroPos(end.POSITION))) return;
    end = end || start;
    let dur = Number((end._time - start._time).toFixed(3));
    if (!Number.isFinite(dur) || dur < 0.05) dur = 0.12;
    if (dur > 1.25) dur = 1.25;
    dur = Number(dur.toFixed(2));
    out.push({
      K_NAME: 'VISUAL',
      EFFECT: 'Camera',
      POSITION: start.POSITION,
      'ALT POSITION': end.POSITION || start.POSITION,
      ROTATION: start.ROTATION,
      'ALT ROTATION': end.ROTATION || start.ROTATION,
      COLOR: start.COLOR || '255, 255, 255',
      'ALT COLOR': end['ALT COLOR'] || start['ALT COLOR'] || '255, 255, 255',
      TIME: dur,
      'EASING STYLE': dur > 0.12 ? 'Quad' : 'Linear',
      'EASING DIRECTION': 'Out',
      'BODY PART': 'HumanoidRootPart',
      'RUN ON SERVER': false,
      'CLIENT SIDED': false,
      'CANCEL ON INTERRUPT': false,
      'RELATIVE FROM BRANCH': false,
      'LAST HIT': -1,
      NAME: start.NAME || ('Camera_' + (ri + 1)),
      _time: start._time,
      _endTime: end._time,
      _waitAfter: dur
    });
  }
  chosen.forEach(function (r, ri) {
    const pts = r.filter(function (c) { return !isZeroPos(c.POSITION); });
    if (!pts.length) return;
    const STEP = 0.32;
    let i0 = 0;
    for (let i = 1; i < pts.length; i++) {
      if (pts[i]._time - pts[i0]._time >= STEP || i === pts.length - 1) {
        pushShot(pts[i0], pts[i], ri);
        i0 = i;
      }
    }
    if (out.length && out[out.length - 1]._endTime !== pts[pts.length - 1]._time) {
      pushShot(pts[pts.length - 1], pts[pts.length - 1], ri);
    }
  });
  for (let i = 0; i < out.length - 1; i++) {
    const gap = out[i + 1]._time - out[i]._endTime;
    if (gap > 0.2) out[i]._waitAfter = Number((out[i].TIME + gap).toFixed(2));
  }
  return out;
}

function pickCameraPose(inst) {
  const kids = (inst && inst.children) || [];
  const prefer = kids.find(function (c) {
    return /campart|currentcamera|^camera$|\bcam\d+\b/i.test(String(c.name || ''));
  });
  if (prefer) return prefer;
  const named = kids.find(function (c) { return poseLooksCamera(c); });
  if (named) return named;
  let found = null;
  kids.forEach(function (c) {
    if (found) return;
    if (String(c.className || '') === 'Pose') {
      const inner = pickCameraPose(c);
      if (inner && inner !== c) found = inner;
    }
  });
  return found || inst;
}

function cfPos(cf) {
  if (!cf) return { x: 0, y: 0, z: 0 };
  if (cf.pos) return { x: Number(cf.pos.x) || 0, y: Number(cf.pos.y) || 0, z: Number(cf.pos.z) || 0 };
  return { x: Number(cf.x) || 0, y: Number(cf.y) || 0, z: Number(cf.z) || 0 };
}
function cfRot(cf) {
  if (!cf) return '0, 0, 0';
  if (cf.rot) return String(cf.rot);
  return '0, 0, 0';
}
function cfVecStr(cf) {
  const p = cfPos(cf);
  return [p.x, p.y, p.z].map(function (n) { return Number(n).toFixed(3).replace(/\.?0+$/, '') || '0'; }).join(', ');
}
function cfDist(a, b) {
  const A = cfPos(a), B = cfPos(b);
  return Math.hypot(A.x - B.x, A.y - B.y, A.z - B.z);
}
function parseMoonJson(raw) {
  const s = String(raw || '').trim();
  if (!s || s.charAt(0) !== '{') return null;
  try { return JSON.parse(s); } catch (e) { return null; }
}
function findNamed(inst, name) {
  if (!inst) return null;
  const kids = inst.children || [];
  for (let i = 0; i < kids.length; i++) if (String(kids[i].name) === name) return kids[i];
  return null;
}
function walkFind(inst, pred, acc) {
  acc = acc || [];
  if (!inst) return acc;
  if (pred(inst)) acc.push(inst);
  (inst.children || []).forEach(function (c) { walkFind(c, pred, acc); });
  return acc;
}
function cframeFromMoonFolder(folder) {
  if (!folder) return null;
  if (folder.className === 'CFrameValue' && folder.props && folder.props.Value) return folder.props.Value;
  const values = findNamed(folder, 'Values') || folder;
  const kids = values.children || [];
  for (let i = 0; i < kids.length; i++) {
    if (kids[i].className === 'CFrameValue' && kids[i].props && kids[i].props.Value) return kids[i].props.Value;
  }
  for (let i = 0; i < kids.length; i++) {
    const deep = cframeFromMoonFolder(kids[i]);
    if (deep) return deep;
  }
  return null;
}
function moonKeyframesFromJoint(joint) {
  const kfRoot = findNamed(joint, '_keyframes') || joint;
  const keys = [];
  (kfRoot.children || []).forEach(function (folder) {
    if (!/^\d+$/.test(String(folder.name || ''))) return;
    const cf = cframeFromMoonFolder(folder);
    if (!cf) return;
    const p = cfPos(cf);
    if (!Number.isFinite(p.x) || !Number.isFinite(p.y) || !Number.isFinite(p.z)) return;
    if (Math.max(Math.abs(p.x), Math.abs(p.y), Math.abs(p.z)) > 180) return;
    if (!p.x && !p.y && !p.z && String(cfRot(cf)).replace(/\s/g, '') === '0,0,0') return;
    keys.push({ frame: Number(folder.name), cf: cf, time: Number(folder.name) / 60 });
  });
  keys.sort(function (a, b) { return a.frame - b.frame; });
  return keys;
}
function pickMoonRigJoint(track) {
  const joints = walkFind(track, function (i) { return i && i.name === '_joint'; });
  if (!joints.length) return track;
  joints.sort(function (a, b) {
    const ka = findNamed(a, '_keyframes');
    const kb = findNamed(b, '_keyframes');
    return ((kb && kb.children && kb.children.length) || 0) - ((ka && ka.children && ka.children.length) || 0);
  });
  const prefer = joints.find(function (j) {
    const hier = findNamed(j, '_hier');
    const v = hier && hier.props && String(hier.props.Value || '');
    return /RootPart|HumanoidRootPart|^Torso$|^Camera$/i.test(v) || !v;
  });
  return prefer || joints[0];
}
function downsampleMoonKeys(keys, maxN) {
  if (!keys.length) return [];
  if (keys.length <= maxN) return keys.slice();
  const out = [keys[0]];
  for (let i = 1; i < keys.length - 1; i++) {
    const prev = out[out.length - 1];
    const cur = keys[i];
    const dt = cur.time - prev.time;
    const dp = cfDist(prev.cf, cur.cf);
    if (dp > 90) continue;
    if (dt >= 0.38 || dp >= 3.2) out.push(cur);
  }
  out.push(keys[keys.length - 1]);
  if (out.length <= maxN) return out;
  const step = (out.length - 1) / (maxN - 1);
  const pick = [];
  for (let i = 0; i < maxN; i++) pick.push(out[Math.round(i * step)]);
  return pick.filter(function (k, i, arr) { return i === 0 || k !== arr[i - 1]; });
}
function moonShotsFromKeys(keys, name) {
  const pts = downsampleMoonKeys(keys, 16);
  const shots = [];
  for (let i = 0; i < pts.length; i++) {
    const cur = pts[i];
    const next = pts[i + 1] || cur;
    let dur = Number((next.time - cur.time).toFixed(2));
    if (!Number.isFinite(dur) || dur < 0.08) dur = 0.12;
    if (dur > 1.8) dur = 1.8;
    shots.push({
      K_NAME: 'VISUAL',
      EFFECT: 'Camera',
      POSITION: cfVecStr(cur.cf),
      'ALT POSITION': cfVecStr(next.cf),
      ROTATION: cfRot(cur.cf),
      'ALT ROTATION': cfRot(next.cf),
      COLOR: '255, 255, 255',
      'ALT COLOR': '255, 255, 255',
      TIME: dur,
      'EASING STYLE': dur > 0.18 ? 'Quad' : 'Linear',
      'EASING DIRECTION': 'Out',
      'BODY PART': 'HumanoidRootPart',
      'RUN ON SERVER': false,
      'CLIENT SIDED': false,
      'CANCEL ON INTERRUPT': false,
      'RELATIVE FROM BRANCH': false,
      'LAST HIT': -1,
      NAME: name || ('Cam_' + (i + 1)),
      _time: cur.time,
      _endTime: next.time,
      _waitAfter: Number((next.time - cur.time).toFixed(2)) || dur
    });
  }
  return shots;
}
function camerasFromMoonAnimator(instances) {
  const list = instances || [];
  const saves = list.filter(function (inst) {
    const par = inst && inst.parent;
    const pname = par && String(par.name || '');
    return inst && inst.className === 'StringValue' && /MoonAnimator2Saves/i.test(pname);
  });
  if (!saves.length) return [];
  function scoreSave(sv) {
    const json = parseMoonJson(sv.props && sv.props.Value);
    if (!json || !json.Items) return -1;
    const names = String(sv.name || '');
    let n = Number((json.Information && json.Information.Length) || 0);
    if (/lastmoon|lastcutscene/i.test(names)) n += 10000;
    if (json.Items.some(function (it) { return it.Path && it.Path.ItemType === 'Camera'; })) n += 4000;
    if (json.Items.some(function (it) { return /camera/i.test(((it.Path && it.Path.InstanceNames) || []).slice(-1)[0] || ''); })) n += 2000;
    return n;
  }
  saves.sort(function (a, b) { return scoreSave(b) - scoreSave(a); });
  const save = saves[0];
  if (scoreSave(save) < 0) return [];
  const json = parseMoonJson(save.props && save.props.Value);
  const items = json.Items || [];
  function itemLast(it) {
    return ((it.Path && it.Path.InstanceNames) || []).slice(-1)[0] || '';
  }
  let rigIdx = items.findIndex(function (it) { return /camerarig|humanoidcamera/i.test(itemLast(it)); });
  if (rigIdx < 0) {
    rigIdx = items.findIndex(function (it) {
      return it.Path && it.Path.ItemType === 'Rig' && /cam/i.test(itemLast(it));
    });
  }
  if (rigIdx < 0) {
    rigIdx = items.findIndex(function (it) {
      return it.Path && it.Path.ItemType === 'Camera' && !/CurrentCamera/i.test(itemLast(it));
    });
  }
  if (rigIdx < 0) return [];
  const track = findNamed(save, String(rigIdx + 1));
  if (!track) return [];
  const joint = pickMoonRigJoint(track);
  const keys = moonKeyframesFromJoint(joint);
  if (keys.length < 2) return [];
  return moonShotsFromKeys(keys, save.name || 'Camera');
}

function camerasFromInstancePool(instances) {
  const list = instances || [];
  const moon = camerasFromMoonAnimator(list);
  if (moon && moon.length) return moon;
  const named = list.filter(function (inst) {
    return String(inst.className || '') === 'KeyframeSequence' && /^(camera|cameranew|cutscene)$/i.test(String(inst.name || ''));
  });
  named.sort(function (a, b) {
    const score = function (s) {
      let n = 0;
      function w(x) { if (x && x.className === 'Keyframe') n++; (x.children || []).forEach(w); }
      w(s);
      return n;
    };
    const prefer = function (s) { return /^camera$/i.test(s.name || '') ? 10000 : 0; };
    return (prefer(b) + score(b)) - (prefer(a) + score(a));
  });
  const tracks = named.slice(0, 1);
  const keys = [];
  function takeKeyframe(inst) {
    const cn = String(inst.className || '');
    if (cn === 'Keyframe') {
      const use = pickCameraPose(inst);
      const cam = cameraFromInst(use, keys.length);
      cam._time = instTime(inst, cam._time);
      cam.NAME = (use && use.name) || inst.name || cam.NAME;
      keys.push(cam);
    } else if (cn === 'CFrameValue' && /campart|camera|^0$|currentcamera/i.test(String(inst.name || ''))) {
      if (lineageLooksCamera(inst) || true) keys.push(cameraFromInst(inst, keys.length));
    } else if (cn === 'Camera') {
      keys.push(cameraFromInst(inst, keys.length));
    } else if (cn === 'Pose' && /campart/i.test(String(inst.name || ''))) {
      const cam = cameraFromInst(inst, keys.length);
      cam._time = instTime(inst.parent || inst, cam._time);
      keys.push(cam);
    }
    (inst.children || []).forEach(takeKeyframe);
  }
  if (tracks.length) {
    tracks.forEach(takeKeyframe);
  } else {
    list.forEach(function (inst) {
      if (String(inst.className || '') === 'Pose' && /campart/i.test(String(inst.name || ''))) {
        const cam = cameraFromInst(inst, keys.length);
        cam._time = instTime(inst.parent || inst, cam._time);
        cam.NAME = inst.name;
        keys.push(cam);
      }
    });
  }
  return buildCameraSequence(keys);
}

function soundFromInst(inst) {
  const props = (inst && inst.props) || {};
  const id = textureIdFrom(props.SoundId || props.SoundId0 || props.AssetId);
  return {
    K_NAME: 'SFX',
    ID: id || 0,
    SPEED: Number.isFinite(Number(props.PlaybackSpeed)) ? Number(props.PlaybackSpeed) : 1,
    VOLUME: Number.isFinite(Number(props.Volume)) ? Number(props.Volume) : 0.5,
    START: 0,
    END: 500,
    'FADE IN': 0,
    'FADE OUT': 0,
    CANCEL: false,
    GLOBAL: !!props.Looped,
    'CLIENT SIDED': false,
    'LAST HIT': -1,
    'PROJECTILE TAG': '',
    NAME: (inst && inst.name) || 'Sound'
  };
}

function beamFromInst(inst) {
  const props = (inst && inst.props) || {};
  return {
    kind: 'BEAM',
    name: (inst && inst.name) || 'Beam',
    texture: textureIdFrom(props.Texture),
    color: color3ToRgb(props.Color),
    width0: Number(props.Width0) || 0,
    width1: Number(props.Width1) || 0
  };
}

function collectXmlEmitters(roots) {
  const emitters = [];
  function walk(inst) {
    if (!inst) return;
    const cn = String(inst.className || '');
    if (cn === 'ParticleEmitter') {
      const extraA = Object.assign({}, particleExtrasFromInst(inst), { name: inst.name || inst.Name });
      const particle = particleFromProps(inst.props || {}, extraA);
      particle.NAME = inst.name || inst.Name || particle.NAME;
      inst.particle = particle;
      emitters.push({ particle: particle, inst: inst, host: topHostOf(inst), name: inst.name, kind: 'particle' });
    } else if (PARSE_OPTS.meshes && /^(MeshPart|SpecialMesh|FileMesh)$/i.test(cn)) {
      const mesh = meshFromInst(inst);
      if (mesh) {
        inst.mesh = mesh;
        emitters.push({ mesh: mesh, inst: inst.parent && forgePair(inst.parent) ? inst.parent : inst, host: topHostOf(inst), name: mesh.NAME || inst.name, kind: 'mesh' });
      }
    } else if (PARSE_OPTS.cameras && cn === 'Camera') {
      const camera = cameraFromInst(inst, emitters.length);
      inst.camera = camera;
      emitters.push({ camera: camera, inst: inst, host: topHostOf(inst), name: inst.name, kind: 'camera' });
    } else if (/^(Sound|SoundEffect)$/i.test(cn)) {
      const sound = soundFromInst(inst);
      inst.sound = sound;
      emitters.push({ sound: sound, inst: inst, host: topHostOf(inst), name: inst.name, kind: 'sound' });
    } else if (cn === 'Beam') {
      const beam = beamFromInst(inst);
      inst.beam = beam;
      emitters.push({ beam: beam, inst: inst, host: topHostOf(inst), name: inst.name, kind: 'beam' });
    }
    (inst.children || []).forEach(walk);
  }
  roots.forEach(walk);
  return emitters;
}

function parseXmlParticles(text) {
  const particles = [];
  const rows = [];
  const re = /<Item[^>]*class="ParticleEmitter"[^>]*>([\s\S]*?)<\/Item>/gi;
  let m;
  while ((m = re.exec(text))) {
    const block = m[1];
    const name = xmlTagValue(block, 'string', 'Name') || 'ParticleEmitter';
    const before = text.slice(Math.max(0, m.index - 2500), m.index);
    const partName = (before.match(/<string name="Name">([^<]*)<\/string>/g) || []).pop();
    const partMatch = partName ? />([^<]*)<\/string>/.exec(partName) : null;
    const props = {
      Name: name,
      Texture: xmlTagValue(block, 'Content', 'Texture') || xmlTagValue(block, 'string', 'Texture') || '',
      Brightness: Number(xmlTagValue(block, 'float', 'Brightness')),
      Drag: Number(xmlTagValue(block, 'float', 'Drag')),
      Rate: Number(xmlTagValue(block, 'float', 'Rate')),
      LightEmission: Number(xmlTagValue(block, 'float', 'LightEmission')),
      LightInfluence: Number(xmlTagValue(block, 'float', 'LightInfluence')),
      ShapePartial: Number(xmlTagValue(block, 'float', 'ShapePartial')),
      ZOffset: Number(xmlTagValue(block, 'float', 'ZOffset')),
      LockedToPart: /name="LockedToPart"[^>]*>\s*true/i.test(block),
      EmissionDirection: xmlTagValue(block, 'token', 'EmissionDirection'),
      Orientation: xmlTagValue(block, 'token', 'Orientation'),
      Shape: xmlTagValue(block, 'token', 'Shape'),
      ShapeInOut: xmlTagValue(block, 'token', 'ShapeInOut'),
      FlipbookLayout: xmlTagValue(block, 'token', 'FlipbookLayout'),
      FlipbookMode: xmlTagValue(block, 'token', 'FlipbookMode'),
      Color: parseXmlColorSeq(xmlTagValue(block, 'ColorSequence', 'Color') || ''),
      Size: parseXmlNumberSeq(xmlTagValue(block, 'NumberSequence', 'Size') || ''),
      Transparency: parseXmlNumberSeq(xmlTagValue(block, 'NumberSequence', 'Transparency') || ''),
      Squash: parseXmlNumberSeq(xmlTagValue(block, 'NumberSequence', 'Squash') || ''),
      Lifetime: parseXmlRange(xmlTagValue(block, 'NumberRange', 'Lifetime') || ''),
      Speed: parseXmlRange(xmlTagValue(block, 'NumberRange', 'Speed') || ''),
      RotSpeed: parseXmlRange(xmlTagValue(block, 'NumberRange', 'RotSpeed') || ''),
      Rotation: parseXmlRange(xmlTagValue(block, 'NumberRange', 'Rotation') || ''),
      FlipbookFramerate: parseXmlRange(xmlTagValue(block, 'NumberRange', 'FlipbookFramerate') || ''),
      Acceleration: parseXmlVec(xmlTagValue(block, 'Vector3', 'Acceleration') || ''),
      SpreadAngle: parseXmlVec(xmlTagValue(block, 'Vector2', 'SpreadAngle') || xmlTagValue(block, 'Vector3', 'SpreadAngle') || '')
    };
    if (!Number.isFinite(props.Brightness)) delete props.Brightness;
    if (!Number.isFinite(props.Drag)) delete props.Drag;
    if (!Number.isFinite(props.Rate)) delete props.Rate;
    const particle = particleFromProps(props, {
      body: bodyPartFromName(partMatch && partMatch[1]),
      partSize: vec3FromAny(props.Size),
      position: vec3FromAny(props.Position),
      name: name
    });
    particle.NAME = name || particle.NAME;
    particles.push(particle);
    rows.push({
      id: String(particle.TEXTURE || name),
      name: name,
      type: 'Particle',
      typeId: 0,
      prop: 'ParticleEmitter',
      className: 'ParticleEmitter',
      path: 'ParticleEmitter/' + name,
      particle: particle
    });
  }
  return { particles: particles, rows: rows };
}

function rotateI32(u) {
  u = u >>> 0;
  return ((u >>> 1) ^ (-(u & 1))) | 0;
}

function readInterleavedI32(buf, off, count) {
  const raw = deinterleave(buf, off, count, 4);
  if (!raw) return { values: [], offset: off };
  const values = [];
  for (let i = 0; i < count; i++) {
    let u = 0;
    try { u = raw.readUInt32BE(i * 4); } catch (e) { u = raw.readUInt32LE(i * 4); }
    values.push(rotateI32(u));
  }
  return { values: values, offset: off + count * 4 };
}

function readReferents(buf, off, count) {
  const r = readInterleavedI32(buf, off, count);
  let acc = 0;
  const refs = [];
  for (let i = 0; i < r.values.length; i++) {
    acc = (acc + r.values[i]) | 0;
    refs.push(acc);
  }
  return { refs: refs, offset: r.offset };
}

function isWorldService(name) {
  return /^(Workspace|Lighting|ReplicatedStorage|ReplicatedFirst|ServerStorage|ServerScriptService|StarterGui|StarterPack|StarterPlayer|StarterPlayerScripts|StarterCharacterScripts|Players|Teams|SoundService|Chat|Terrain|Camera|Game|DataModel|Lighting|CSGDictionaryService)$/i.test(String(name || ''));
}

function isFolderLike(inst) {
  if (!inst) return false;
  const n = String(inst.className || '');
  return /^(Folder|Configuration|Actor|WorldModel)$/i.test(n);
}

function isGroupContainer(inst) {
  if (!inst) return false;
  if (isWorldService(inst.className) || isWorldService(inst.name)) return false;
  if (isFolderLike(inst)) return true;
  return /^Model$/i.test(String(inst.className || ''));
}

function isSolidHost(inst) {
  if (!inst || isFolderLike(inst) || isWorldService(inst.className) || isWorldService(inst.name)) return false;
  const n = String(inst.className || '');
  if (/^(Attachment|ParticleEmitter)$/i.test(n)) return false;
  return /^(Model|Tool|Accessory|Accoutrement|Part|MeshPart|WedgePart|CornerWedgePart|TrussPart|UnionOperation|NegateOperation|VehicleSeat|Seat|SpawnLocation|BasePart)$/i.test(n) || !!n;
}

function topHostOf(inst) {
  if (!inst) return null;
  let cur = inst;
  let guard = 0;
  const seen = new Set();
  while (cur && (cur.className === 'Attachment' || cur.className === 'ParticleEmitter') && cur.parent && guard++ < 64 && !seen.has(cur)) {
    seen.add(cur);
    cur = cur.parent;
  }
  while (cur && isFolderLike(cur) && cur.parent && !isWorldService(cur.parent.className) && guard++ < 64 && !seen.has(cur)) {
    seen.add(cur);
    cur = cur.parent;
  }
  if (!isSolidHost(cur)) {
    let p = inst;
    while (p && guard++ < 64 && !seen.has(p)) {
      seen.add(p);
      if (isSolidHost(p)) return p;
      p = p.parent;
    }
    return inst;
  }
  let last = cur;
  while (last.parent && isSolidHost(last.parent) && !isFolderLike(last.parent) && !isWorldService(last.parent.className) && !isWorldService(last.parent.name) && guard++ < 64 && !seen.has(last.parent)) {
    seen.add(last);
    last = last.parent;
  }
  return last;
}

function instKey(inst) {
  if (!inst) return 'none';
  if (inst.ref != null) return 'r:' + inst.ref;
  if (inst.referent) return 'x:' + inst.referent;
  return 'n:' + String(inst.className || '') + ':' + String(inst.name || '');
}

function folderPathFromRoot(inst) {
  const folders = [];
  let p = inst;
  let guard = 0;
  const seen = new Set();
  while (p && guard++ < 64 && !seen.has(p)) {
    seen.add(p);
    if (isWorldService(p.className) || isWorldService(p.name)) break;
    if (isGroupContainer(p)) folders.push(p);
    p = p.parent;
  }
  folders.reverse();
  return folders;
}

function collectNodeParticles(node) {
  const out = [];
  (node.particles || []).forEach(function (p) { out.push(p); });
  (node.children || []).forEach(function (c) {
    collectNodeParticles(c).forEach(function (p) { out.push(p); });
  });
  return out;
}

function unwrapSingleFolders(node) {
  if (!node || !node.children) return node;
  function folderKids(n) {
    return (n.children || []).filter(function (c) { return c.type === 'ParticleFolder'; });
  }
  while (
    node.children.length === 1 &&
    node.children[0].type === 'ParticleFolder' &&
    folderKids(node.children[0]).length >= 1
  ) {
    node.children = node.children[0].children || [];
  }
  if (
    node.children.length === 1 &&
    node.children[0].type === 'ParticleFolder' &&
    folderKids(node.children[0]).length === 0
  ) {
    node.children = node.children[0].children || [];
  }
  return node;
}

function stampTree(node, counter) {
  node.id = String(counter.n++);
  (node.children || []).forEach(function (c) {
    c.parentId = node.id;
    stampTree(c, counter);
  });
}

function indexTree(node, map) {
  map[node.id] = node;
  (node.children || []).forEach(function (c) { indexTree(c, map); });
}

function buildParticleTree(emitters) {
  const root = {
    id: 'root',
    name: 'Root',
    className: 'Folder',
    type: 'ParticleFolder',
    children: [],
    particles: []
  };
  const seen = new Map();
  function childOf(parent, inst, type) {
    const key = parent.id + '|' + type + '|' + instKey(inst);
    if (seen.has(key)) return seen.get(key);
    const node = {
      name: (inst && (inst.name || inst.className)) || (type === 'ParticleFolder' ? 'Folder' : 'Model'),
      className: (inst && inst.className) || (type === 'ParticleFolder' ? 'Folder' : 'Model'),
      type: type,
      typeId: 0,
      path: [inst && inst.className, inst && inst.name].filter(Boolean).join('/'),
      children: [],
      particles: [],
      meshes: [],
      cameras: [],
      sounds: [],
      beams: []
    };
    parent.children.push(node);
    seen.set(key, node);
    return node;
  }
  (emitters || []).forEach(function (row) {
    const inst = row.inst;
    const boxes = folderPathFromRoot(inst);
    let node = root;
    boxes.forEach(function (folder) {
      node = childOf(node, folder, 'ParticleFolder');
    });
    const part = partForBody(inst);
    const inner = boxes.length ? boxes[boxes.length - 1] : null;
    function attach(target) {
      if (!target.particles) target.particles = [];
      if (!target.meshes) target.meshes = [];
      if (!target.cameras) target.cameras = [];
      if (!target.sounds) target.sounds = [];
      if (!target.beams) target.beams = [];
      if (row.particle) target.particles.push(row.particle);
      if (row.mesh) target.meshes.push(row.mesh);
      if (row.camera) target.cameras.push(row.camera);
      if (row.sound) target.sounds.push(row.sound);
      if (row.beam) target.beams.push(row.beam);
    }
    if (part && (!inner || instKey(part) !== instKey(inner))) {
      attach(childOf(node, part, 'ParticleHost'));
    } else {
      attach(node);
    }
  });
  unwrapSingleFolders(root);
  function rollup(node) {
    (node.children || []).forEach(rollup);
    const own = (node.particles || []).length + (node.meshes || []).length + (node.cameras || []).length + (node.sounds || []).length + (node.beams || []).length;
    let child = 0;
    (node.children || []).forEach(function (c) { child += Number(c.count) || 0; });
    node.count = own + child;
    node.vfxCount = {
      particles: (node.particles || []).length,
      meshes: (node.meshes || []).length,
      cameras: (node.cameras || []).length,
      sounds: (node.sounds || []).length,
      beams: (node.beams || []).length
    };
    node.children.sort(function (a, b) {
      if (a.type !== b.type) return a.type === 'ParticleFolder' ? -1 : 1;
      return String(a.name).localeCompare(String(b.name));
    });
  }
  rollup(root);
  stampTree(root, { n: 1 });
  return root;
}

function groupParticleHosts(emitters) {
  const tree = buildParticleTree(emitters);
  return tree.children || [];
}

function parseBinaryPlace(buf) {
  const chunks = listChunks(buf);
  const classes = {};
  chunks.forEach(function (chunk) {
    if (chunk.name !== 'INST') return;
    const data = chunk.data;
    if (data.length < 10) return;
    const classIndex = data.readUInt32LE(0);
    const cn = readLenString(data, 4);
    if (!cn) return;
    let off = cn.offset;
    if (off >= data.length) return;
    off += 1;
    if (off + 4 > data.length) return;
    const count = data.readUInt32LE(off);
    off += 4;
    const refs = readReferents(data, off, count);
    const instances = (refs.refs || []).map(function (ref) {
      return { ref: ref, className: cn.value, name: cn.value, props: {}, children: [], parent: null };
    });
    while (instances.length < count) {
      instances.push({ ref: instances.length, className: cn.value, name: cn.value, props: {}, children: [], parent: null });
    }
    classes[classIndex] = { className: cn.value, count: count, names: [], sources: [], referents: refs.refs, instances: instances };
  });

  const sharedStrings = [];
  chunks.forEach(function (chunk) {
    if (chunk.name !== 'SSTR') return;
    parseSstrChunk(chunk.data).forEach(function (s) { sharedStrings.push(s); });
  });

  chunks.forEach(function (chunk) {
    if (chunk.name !== 'PROP') return;
    const data = chunk.data;
    if (data.length < 6) return;
    const classIndex = data.readUInt32LE(0);
    const pn = readLenString(data, 4);
    if (!pn) return;
    const typeId = data[pn.offset];
    const cls = classes[classIndex] || { className: 'Instance', count: 1, names: [], sources: [] };
    if (!classes[classIndex]) classes[classIndex] = cls;
    const values = (typeId === 1) ? readStringArray(data, pn.offset + 1, cls.count || 64) : [];
    if (pn.value === 'Name') {
      cls.names = values;
      (cls.instances || []).forEach(function (inst, i) {
        if (values[i]) inst.name = values[i];
      });
    }
    if (pn.value === 'Source') cls.sources = values;
    cls['prop_' + pn.value] = values;
    if (!cls.typed) cls.typed = {};
    if (wantsTypedProps(cls.className) || typeId === 1 || typeId === 0x1c || typeId === 0x22 || typeId === 0x23 || typeId === 0x24 || typeId === 0x25 || typeId === 0x26) {
      cls.typed[pn.value] = parseTypedProp(data, typeId, pn.offset + 1, cls.count || 1, sharedStrings);
    }
    // skipped idsInText scan in studio parser
    if (cls.instances && cls.typed && cls.typed[pn.value]) {
      cls.instances.forEach(function (inst, i) {
        const arr = cls.typed[pn.value];
        inst.props[pn.value] = arr[i] != null ? arr[i] : arr[0];
      });
    }
  });

  const byRef = {};
  Object.keys(classes).forEach(function (idx) {
    (classes[idx].instances || []).forEach(function (inst) {
      byRef[inst.ref] = inst;
      byRef[String(inst.ref)] = inst;
    });
  });
  chunks.forEach(function (chunk) {
    if (chunk.name !== 'PRNT') return;
    const data = chunk.data;
    if (data.length < 5) return;
    let off = 1;
    const count = data.readUInt32LE(off);
    off += 4;
    const children = readReferents(data, off, count);
    const parents = readReferents(data, children.offset, count);
    for (let i = 0; i < count; i++) {
      const child = byRef[children.refs[i]];
      const pref = parents.refs[i];
      const parent = pref === -1 ? null : byRef[pref];
      if (!child) continue;
      child.parent = parent || null;
      if (parent) parent.children.push(child);
    }
  });

  const rows = [];
  const seen = {};
  const scripts = [];
  const particles = [];
  Object.keys(classes).forEach(function (idx) {
    const cls = classes[idx];
    const scriptClass = /^(Script|LocalScript|ModuleScript)$/.test(cls.className);
    if (scriptClass && cls.sources && cls.sources.length) {
      for (let i = 0; i < cls.sources.length; i++) {
        const src = String(cls.sources[i] || '').trim();
        if (!src) continue;
        const nm = sanitizeFilename(cls.names[i] || (cls.className + '_' + (i + 1)));
        scripts.push({ name: nm + '.lua', data: Buffer.from(src, 'utf8'), kind: cls.className });
      }
    }
    if (cls.className !== 'ParticleEmitter') {
      Object.keys(cls).forEach(function (key) {
        if (key.indexOf('ids_') !== 0) return;
        const prop = key.slice(4);
        if (prop === 'Source' || prop === 'Name' || prop === 'Texture') return;
        const typed = typeFromContext(prop, cls.className);
        (cls[key] || []).forEach(function (id) {
          if (seen[id + typed.type]) return;
          seen[id + typed.type] = true;
          const instName = cls.names && cls.names.length ? cls.names[0] : '';
          const inst = instName || (cls.className + ' ' + prop);
          rows.push({
            id: id,
            name: inst,
            type: typed.type,
            typeId: typed.typeId,
            prop: prop,
            className: cls.className,
            path: [cls.className, inst].filter(Boolean).join('/')
          });
        });
      });
    }
    const extraClass = /^(ParticleEmitter|Sound|SoundEffect|Beam)$/.test(cls.className)
      || (PARSE_OPTS.meshes && /^(MeshPart|SpecialMesh|FileMesh)$/.test(cls.className))
      || (PARSE_OPTS.cameras && cls.className === 'Camera');
    if (extraClass) {
      const n = (cls.instances && cls.instances.length) || cls.count || 1;
      for (let i = 0; i < n; i++) {
        const inst = (cls.instances && cls.instances[i]) || { name: (cls.names && cls.names[i]) || cls.className, className: cls.className, parent: null };
        const props = inst.props || {};
        Object.keys(cls.typed || {}).forEach(function (key) {
          if (props[key] != null) return;
          const arr = cls.typed[key] || [];
          props[key] = arr[i] != null ? arr[i] : arr[0];
        });
        inst.props = props;
        const row = { inst: inst, host: topHostOf(inst), name: inst.name, kind: 'other' };
        if (cls.className === 'ParticleEmitter') {
          const extraB = Object.assign({}, particleExtrasFromInst(inst), { name: inst.name || inst.Name });
          const particle = particleFromProps(props, extraB);
          particle.NAME = inst.name || inst.Name || particle.NAME;
          particles.push(particle);
          inst.particle = particle;
          row.particle = particle;
          row.kind = 'particle';
        } else if (PARSE_OPTS.meshes && /^(MeshPart|SpecialMesh|FileMesh)$/.test(cls.className)) {
          const mesh = meshFromInst(inst);
          if (mesh) {
            row.mesh = mesh;
            row.kind = 'mesh';
            row.name = mesh.NAME || inst.name;
            if (inst.parent && forgePair(inst.parent)) row.inst = inst.parent;
          }
        } else if (PARSE_OPTS.cameras && cls.className === 'Camera') {
          row.camera = cameraFromInst(inst, i);
          row.kind = 'camera';
        } else if (/^(Sound|SoundEffect)$/.test(cls.className)) {
          row.sound = soundFromInst(inst);
          row.kind = 'sound';
        } else if (cls.className === 'Beam') {
          row.beam = beamFromInst(inst);
          row.kind = 'beam';
        }
        inst._peRow = row;
      }
    }
  });
  const peRows = [];
  const allInst = [];
  Object.keys(classes).forEach(function (idx) {
    (classes[idx].instances || []).forEach(function (inst) {
      allInst.push(inst);
      if (inst._peRow && inst._peRow.kind !== 'camera') peRows.push(inst._peRow);
    });
  });
  const sequenced = PARSE_OPTS.cameras ? camerasFromInstancePool(allInst) : [];
  const camHost = { name: 'Cameras', className: 'Folder', parent: null };
  sequenced.forEach(function (cam) {
    peRows.push({ camera: cam, inst: camHost, host: camHost, name: cam.NAME, kind: 'camera' });
  });
  const tree = buildParticleTree(peRows);
  return { rows: rows, scripts: scripts, particles: particles, hosts: tree.children || [], tree: tree, cameras: sequenced, _instances: allInst };
}

function parseXmlPlace(buf) {
  const text = buf.toString('utf8');
  const rows = [];
  const seen = {};
  const scripts = [];
  let idx = 0;
  while ((idx = text.indexOf('name="Source"', idx)) !== -1) {
    const before = text.slice(Math.max(0, idx - 3000), idx);
    const after = text.slice(idx, idx + 200000);
    const classM = /class="(Script|LocalScript|ModuleScript)"[\s\S]*$/.exec(before);
    const nameMatches = before.match(/<string name="Name">([^<]*)<\/string>/g) || [];
    const lastName = nameMatches.length ? />([^<]*)<\/string>/.exec(nameMatches[nameMatches.length - 1]) : null;
    const srcM = /name="Source">(?:<!\[CDATA\[)?([\s\S]*?)(?:\]\]>)?<\/(?:ProtectedString|string)>/i.exec(after);
    const src = decodeXml(srcM ? srcM[1] : '').trim();
    if (src && (classM || src.indexOf('function') !== -1 || src.indexOf('local ') !== -1)) {
      scripts.push({
        name: sanitizeFilename((lastName && lastName[1]) || ((classM && classM[1]) || 'Script') + '_' + (scripts.length + 1)) + '.lua',
        data: Buffer.from(src, 'utf8'),
        kind: (classM && classM[1]) || 'Script'
      });
    }
    idx += 12;
  }
  const urlRe = /<(?:Content|string|token) name="([^"]+)"[^>]*>([\s\S]*?)<\/(?:Content|string|token)>/gi;
  let p;
  while ((p = urlRe.exec(text))) {
    const ids = idsInText(p[2]);
    if (!ids.length) continue;
    const typed = typeFromContext(p[1], '');
    ids.forEach(function (id) {
      if (seen[id + typed.type]) return;
      seen[id + typed.type] = true;
      rows.push({
        id: id,
        name: p[1] + ' ' + id,
        type: typed.type,
        typeId: typed.typeId,
        prop: p[1],
        className: '',
        path: p[1] || 'Content'
      });
    });
  }
  idsInText(text).forEach(function (id) {
    if (seen[id + 'Content'] || seen[id + 'Audio'] || seen[id + 'Image'] || seen[id + 'Mesh'] || seen[id + 'Animation'] || seen[id + 'Decal']) return;
    seen[id + 'Content'] = true;
    rows.push({ id: id, name: id, type: 'Content', typeId: 0, prop: '', className: '' });
  });
  const xmlTree = parseXmlTree(text);
  const emitters = collectXmlEmitters(xmlTree).filter(function (row) { return row.kind !== 'camera'; });
  let sequenced = [];
  if (PARSE_OPTS.cameras) {
    const flat = [];
    function walkXml(inst) {
      if (!inst) return;
      flat.push(inst);
      (inst.children || []).forEach(walkXml);
    }
    (xmlTree || []).forEach(walkXml);
    sequenced = camerasFromInstancePool(flat);
  }
  const camHost = { name: 'Cameras', className: 'Folder', parent: null };
  sequenced.forEach(function (cam) {
    emitters.push({ camera: cam, inst: camHost, host: camHost, name: cam.NAME, kind: 'camera' });
  });
  const particles = emitters.map(function (row) { return row.particle; }).filter(Boolean);
  const particleTree = buildParticleTree(emitters);
  return { rows: rows, scripts: scripts, particles: particles, hosts: particleTree.children || [], tree: particleTree, cameras: sequenced };
}

function ensureHosts(parsed, fallbackName) {
  parsed.particles = parsed.particles || [];
  parsed.tree = parsed.tree || { id: 'root', name: 'Root', className: 'Folder', type: 'ParticleFolder', children: parsed.hosts || [], particles: parsed.particles.slice() };
  parsed.hosts = (parsed.tree && parsed.tree.children) || parsed.hosts || [];
  if (!parsed.hosts.length && (parsed.cameras && parsed.cameras.length)) {
    parsed.hosts = [{
      id: 'cam-1',
      number: 1,
      name: 'Cameras',
      className: 'Folder',
      type: 'ParticleHost',
      typeId: 0,
      path: 'Cameras',
      particles: [],
      cameras: parsed.cameras.slice(),
      count: parsed.cameras.length,
      children: []
    }];
    parsed.tree.children = parsed.hosts;
    parsed.tree.cameras = parsed.cameras.slice();
    parsed.tree.count = parsed.cameras.length;
  }
  if (!parsed.hosts.length && parsed.particles.length) {
    parsed.hosts = [{
      id: '1',
      number: 1,
      name: fallbackName || 'Model',
      className: 'Model',
      type: 'ParticleHost',
      typeId: 0,
      path: 'Model',
      particles: parsed.particles.slice(),
      count: parsed.particles.length,
      children: []
    }];
    parsed.tree.children = parsed.hosts;
  }
  return parsed;
}

function parsePlace(buf, fileName, opts) {
  setParseOpts(opts || {});
  let parsed;
  if (isXmlRbxl(buf)) parsed = parseXmlPlace(buf);
  else parsed = parseBinaryPlace(buf);
  return ensureHosts(parsed, fileName);
}


root.JJSParser = {
  lz4Block: lz4Block,
  listChunks: listChunks,
  parsePlace: parsePlace,
  collectNodeParticles: collectNodeParticles,
  spreadToStr: spreadToStr,
  meshFromInst: meshFromInst,
  cameraFromInst: cameraFromInst,
  soundFromInst: soundFromInst,
  buildCameraSequence: buildCameraSequence,
  camerasFromInstancePool: camerasFromInstancePool,
  Buffer: Buffer,
  _readReferents: readReferents,
  _readLenString: readLenString,
  _parseTypedProp: parseTypedProp,
  _parseSstrChunk: parseSstrChunk
};
})(typeof window !== 'undefined' ? window : globalThis);
