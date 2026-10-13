# TreinoSmart v0.5 — GTS 3 MOYOUNG-V2

Protótipo Android nativo / offline-first para registrar a prescrição de musculação e investigar **controle direto** do smartwatch `GTS 3`, `MOYOUNG-V2`, software `MOY-VLP2-2.0.4`.

## Mudanças concretas desta versão

- **Controle pelo próprio aplicativo:** botões para pedir BPM dinâmico (`0x68`), solicitar BPM pontual (`0x6D`) e testar a vibração com o comando de localização (`0x61`). Mensagens curtas `FE EA 20 <tam> <comando> <dados>` pelo serviço `0xFEEA`, característica de escrita `0xFEE2`; respostas em `0xFEE3`. **Envio implementado, efeito no seu firmware ainda não verificado.** Nenhum comando de atualização de firmware é utilizado.
- **Treino + BLE integrados:** ao iniciar uma sessão real, envia solicitação de BPM dinâmico se o monitor BLE já estiver conectado. Ao encerrar, envia solicitação de parada.
- **Detector de BPM experimental:** com amostras recentes, procura subida sustentada (possível início) e queda sustentada (possível fim da série), considerando a fase e a prescrição. Quando estima fim, o descanso previsto inicia automaticamente. Detectores BPM estão sujeitos a atraso e falsos positivos/negativos; edite os dados após o treino. Habilitação opt-in na sessão.
- **Alerta no relógio diretamente:** quando o descanso termina, tenta comando `0x61` na mesma conexão. Se não houver resposta do relógio, o alerta local Android continua.
- **Controle manual preservado**, necessário para comparação, correção e séries sem evidências suficientes. Repetições executadas não são inventadas; apenas as confirmadas entram no volume.
- **Sem dependência de aplicativos de terceiros em execução**. Não exige movimento do celular. A interface no relógio ainda não está implantada; há um estudo em `watchface/`.

## Primeiro teste real (sem exercício intenso)

1. Conecte o GTS 3 no TreinoSmart, aguarde `BLE preparado`.
2. No próprio TreinoSmart toque **Solicitar BPM**; observe se o sensor inicia sem tocar na tela do relógio. Teste também **Ligar BPM dinâmico** e observe se continuam chegando amostras por 1–2 minutos. Use **Parar BPM dinâmico** ao terminar.
3. Pressione **Testar vibração no GTS 3** e observe se a função localizar faz o relógio vibrar.
4. Inicie um treino prescrito. O app solicitará o modo dinâmico se houver conexão. Ative **Detector BPM** e confira as transições na sessão. Para avaliar o algoritmo, marque séries reais manualmente em sessões de teste e exporte o CSV; não confie cegamente na detecção.
5. Se qualquer comando falhar, exporte o CSV: ele registrará pacotes, comandos solicitados e status BLE. **Não publique logs de saúde nem endereços do dispositivo no repositório público.** Não tente mandar bytes desconhecidos ou gravar firmware.

## Compilação

Use `.github/workflows/gerar-apk.yml` no GitHub Actions e baixe o artefato `TreinoSmart-APK-debug`. O ZIP contém a pasta `.github`; se enviar os arquivos pelo navegador, confirme que ela entrou no repositório. APK ainda precisa de compilação e teste no seu Android 11 — **não foi compilado aqui por falta do SDK Android**.

## Projeto

`android/` aplicativo Kotlin; `analytics/` análise Python; `rust/` ferramentas e decoder; `backend/` protótipo FastAPI; `watchface/` estudo de mostrador, sem gravar firmware; `docs/` especificação e histórico das decisões.

## Limites

BPM pode responder ao esforço com atraso e não mede diretamente execução nem confirma recuperação muscular. O detector é exploratório, não faz avaliação clínica, não dá metas fisiológicas nem altera a prescrição do professor. Comandos `0x68`/`0x6D`/`0x61` são referências do protocolo Moyoung e podem ser interpretados de maneira diferente por firmwares distintos.

Referências técnicas: https://gadgetbridge.org/internals/specifics/moyoung-protocol/ ; https://github.com/jphein/moyoung-watch ; https://github.com/e-labInnovations/dafit-canvas
