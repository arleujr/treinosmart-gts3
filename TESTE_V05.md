# Roteiro curto — validação v0.5 no GTS 3

Não é necessário instalar outros aplicativos; use somente TreinoSmart e o relógio.

**A — Coleta:** conecte, clique Solicitar BPM sem ativar manualmente no relógio. Anote se a tela do relógio acende para medir e se o BPM começa a chegar. Depois Ligar BPM dinâmico: verifique se chegam leituras durante 1–2 minutos. Finalmente Parar BPM dinâmico.

**B — Pulso:** conectado, pressione Testar vibração no GTS 3 uma vez. Anote se vibrou e se houve retorno/erro no aplicativo. Esse comando pode ativar o padrão 'localizar relógio'.

**C — Detector:** inicie uma sessão real, ative Detector de BPM e registre uma sessão leve, dentro do seu treino usual. Corrija manualmente transições erradas. Um aumento da FC não prova início, e queda não prova fim; pode não detectar algumas séries.

**D — Alertas:** em uma sessão, use início/fim de série manualmente se necessário e veja se, após o descanso prescrito, o celular notifica e o relógio recebe a tentativa de comando.

**E — Dados:** Exportar CSV. Campos `hr`, `hr_boundary_candidate`, `set_start`, `set_end`, `rest_due`, `hr_command`, `watch_alert`, `ble_status` ajudam a depurar.

Se apenas o modo esporte do relógio ativar BPM, registre isso: temos a primeira implementação de comandos, mas o comportamento precisa de confirmação por hardware.

Para testar comandos de sensor não precisamos fazer esforço adicional, apenas observar a comunicação em repouso com o relógio no pulso.
