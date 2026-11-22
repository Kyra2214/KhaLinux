# Personalização (7.2)

**Estado: concluído em código; nada compilado/executado** (sem `kotlinc`/rede).

- Preferências persistidas na tabela `terminal_settings`: fonte (10–22 sp), linhas, colunas e `themeIndex`.
- Temas em `runtime/settings/TerminalThemes.kt`; alteram só o fundo e o primeiro plano padrão. As cores ANSI explícitas continuam as do emulador.
- Migração 2→3 (`ALTER TABLE ... ADD COLUMN themeIndex INTEGER NOT NULL DEFAULT 0`); `@ColumnInfo(defaultValue = "0")` na entidade para a validação do Room. O JSON do esquema v3 será gerado pelo build e deve ser commitado (pendência já registrada).
- Suposição: sem modo de cor do app inteiro; apenas o terminal é tematizado.
