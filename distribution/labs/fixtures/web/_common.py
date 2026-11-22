"""Utilitários dos serviços de laboratório Web (stdlib apenas). Escutam SEMPRE e somente em 127.0.0.1."""
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

HOST = "127.0.0.1"


def port_from_argv(default):
    port = int(sys.argv[1]) if len(sys.argv) > 1 else default
    if not 1024 <= port <= 65535:
        sys.exit("porta fora de 1024-65535")
    return port


class QuietHandler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):  # sem ruído no terminal
        pass

    def send_body(self, status, body, ctype="text/plain; charset=utf-8", headers=None):
        data = body.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)


def serve(handler, default_port):
    server = HTTPServer((HOST, port_from_argv(default_port)), handler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
