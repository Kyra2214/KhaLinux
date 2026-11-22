# Terminal ANSI/VT

O KhaLinux inclui um emulador de terminal leve no módulo runtime. Ele recebe bytes do PTY, decodifica UTF-8 entre blocos e mantém uma tela limitada com scrollback.

## Suporte atual

- UTF-8 incremental, inclusive quando um caractere é dividido entre eventos stdout/stderr.
- CR, LF, backspace e tabulação.
- Cursor: mover, posicionar, salvar/restaurar e visibilidade.
- Limpeza de linha/tela, inserção/remoção de caracteres e linhas e scroll básico.
- SGR: reset, bold, dim, italic, underline, inverse e strike-through.
- Cores ANSI de 16 cores, paleta 256 cores e RGB verdadeiro.
- Sequências OSC de título são consumidas sem aparecer como texto.
- Scrollback limitado a 1.000 linhas, configurável no runtime.
- A UI Compose renderiza cores e atributos usando texto monoespaçado.

O runtime anuncia `TERM=ansi`, que corresponde ao subconjunto implementado. Não anuncia `xterm-256color`.

## Limitações conhecidas

Não é um emulador xterm completo. Ainda não implementa alternate screen, mouse reporting, bracketed paste, respostas a consultas de dispositivo, regiões de scroll arbitrárias, hyperlinks OSC 8 ou cálculo de largura Unicode para caracteres CJK/emoji. Aplicativos TUI complexos podem exigir recursos ainda não suportados.

Os testes automatizados cobrem parser, UTF-8 fragmentado, cores, cursor, erase, scrollback e integração host com PRoot/QEMU. A validação no Android físico continua reservada à etapa final.
