# Integração entre ferramentas (7.7)

**Estado: concluído em código; nada compilado/executado** (sem `kotlinc`/rede).

- Saída → nota: o texto visível da aba ativa vira `reports/nota-AAAAMMDD-HHMMSS.md` (UTC, sem sobrescrever), legível no gerenciador de arquivos. Saída pode conter dados sensíveis do usuário: a nota só é criada por ação explícita e nada é enviado para fora do aparelho.
- Ferramenta → terminal: insere `<comando> ` no campo de comando (o usuário completa e executa). O gate de escopo continua sendo aplicado em `ToolkitService.run`; o terminal livre não passa por ele (limitação já registrada em `docs/scripts.md`).
- Suposição: sem encadeamento automático entre ferramentas nem anexo automático a `relatorio-*.md`; o usuário combina notas e relatórios em `reports/`.
