#!/usr/bin/env python3
"""Spec 024 fixtures server for manual device gates (quickstart). Serves this folder on :8024,
adds the HttpOnly cookie fp_persist on storage.html, and JavaScript MIME types for .js.
Use with `adb reverse tcp:8024 tcp:8024`; open http://localhost:8024/… and http://127.0.0.1:8024/…"""
import http.server, socketserver, os

class Handler(http.server.SimpleHTTPRequestHandler):
    extensions_map = {**http.server.SimpleHTTPRequestHandler.extensions_map, '.js': 'application/javascript'}

    def end_headers(self):
        if self.path.split('?')[0].endswith('/storage.html') and 'check' not in self.path:
            self.send_header('Set-Cookie', 'fp_persist=1; Max-Age=31536000; Path=/; HttpOnly; SameSite=Lax')
        self.send_header('Cache-Control', 'no-store')
        super().end_headers()

os.chdir(os.path.dirname(os.path.abspath(__file__)))
with socketserver.ThreadingTCPServer(('', 8024), Handler) as server:
    server.serve_forever()
