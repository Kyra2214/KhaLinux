# Favoritos (7.3)

**Estado: concluído em código; nada compilado/executado** (sem `kotlinc`/rede).

- Armazenamento: `filesDir/favorites.tsv`, linhas `COMMAND|TOOL<TAB>valor`; arquivo corrompido é ignorado linha a linha. Sem Room (evita nova migração/esquema).
- Comandos: favoritados a partir do campo de comando (modo não interativo); a entrada de shell interativo nunca é favoritada (pode ser senha). O usuário não deve favoritar comandos com segredos.
- Ferramentas: identificadas pelo `id` do `tools.json`; ids que deixarem de existir simplesmente não aparecem.
- Limites: 50 por tipo, 500 caracteres por valor.
