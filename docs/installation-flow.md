# Fluxo de instalação automática

1. `MainActivity` conecta ao `KhaLinuxRuntimeService`; o `TerminalViewModel.attachRuntime` chama `startInstall()` se o runtime não existe.
2. O Service inicia como foreground (`dataSync`) e executa em thread própria: PRoot → RootFS → finalização.
3. Cada etapa valida o manifesto pelo SHA-256 fixado no app e os hashes do artefato antes de usar.
4. Download: `ResumableDownloader` usa `Range` sobre `.<nome>.download`; falhas de rede mantêm o parcial; hash divergente o apaga.
5. Extração com proteção contra path traversal/symlink; progresso em bytes.
6. Finalização: `RootfsFinalizer` (resolv.conf, hosts, home) e criação do runtime.

## Percentual global

| Fase | Faixa |
|---|---|
| PRoot | 0–3% |
| Download RootFS | 3–58% |
| Verificação | 58–60% |
| Extração | 60–97% |
| Finalização | 97–100% |

## Limitações conhecidas

- O percentual de extração é aproximado: `rootfs_bytes` vem de `du -sb`, que inclui diretórios e links.
- Reabrir o app após falha reinicia a tentativa automaticamente; o download continua do arquivo parcial.
- Não validado em aparelho físico (ver bloqueio de execução em `filesDir` no plano de correção).
- Nameservers padrão (1.1.1.1 e 8.8.8.8) são uma suposição; configurável em `RootfsFinalizer.apply`.
