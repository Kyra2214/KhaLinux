"""Gera, de forma determinística, as evidências didáticas dos laboratórios de forense (stdlib apenas).
Uso: python3 gerar_evidencias.py [diretorio]   (padrão: /opt/khalinux-labs/forense/evidencias)
Só escreve arquivos novos dentro do diretório informado; nada é lido ou enviado para fora."""
import os
import struct
import sys
import zlib

DESTINO = sys.argv[1] if len(sys.argv) > 1 else "/opt/khalinux-labs/forense/evidencias"
FLAG = b"KHA{forense-png-52ab}"


def chunk(tipo, dados):
    corpo = tipo + dados
    return struct.pack(">I", len(dados)) + corpo + struct.pack(">I", zlib.crc32(corpo) & 0xFFFFFFFF)


def png_com_comentario():
    ihdr = struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0)
    idat = zlib.compress(b"\x00\xff\x00\x00", 9)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"tEXt", b"Comment\x00" + FLAG)
            + chunk(b"IDAT", idat) + chunk(b"IEND", b""))


def log_acesso():
    # (hora, ip, usuario, resultado); IPs das faixas de documentação (RFC 5737), sem relação com redes reais.
    eventos = [
        ("08:00:02", "192.0.2.10", "maria", "OK"), ("08:01:15", "192.0.2.11", "joao", "OK"),
        ("08:03:40", "203.0.113.9", "admin", "FALHA"), ("08:03:42", "203.0.113.9", "admin", "FALHA"),
        ("08:03:45", "203.0.113.9", "admin", "FALHA"), ("08:05:10", "192.0.2.10", "maria", "OK"),
        ("08:06:01", "198.51.100.4", "maria", "FALHA"), ("08:06:09", "198.51.100.4", "maria", "FALHA"),
        ("08:07:30", "203.0.113.9", "admin", "FALHA"), ("08:07:33", "203.0.113.9", "admin", "FALHA"),
        ("08:08:00", "192.0.2.12", "ana", "OK"), ("08:09:21", "203.0.113.9", "admin", "FALHA"),
        ("08:09:24", "203.0.113.9", "admin", "FALHA"), ("08:10:05", "198.51.100.4", "maria", "FALHA"),
        ("08:11:11", "192.0.2.11", "joao", "OK"), ("08:12:50", "203.0.113.9", "admin", "FALHA"),
    ]
    return "".join("2026-11-01T%s ip=%s user=%s resultado=%s\n" % e for e in eventos)


def main():
    os.makedirs(DESTINO, exist_ok=True)
    arquivos = {
        "copia-a.txt": b"Relatorio interno KhaLinux\nstatus: aprovado\n",
        "copia-b.txt": b"Relatorio interno KhaLinux\nstatus: aprovado\n",
        "versao-c.txt": b"Relatorio interno KhaLinux\nstatus: aprovadO\n",
        "relatorio.pdf": png_com_comentario(),  # disfarce: extensão .pdf, conteúdo PNG
        "log-acesso.txt": log_acesso().encode("ascii"),
    }
    for nome, dados in arquivos.items():
        with open(os.path.join(DESTINO, nome), "wb") as f:
            f.write(dados)


main()
