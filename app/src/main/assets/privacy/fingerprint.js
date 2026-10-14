/*! ThirtySix basic fingerprinting protection (Spec 024 research R9). ThirtySix's own code.
 * Called by the app's PrivacyScriptComposer as (function(cfg){...})({v, s, x}):
 *   s = per-session secret (hex), x = registrable domains with protection off.
 * Read-outs of drawings and audio carry slight noise, seeded per top-level site and session;
 * what is shown on screen is never modified. Battery status is withheld; media-device names and
 * ids stay hidden until the page has camera or microphone access. Nothing is sent to the app. */
(function (cfg) {
  'use strict';

  function topHost() {
    var ancestors = location.ancestorOrigins;
    if (ancestors && ancestors.length > 0) {
      try { return new URL(ancestors[ancestors.length - 1]).hostname.toLowerCase(); } catch (e) { /* own host */ }
    }
    return location.hostname.toLowerCase();
  }

  var top = topHost();
  for (var i = 0; i < cfg.x.length; i++) {
    var site = cfg.x[i];
    if (top === site || top.slice(-(site.length + 1)) === '.' + site) return;
  }
  if (!/^https?:$/.test(location.protocol)) return;

  // FNV-1a (32-bit) of secret|top, then xorshift32. Same site + same secret → same noise.
  function seedOf(text) {
    var h = 0x811c9dc5;
    for (var k = 0; k < text.length; k++) {
      h ^= text.charCodeAt(k);
      h = Math.imul(h, 0x01000193) >>> 0;
    }
    return h || 1;
  }
  var SEED = seedOf(cfg.s + '|' + top);
  function rng(salt) {
    var x = (SEED ^ salt) >>> 0 || 1;
    return function () {
      x ^= x << 13; x >>>= 0;
      x ^= x >>> 17;
      x ^= x << 5; x >>>= 0;
      return x;
    };
  }

  var nativeToString = Function.prototype.toString;
  var disguised = new WeakMap();
  function disguise(wrapper, original) {
    disguised.set(wrapper, original);
    try { Object.defineProperty(wrapper, 'name', { value: original.name }); } catch (e) { /* keep */ }
    try { Object.defineProperty(wrapper, 'length', { value: original.length }); } catch (e) { /* keep */ }
    return wrapper;
  }
  var toStringWrapper = function toString() {
    var target = disguised.has(this) ? disguised.get(this) : this;
    return nativeToString.call(target);
  };
  disguise(toStringWrapper, nativeToString);
  Function.prototype.toString = toStringWrapper;

  function replace(proto, name, make) {
    if (!proto) return;
    var original = proto[name];
    if (typeof original !== 'function') return;
    var wrapper = disguise(make(original), original);
    try {
      Object.defineProperty(proto, name, { value: wrapper, writable: true, configurable: true, enumerable: false });
    } catch (e) { /* frozen by the page: leave it */ }
  }

  // ±1 on a sparse, seeded subset of colour channels. Alpha is never touched.
  function noisePixels(data, width) {
    var next = rng(width >>> 0);
    for (var p = 0; p < data.length; p += 4) {
      var r = next();
      if ((r & 0x3f) !== 0) continue; // about 1 pixel in 64
      var channel = p + ((r >>> 6) % 3);
      var delta = (r & 0x100) ? 1 : -1;
      var v = data[channel] + delta;
      data[channel] = v < 0 ? 1 : (v > 255 ? 254 : v);
    }
  }

  var ctx2dProto = window.CanvasRenderingContext2D && CanvasRenderingContext2D.prototype;
  var nativeGetImageData = ctx2dProto && ctx2dProto.getImageData;

  replace(ctx2dProto, 'getImageData', function (original) {
    return function getImageData() {
      var image = original.apply(this, arguments);
      noisePixels(image.data, image.width);
      return image;
    };
  });

  // toDataURL / toBlob: draw the canvas onto a scratch canvas, add noise there, read that copy.
  function noisyCopy(canvas) {
    var width = canvas.width, height = canvas.height;
    if (!width || !height || !nativeGetImageData) return null;
    var copy = document.createElement('canvas');
    copy.width = width; copy.height = height;
    var context = copy.getContext('2d');
    if (!context) return null;
    try {
      context.drawImage(canvas, 0, 0);
      var image = nativeGetImageData.call(context, 0, 0, width, height);
      noisePixels(image.data, width);
      context.putImageData(image, 0, 0);
      return copy;
    } catch (e) {
      return null; // tainted or WebGL-only canvas: read-out is left as is
    }
  }

  var canvasProto = window.HTMLCanvasElement && HTMLCanvasElement.prototype;
  replace(canvasProto, 'toDataURL', function (original) {
    return function toDataURL() {
      var copy = noisyCopy(this);
      return original.apply(copy || this, arguments);
    };
  });
  replace(canvasProto, 'toBlob', function (original) {
    return function toBlob() {
      var copy = noisyCopy(this);
      return original.apply(copy || this, arguments);
    };
  });

  if (window.OffscreenCanvasRenderingContext2D) {
    replace(OffscreenCanvasRenderingContext2D.prototype, 'getImageData', function (original) {
      return function getImageData() {
        var image = original.apply(this, arguments);
        noisePixels(image.data, image.width);
        return image;
      };
    });
  }

  // Audio: copy-on-read with tiny noise on a sparse subset of samples.
  function noiseFloats(array, salt) {
    var next = rng(salt >>> 0);
    for (var n = 0; n < array.length; n++) {
      var r = next();
      if ((r & 0x7f) !== 0) continue;
      array[n] = array[n] + ((r & 0x100) ? 1e-7 : -1e-7);
    }
  }

  if (window.AudioBuffer) {
    var copies = new WeakMap();
    replace(AudioBuffer.prototype, 'getChannelData', function (original) {
      return function getChannelData(channel) {
        var perBuffer = copies.get(this);
        if (!perBuffer) { perBuffer = {}; copies.set(this, perBuffer); }
        if (!perBuffer[channel]) {
          var copy = new Float32Array(original.call(this, channel));
          noiseFloats(copy, channel + 7);
          perBuffer[channel] = copy;
        }
        return perBuffer[channel];
      };
    });
  }

  if (window.AnalyserNode) {
    replace(AnalyserNode.prototype, 'getFloatFrequencyData', function (original) {
      return function getFloatFrequencyData(array) {
        original.call(this, array);
        noiseFloats(array, 11);
      };
    });
    replace(AnalyserNode.prototype, 'getByteFrequencyData', function (original) {
      return function getByteFrequencyData(array) {
        original.call(this, array);
        var next = rng(13);
        for (var b = 0; b < array.length; b++) {
          var r = next();
          if ((r & 0x7f) === 0 && array[b] > 0 && array[b] < 255) array[b] += (r & 0x100) ? 1 : -1;
        }
      };
    });
  }

  // Battery status: withheld (FR-027). Feature-detecting pages carry on without it.
  if (window.Navigator && 'getBattery' in Navigator.prototype) {
    try { delete Navigator.prototype.getBattery; } catch (e) { /* keep */ }
  }

  // Media devices: no ids before the page has camera or microphone access (FR-028).
  // The engine already returns empty labels until then; ids are blanked in that same state.
  if (window.MediaDevices) {
    replace(MediaDevices.prototype, 'enumerateDevices', function (original) {
      return function enumerateDevices() {
        return original.apply(this, arguments).then(function (devices) {
          var anyLabel = false;
          for (var d = 0; d < devices.length; d++) if (devices[d].label) { anyLabel = true; break; }
          if (anyLabel) return devices;
          return devices.map(function (device) {
            return {
              deviceId: '', groupId: '', kind: device.kind, label: '',
              toJSON: function () { return { deviceId: '', groupId: '', kind: device.kind, label: '' }; },
            };
          });
        });
      };
    });
  }
})
