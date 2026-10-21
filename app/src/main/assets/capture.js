(function () {
  if (window.__vt) return;
  window.__vt = true;
  var B = window.VTBridge, MAX = 1500000;
  var TEXT = /json|text|xml|javascript|html|x-www-form/i;
  var SENS = /pass(word)?|pwd|mdp|mot_?de_?passe/i;

  function abs(u) { try { return new URL(u, location.href).href; } catch (e) { return String(u); } }
  function send(e) { try { B.entry(JSON.stringify(e)); } catch (x) {} }
  function now() { return new Date().toISOString(); }
  function maskObj(o) {
    if (o && typeof o === 'object') {
      for (var k in o) {
        if (SENS.test(k) && typeof o[k] !== 'object') o[k] = '***';
        else o[k] = maskObj(o[k]);
      }
    }
    return o;
  }
  function maskBody(b) {
    if (b === null || b === undefined) return null;
    if (typeof b !== 'string') {
      try {
        if (b instanceof URLSearchParams) b = b.toString();
        else if (b instanceof FormData) {
          var o = {};
          b.forEach(function (v, k) { o[k] = (typeof File !== 'undefined' && v instanceof File) ? '[file ' + v.name + ']' : v; });
          b = JSON.stringify(o);
        } else return '[binary ' + (b.size || b.byteLength || '?') + ']';
      } catch (e) { return '[unreadable]'; }
    }
    if (b.length > MAX) b = b.slice(0, MAX);
    try { return JSON.stringify(maskObj(JSON.parse(b))); } catch (e) {}
    return b.replace(/((?:pass(?:word)?|pwd|mdp|mot_?de_?passe)[^=&]*=)[^&]*/gi, '$1***');
  }
  function parseH(s) {
    var h = {};
    (s || '').trim().split(/[\r\n]+/).forEach(function (l) {
      var i = l.indexOf(':'); if (i > 0) h[l.slice(0, i).trim().toLowerCase()] = l.slice(i + 1).trim();
    });
    return h;
  }

  // ---- fetch ----
  var of = window.fetch;
  if (of) window.fetch = function (input, init) {
    var t0 = Date.now();
    var req = (typeof Request !== 'undefined' && input instanceof Request) ? input : null;
    var url = req ? req.url : String(input);
    var method = ((init && init.method) || (req && req.method) || 'GET').toUpperCase();
    var rh = {};
    try { new Headers((init && init.headers) || (req && req.headers) || {}).forEach(function (v, k) { rh[k] = v; }); } catch (e) {}
    var e = { type: 'fetch', time: new Date(t0).toISOString(), page: location.href, method: method, url: abs(url),
      requestHeaders: rh, requestBody: maskBody(init && init.body !== undefined ? init.body : null) };
    return of.apply(this, arguments).then(function (res) {
      e.status = res.status; e.statusText = res.statusText; e.durationMs = Date.now() - t0;
      var h = {}; res.headers.forEach(function (v, k) { h[k] = v; }); e.responseHeaders = h;
      if (TEXT.test(h['content-type'] || '')) {
        res.clone().text().then(function (t) {
          e.truncated = t.length > MAX; e.responseBody = e.truncated ? t.slice(0, MAX) : t; send(e);
        }).catch(function () { send(e); });
      } else { e.responseBody = '[non-text ' + (h['content-type'] || '') + ']'; send(e); }
      return res;
    }, function (err) { e.error = String(err); send(e); throw err; });
  };

  // ---- XHR ----
  var X = XMLHttpRequest.prototype, oo = X.open, os = X.send, oh = X.setRequestHeader;
  X.open = function (m, u) {
    this.__vt = { type: 'xhr', method: String(m).toUpperCase(), url: abs(u), requestHeaders: {} };
    return oo.apply(this, arguments);
  };
  X.setRequestHeader = function (k, v) { if (this.__vt) this.__vt.requestHeaders[k] = v; return oh.apply(this, arguments); };
  X.send = function (b) {
    var x = this, e = x.__vt;
    if (e) {
      e.time = now(); e.page = location.href; e.requestBody = maskBody(b);
      var t0 = Date.now();
      x.addEventListener('loadend', function () {
        e.status = x.status; e.durationMs = Date.now() - t0;
        e.responseHeaders = parseH(x.getAllResponseHeaders());
        try {
          var ct = x.getResponseHeader('content-type') || '';
          if ((x.responseType === '' || x.responseType === 'text') && TEXT.test(ct)) {
            var t = x.responseText; e.truncated = t.length > MAX; e.responseBody = e.truncated ? t.slice(0, MAX) : t;
          } else e.responseBody = '[non-text ' + ct + ' ' + x.responseType + ']';
        } catch (er) {}
        send(e);
      });
    }
    return os.apply(this, arguments);
  };

  // ---- WebSocket ----
  var OW = window.WebSocket;
  if (OW) {
    var W = function (u, p) {
      var ws = p !== undefined ? new OW(u, p) : new OW(u);
      var id = Math.random().toString(36).slice(2), url = abs(u);
      send({ type: 'ws-open', id: id, url: url, time: now(), page: location.href });
      ws.addEventListener('message', function (ev) {
        send({ type: 'ws-in', id: id, url: url, time: now(), data: typeof ev.data === 'string' ? ev.data.slice(0, 50000) : '[binary]' });
      });
      var s = ws.send;
      ws.send = function (d) { send({ type: 'ws-out', id: id, url: url, time: now(), data: maskBody(d) }); return s.apply(ws, arguments); };
      return ws;
    };
    W.prototype = OW.prototype;
    ['CONNECTING', 'OPEN', 'CLOSING', 'CLOSED'].forEach(function (k) { W[k] = OW[k]; });
    window.WebSocket = W;
  }

  // ---- export : stockage + fichiers du front ----
  function dump(s) { var o = {}; try { for (var i = 0; i < s.length; i++) { var k = s.key(i); o[k] = s.getItem(k); } } catch (e) {} return o; }
  window.__vt_export = async function () {
    var out = { location: location.href, userAgent: navigator.userAgent, localStorage: dump(localStorage),
      sessionStorage: dump(sessionStorage), assets: [] };
    try { out.indexedDBs = (await indexedDB.databases()).map(function (d) { return d.name; }); } catch (e) {}
    try { out.caches = await caches.keys(); } catch (e) {}
    try {
      out.serviceWorkers = (await navigator.serviceWorker.getRegistrations()).map(function (r) {
        return { scope: r.scope, script: (r.active || r.waiting || r.installing || {}).scriptURL };
      });
    } catch (e) {}
    var urls = {};
    urls[location.href] = 1;
    document.querySelectorAll('script[src],link[href]').forEach(function (n) { urls[abs(n.src || n.href)] = 1; });
    try {
      performance.getEntriesByType('resource').forEach(function (r) {
        if (/\.(js|css|json|webmanifest|html|svg)(\?|$)/i.test(r.name)) urls[r.name] = 1;
      });
    } catch (e) {}
    (out.serviceWorkers || []).forEach(function (s) { if (s.script) urls[s.script] = 1; });
    var list = Object.keys(urls).slice(0, 300);
    for (var i = 0; i < list.length; i++) {
      try {
        var r = await of.call(window, list[i], { credentials: 'include' });
        var t = await r.text();
        out.assets.push({ url: list[i], status: r.status, contentType: r.headers.get('content-type'), size: t.length, body: t.slice(0, 3000000) });
      } catch (e) { out.assets.push({ url: list[i], error: String(e) }); }
    }
    try { B.finish(JSON.stringify(out)); } catch (e) {}
  };
})();
