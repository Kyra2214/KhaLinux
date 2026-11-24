# Regras de desenvolvimento do KhaLinux (branch de laboratório)

Estas regras se aplicam ao trabalho nesta branch e a toda alteração feita nela.

## Ambiente físico autorizado

- O KhaLinux será usado exclusivamente em um laboratório controlado com **dois aparelhos Android**.
- **Aparelho 1:** aparelho operador, que executa o KhaLinux e controla as atividades.
- **Aparelho 2:** aparelho dedicado aos testes, sem uso pessoal, destinado a receber as conexões e solicitações do laboratório.
- Não ampliar o escopo para aparelhos, redes ou pessoas externas ao laboratório. Não presumir autorização fora desses dois aparelhos.

## Fidelidade técnica: comportamento real como padrão

- Implementar e demonstrar o comportamento real do Android e da rede: interfaces, SSID, endereços IP, estado do hotspot, conexões, permissões, respostas de APIs, erros e bloqueios do sistema.
- **Não substituir funcionalidades reais por dados inventados, resultados simulados, valores fixos apresentados como reais ou telas cenográficas.**
- Não criar clientes, dispositivos, tráfego, permissões concedidas, resultados ou eventos fictícios no fluxo de execução real.
- Se uma informação não puder ser obtida no Android, mostrar claramente “indisponível”, “não suportado” ou o erro real. Nunca preencher com um valor inventado.
- Dados sintéticos só podem existir em testes automatizados, fixtures ou demonstrações explicitamente identificadas como simulação; nunca devem ser confundidos com telemetria real.
- Cada resultado exibido deve corresponder a uma observação ou operação efetivamente realizada. Distinguir claramente dados observados, dados informados pelo operador e dados indisponíveis.

## Permissões e acesso ao aparelho de teste

- Usar APIs oficiais do Android e o fluxo normal de permissões do sistema.
- Solicitar autorização no próprio aparelho 2, de forma explícita, específica e visível, antes de acessar câmera, microfone, localização, arquivos ou outros recursos protegidos.
- Registrar o resultado real de cada solicitação: concedida, negada, revogada, bloqueada ou não suportada.
- Não contornar permissões, proteções do Android, telas de consentimento ou controles do usuário.
- Não implementar coleta oculta, persistência não autorizada, extração furtiva ou captura de senhas e tokens de contas. Para validar formulários e fluxos de autenticação, usar contas e credenciais de teste controladas pelo laboratório, sem coletar credenciais pessoais.

## Regras de implementação e validação

- Preservar o código existente e integrar mudanças incrementalmente; não reintroduzir dados fictícios para mascarar funcionalidades ausentes.
- Não afirmar que uma funcionalidade funciona sem evidência de implementação e validação.
- Antes de testes físicos, executar revisão estática, testes unitários/host quando aplicáveis e compilação.
- Testes físicos nos dois aparelhos ficam reservados à etapa final, após a conclusão das validações anteriores.
- Documentar limitações por versão do Android, permissões exigidas, APIs indisponíveis e diferenças entre fabricantes.
- Não alterar a branch `main` durante esta recuperação. Trabalhar nesta branch até revisão e autorização explícita para integrar.

## Regra de apresentação

A interface e os relatórios devem dizer exatamente o que ocorreu. Não rotular uma simulação como operação real, não apresentar estimativas como medições e não ocultar falhas ou limitações.
