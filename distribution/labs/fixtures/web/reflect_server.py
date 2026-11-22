"""Laboratório web-reflexao: página de busca que reflete a entrada SEM escapar (propositalmente), em 127.0.0.1:8091."""
from urllib.parse import parse_qs, urlsplit

from _common import QuietHandler, serve


class Handler(QuietHandler):
    def do_GET(self):
        parts = urlsplit(self.path)
        if parts.path != "/busca":
            self.send_body(404, "nao encontrado\n")
            return
        q = parse_qs(parts.query).get("q", [""])[0]
        # VULNERÁVEL DE PROPÓSITO: q entra no HTML sem escape (ver docs/labs/web-reflexao.md).
        self.send_body(200, "<html><body><p>Resultados para: " + q + "</p></body></html>\n", "text/html; charset=utf-8")


serve(Handler, 8091)
