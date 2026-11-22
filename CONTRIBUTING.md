# Contribuindo

Obrigado pelo interesse no KhaLinux. Leia [`README.md`](README.md), [`ARCHITECTURE.md`](ARCHITECTURE.md) e [`SECURITY.md`](SECURITY.md) antes de propor mudanças.

## Fluxo

1. Abra uma issue descrevendo contexto, escopo e critério de aceite.
2. Crie uma branch curta a partir da branch principal.
3. Faça mudanças pequenas e focadas.
4. Adicione testes e documentação; nunca declare testes que não foram executados.
5. Abra um pull request usando o template e descreva limitações conhecidas.

## Convenções

- Use nomes claros e consistentes com a arquitetura.
- Preserve compatibilidade ARM64 e execução sem root.
- Não adicione dependências sem justificar licença, origem, tamanho e necessidade.
- Não inclua binários grandes, credenciais ou dados pessoais.
- Commits devem ser pequenos e descritivos, preferencialmente no imperativo.
- Funcionalidades de segurança ofensiva devem indicar claramente escopo autorizado e laboratório controlado.

## Qualidade

Cada funcionalidade precisa de implementação, teste executado, evidência, documentação e critério de aceite. Testes dependentes de aparelho real devem ser identificados separadamente. Pull requests sem escopo verificável ou que apresentem scaffold como funcional poderão ser devolvidos.
