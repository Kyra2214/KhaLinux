# Gerenciador de arquivos (7.5)

**Estado: concluído em código; nada compilado/executado** (sem `kotlinc`/rede).

- Raiz: `filesDir/home` (= `/home/khalinux`). Tudo é relativo a ela; o caminho canônico precisa ficar dentro da raiz, o que também bloqueia links simbólicos para fora.
- Operações: listar (pastas primeiro), ler/gravar texto UTF-8 (≤256 KB, sem NUL), criar pasta, renomear na mesma pasta (sem sobrescrever), apagar (pasta não vazia só com confirmação; links são removidos sem seguir o destino).
- Limites/suposições: sem importar/exportar para o armazenamento do Android, sem permissões Unix, sem edição de binários; o editor é texto simples.
