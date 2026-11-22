"""Laboratório web-http-basico: servidor didático (sem vulnerabilidades) em 127.0.0.1:8090."""
from urllib.parse import urlsplit

from _common import QuietHandler, serve


class Handler(QuietHandler):
    def do_GET(self):
        path = urlsplit(self.path).path
        if path == "/":
            self.send_body(200, "KhaLinux lab web\n", headers={"X-Lab-Versao": "1"})
        elif path == "/robots.txt":
            self.send_body(200, "User-agent: *\nDisallow: /segredo-lab\n")
        elif path == "/segredo-lab":
            self.send_body(200, "flag: KHA{http-basico-7c21}\n")
        else:
            self.send_body(404, "nao encontrado\n")

    do_HEAD = do_GET


serve(Handler, 8090)
