"""Laboratório web-sqli-local: login que monta SQL por concatenação (propositalmente), em 127.0.0.1:8092.
Banco SQLite em memória, uma única instrução por consulta; nada é gravado em disco."""
import sqlite3
import threading
from urllib.parse import parse_qs, urlsplit

from _common import QuietHandler, serve

DB = sqlite3.connect(":memory:", check_same_thread=False)
LOCK = threading.Lock()
DB.execute("CREATE TABLE usuarios (nome TEXT, senha TEXT, flag TEXT)")
DB.execute("INSERT INTO usuarios VALUES ('visitante', 'visitante', 'KHA{visitante-a1b2}')")
DB.execute("INSERT INTO usuarios VALUES ('admin', 's3nha-longa-da-lab-91', 'KHA{sqli-local-3d9f}')")


class Handler(QuietHandler):
    def do_GET(self):
        parts = urlsplit(self.path)
        if parts.path != "/login":
            self.send_body(404, "nao encontrado\n")
            return
        qs = parse_qs(parts.query)
        user = qs.get("user", [""])[0]
        senha = qs.get("senha", [""])[0]
        # VULNERÁVEL DE PROPÓSITO: entrada concatenada na consulta (ver docs/labs/web-sqli-local.md).
        sql = "SELECT nome, flag FROM usuarios WHERE nome='" + user + "' AND senha='" + senha + "'"
        try:
            with LOCK:
                row = DB.execute(sql).fetchone()
        except sqlite3.Error as exc:
            self.send_body(500, "erro de banco: %s\n" % exc)
            return
        if row:
            self.send_body(200, "Bem-vindo, %s! flag=%s\n" % row)
        else:
            self.send_body(401, "credenciais invalidas\n")


serve(Handler, 8092)
