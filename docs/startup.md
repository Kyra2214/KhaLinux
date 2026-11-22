# Inicialização (8.3)

**Estado: concluído em código; nada compilado/executado** (sem `kotlinc`/rede). Sem medição de tempo em aparelho: os ganhos abaixo são por construção, não medidos.

Auditoria do que acontece ao criar o Service (código lido):
- `createRuntimeIfInstalled()` roda na thread principal (localizar PRoot, `RootfsFinalizer.apply`, preparar binds): poucas operações de arquivo pequenas. **Mantido** como está: o estado `runtime` é lido logo após a ligação (`isInstalled`), e torná-lo assíncrono criaria uma janela de "não instalado" falsa. Fica registrado como possível melhoria após medição.
- `labs()` recopiava **todas** as fixtures dos laboratórios para o RootFS a cada vez que o app abria. Agora `AssetSyncStamp` recopia só quando o app foi atualizado (marca = `lastUpdateTime` do pacote) ou o destino não existe; a marca é gravada só depois da cópia completa. Na primeira execução após criar o destino há uma recópia extra (marca com sufixo), sem efeito funcional.
- Catálogo, toolkit e laboratórios eram analisados (parse de assets) na primeira abertura de cada tela. Agora são aquecidos em segundo plano logo após a criação do Service, na fila do instalador (serial com limpeza e com a instalação), sem bloquear a thread principal. Se o runtime não está instalado, o aquecimento não faz nada.
- O tutor (7.8) monta seu motor na primeira pergunta, em IO; custo pequeno (arquivo de ~13 KB).
- Validação pendente: `AssetSyncStampTest` (escrito, não executado). Medir tempo de abertura das telas em aparelho.
