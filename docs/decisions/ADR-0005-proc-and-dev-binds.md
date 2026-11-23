# ADR-0005: /proc montado do Android; /sys não

**Estado:** revisado (decisão do autor, novembro de 2026). Substitui a decisão original de não montar `/proc`.

**Decisão:** `AndroidBindMountPolicy` liga `/dev/null, zero, random, urandom, tty, pts` **e `/proc`** do Android. `/sys` e o `/dev` inteiro continuam fora.

**Motivos:** `ps`, `top`, `free`, `ss`, `lynis`, `rkhunter`, `chkrootkit` e scripts de pós-instalação do `dpkg` dependem de `/proc`; sem ele a instalação de pacotes e as ferramentas do toolkit ficavam limitadas.

**Exposição (análise):** o Debian passa a ler informações de `/proc` visíveis ao UID do app. No Android moderno (`hidepid`) isso não inclui processos de outros apps, mas expõe dados do próprio app e do kernel (versão, memória, CPU). O PRoot não implementa montagem somente leitura (`ro`); o convidado "root" simulado continua sem privilégios reais de escrita em `/proc`. Ferramentas ofensivas seguem atrás da confirmação de escopo.

**Consequências:** limitações restantes são de privilégio (sem root real, sem raw sockets, sem `/sys`). Teste: `AndroidBindMountPolicyTest`.
