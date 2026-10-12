#!/usr/bin/env python3
"""Spec 023 G10/G11 — serves this folder and prints every request's path and headers.

Run with `python3 -I echo_headers.py 8023` from this folder, then `adb reverse tcp:8023 tcp:8023`.
"""
import http.server
import sys


class Handler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        print(f"--- {self.path}")
        for name, value in self.headers.items():
            print(f"    {name}: {value}")
        sys.stdout.flush()
        super().do_GET()


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8023
    http.server.ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
