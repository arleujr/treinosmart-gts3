# Assistente Inteligente de Musculação — Visão e Especificação

**Estado:** idealização consolidada, investigação do hardware pendente  
**Versão:** 0.1 — 9 de outubro de 2026  
**Tipo de projeto:** aplicativo Android offline-first + sistema de engenharia de dados e backend; código destinado a portfólio GitHub  
**Equipamentos conhecidos:** smartwatch anunciado como Zeblaze GT3/GTS 3 Voice Calling, tela *Sobre* `GTS 3 MOY-VLP2-2.04`, usa Da Fit; smartphone Android 11; PC Windows 10, Core i5-3330, 16 GB RAM, SSD e AMD Radeon 2 GB.  
**Identidade exata do hardware e características Bluetooth:** não verificadas experimentalmente.

## 1. Visão

Criar um assistente pessoal de musculação que transforma a prescrição de treino em uma sessão monitorada com mínimo contato com o celular. A sequência de exercícios e suas séries já é conhecida previamente. O problema principal **não é reconhecer qual exercício está sendo praticado**, mas usar dados do relógio para inferir **quando uma série começa, quando termina, quanto dura, quanto se descansa** e **como o treino realizado se compara ao planejado**.

A aplicação deve oferecer acompanhamento e feedback no decorrer da sessão, não somente um relatório posterior. Os resultados serão interpretados como evidências, com grau de confiança e possibilidade de correção. O sistema não substitui o professor e não diagnostica execução biomecânica, lesões ou recuperação muscular.

### Experiência ideal

1. Treino planejado previamente com ordem dos exercícios, séries, repetições, carga e descanso.
2. Usuário inicia a sessão preferencialmente pelo próprio relógio, **se possível**; caso contrário, pelo Android.
3. Smartphone pode permanecer no bolso ou guardado, tela apagada.
4. Sistema estima o início de uma série com sinais reais do relógio e começa cronômetro.
5. Detecta o término, registra duração e inicia automaticamente o descanso.
6. Mostra informações e alertas contextuais durante a sessão — por notificação no relógio e/ou interface no celular — sem excesso de interrupções.
7. Ao terminar o descanso, relógio vibra e, se suportado, mostra exercício, série e indicação relevante.
8. Próxima série é acompanhada automaticamente, respeitando a ordem definida na prescrição.
9. Ao fim: comparação previsto × observado, tempos, pausas, cargas/repetições confirmadas, volume e tendências.
10. Erros de detecção podem ser corrigidos depois ou por intervenção pontual, **sem exigir confirmações a cada série como fluxo principal**.

## 2. Escopo funcional

### 2.1 Cadastro e execução
- Modelagem de planos, dias, exercícios, sequência, séries, repetições, cargas, intervalos e orientações do professor.
- Sessões planejadas versus sessões executadas: manter ambas, sem sobrescrever prescrição.
- Máquinas de estado: **pronto → execução provável → conclusão provável → descanso → pronto**, com estados especiais `incerto`, `pausado`, `reconectando` e `encerrado`.
- Reconciliação de interrupções, atrasos, exercícios pulados e modificações pontuais do treino.
- Correção a posteriori com histórico de alterações.
- Operação offline completa durante a sessão.

### 2.2 Coleta e análise
- Sinais conhecidos no uso do aparelho: frequência cardíaca (BPM), vibração e resultados de atividade/corrida.
- Investigar o acesso a **timestamps e amostras de BPM**, movimentos do pulso e quaisquer notificações/eventos de botões.
- Não confundir presença física de sensores com transmissão de amostras ao Android.
- Registrar dados brutos **somente quando disponíveis**; caso contrário, dados agregados e suas limitações.
- Para cada série: horários de início/fim estimados, duração, fonte, versão do algoritmo, confiança, parâmetros usados e eventual validação manual.
- Registrar descanso *prescrito*, *observado* e diferença entre ambos.
- Comparar tempos de séries semelhantes e ritmo global com referência definida pelo professor ou histórico pessoal.
- Distinguir **repetições prescritas** de **repetições efetivamente realizadas e confirmadas**; não declarar que houve 12 repetições só porque a planilha previa 12.
- Volume (por exemplo, soma de carga × repetições executadas confirmadas, quando cabível), com indicação de dados ausentes.
- Frequência cardíaca como informação complementar, não prova isolada de finalização da série ou recuperação muscular.

### 2.3 Feedback ao vivo
- **Durante a série**, apenas se existir sinal temporal com qualidade e latência suficientes: orientação contextual do tipo “ritmo mais rápido que a faixa combinada”.
- **Após a série**, mais viável: “série mais rápida que as anteriores”, “descanso menor que o previsto”, “alteração importante no padrão” ou “tempo consistente”.
- Alertas devem evitar afirmar “execução incorreta” sem evidência e não incentivar ignorar limites do professor ou sinais físicos.
- Regras configuráveis por treino/exercício: faixas de referência, tolerâncias e possibilidade de silenciar alertas.
- Controle de confiança: o sistema prefere não notificar a informar algo enganoso.
- Vibração ao final do descanso e alertas importantes, mediante suporte real do relógio.
- Futuro: ajuste **sugerido**, não compulsório, de descanso com base em métricas observáveis e limites definidos pelo professor.

### 2.4 Interação no relógio: camadas de possibilidade
1. **Confirmada pelo relato do usuário:** relógio já recebeu notificações do smartphone com vibração (não foi testado com nosso app).
2. **A testar:** notificações personalizadas do nosso app via Da Fit ou Gadgetbridge.
3. **A testar:** comando iniciado no relógio usando recurso nativo reaproveitável (controle de música, localizar celular, câmera ou outro evento), quando o firmware transmitir evento e ele chegar ao nosso app.
4. **A testar:** mostrador/watchface personalizado, provavelmente mais viável como elemento visual que como aplicativo com botões próprios.
5. **Não demonstrado:** instalar um aplicativo interativo nativo, redesenhar todo o sistema do relógio ou criar menus próprios com comandos arbitrários; firmware provavelmente proprietário. Não prometer.

### 2.5 Dados, backend e integrações
- Histórico local no Android; sincronização eventual e resiliente com API.
- Cadastro de prescrição, sessões, séries, eventos, amostras, correções e análises no backend.
- Exportações CSV/JSON; possível arquivo de atividade para integração futura, sem depender dela durante o treino.
- Integração externa, incluindo Strava, apenas se formato e requisitos de acesso justificarem; detalhes da prescrição permanecem em nossa plataforma.
- Dashboard de evolução e rastreabilidade: dados originais, decisões do classificador e qualidade das observações.
- Nenhum serviço online deve ser necessário para executar treino, detectar séries ou gerar vibrações.

## 3. Arquitetura técnica proposta

```text
Relógio (sensores internos, firmware proprietário)
          ↕ Bluetooth BLE / protocolo Moyoung? (investigar)
Camada de integração no Android (Kotlin)
    ├─ opcional: Da Fit encaminha notificações (fase experimental)
    ├─ opcional: Gadgetbridge, se dispositivo compatível
    └─ desejável: implementação própria BLE/Moyoung
          ↓ eventos + amostras com timestamps
Motor local offline (Rust + integração JNI/UniFFI, se custo justificar)
    ├─ pré-processamento / janelas temporais
    ├─ estados: provável execução/descanso/transição
    ├─ scores de confiança e detecção de anomalias simples
    └─ regras de alerta baseadas na prescrição
          ↕ FFI
Aplicativo Android (Kotlin + Jetpack Compose)
    ├─ banco local Room/SQLite
    ├─ execução em segundo plano, conexão e notificações
    ├─ interface de plano/histórico e correções
    └─ fila de sincronização
          ↕ API REST, apenas quando online
Backend (Python FastAPI)
    ├─ PostgreSQL
    ├─ ingestão, validação e versionamento de sessões
    ├─ análises Pandas/NumPy/SciPy e experimentos scikit-learn
    └─ relatórios, exportações e integrações externas
```

### Papel real das linguagens

| Stack | Por que entra |
|---|---|
| Kotlin / Android SDK | Controle de conexão e permissões Bluetooth, serviço de treino, notificações, Room, interface. |
| Rust | **Motor local determinístico** que recebe sequências de amostras/eventos, mantém estado de treino e calcula características/scores. Pode ser testado com dados gravados no PC e integrado no Android via JNI/UniFFI. Se a integração for complexa, prototipar em Kotlin e migrar com justificativa. |
| Python | Exploração de sinais, tratamento, visualizações, definição e validação de algoritmos, experimentos com modelos; API FastAPI. |
| PostgreSQL | Histórico persistente, consultas por sessão/exercício, integridade e análises. |
| SQLite/Room | Fonte da verdade durante o treino offline, fila para sincronizar depois. |
| Docker | Subir FastAPI + PostgreSQL no PC quando possível; **não** requisito do treino em tempo real. |

### Por que Rust tem sentido aqui

Uma biblioteca `training-core` em Rust pode receber `sample(t, tipo, valor, qualidade)`, `expected_exercise` e `config`, retornando eventos como `SetStarted`, `SetEnded`, `RestStarted`, `RestFinished`, `LowConfidence` e `PacingAlert`. É um núcleo desacoplado do Android, com testes reproduzíveis, que pode ser alimentado por arquivos CSV em um CLI de laboratório e também pelo app real. **Não** desenvolver “IA pesada” prematuramente: começar por tratamento temporal e regras, evoluindo se houver dados coletados.

### Modelo mínimo de registros

- `workout_plan`, `exercise_plan`, `planned_set`;
- `workout_session`, `executed_set`, `rest_interval`;
- `sensor_sample` (tempo, tipo, unidade, valor, dispositivo, origem e qualidade);
- `event_detection` (início, fim, estado, score, algoritmo/versão, origem);
- `feedback_event` (tipo, momento, evidência, entregue/silenciado);
- `correction` (antes/depois, usuário, razão, timestamp);
- `sync_outbox` (identificador idempotente, versão, estado).

Separar relógio/telefone, tempos absolutos e relógio monotônico; detectar lacunas de amostras e reconexões. Não registrar falsamente dados presumidos como medidos. Evitar guardar identificadores Bluetooth pessoais nos dados públicos do GitHub.

## 4. Algoritmo e critérios de validação

1. **Sem dados reais ainda:** fabricar sessões sintéticas somente para testar fluxos, com metadado `synthetic=true`.
2. **Coleta real:** registrar padrões em sessões normais e rótulos de referência (começo/fim de série) quando possível, sem demandar atividade extra ou mudanças arriscadas.
3. **Baseline:** limiares, janelas móveis, duração mínima, histerese, períodos de inatividade e regras dependentes do exercício previsto.
4. **Análise offline:** visualizar séries temporais, contrastar rótulos e ajustar parâmetros com Python.
5. **Modelo supervisionado:** somente se houver dados suficientes e diversidade para comparar contra o baseline.
6. **Métricas:** erro absoluto do início/fim em segundos; precisão/recall de séries detectadas; falsos positivos por sessão; erro na duração e no descanso; atraso de entrega da vibração; consumo de bateria; desempenho com tela apagada.
7. **Fallback:** se confiança baixa, guardar intervalo incerto, solicitar revisão posterior e continuar sessão quando apropriado; evitar inventar conclusões.

## 5. Posições técnicas já alinhadas

- Objetivo principal: automação de execução e descanso, sem manusear celular entre todas as séries.
- Sequência dos exercícios já é conhecida, **não** depende de classificação automática do nome do exercício.
- Feedback no decorrer da sessão faz parte do escopo, não é acessório futuro indefinido.
- Preferência por iniciar sessão pelo relógio, se houver comando utilizável.
- Desejo de personalizar mostrador/interface, investigando limites reais.
- Offline-first obrigatório; treino ao vivo nunca bloqueado pela ausência do backend.
- Desejo de Kotlin + Python + Rust e backend, cada qual com função justificada.
- Ferramentas gratuitas e compatíveis com PC antigo; desenvolvimento sem emulador, usando Android 11 conectado.
- Compatibilidade Moyoung sugerida pelo prefixo `MOY-`; **modelo exato e acesso a amostras de movimento não comprovados**.

## 6. Decisões abertas que dependem dos testes

| Questão | Evidência que resolve | Implicação |
|---|---|---|
| O relógio usa Moyoung v1/v2? | Nome de fabricante em GATT, traço HCI e compatibilidade com Gadgetbridge | Implementar protocolo diretamente ou adaptar. |
| Há leituras BPM ao vivo? | Notificações GATT, frequências e timestamp | Melhora análises, mas isoladamente não delimita séries. |
| Há movimento bruto/detalhado? | Serviço, payload e alterações correlacionadas a movimento | Principal caminho para automatizar detecção. |
| Podemos iniciar treino pelo pulso? | Evento capturado ao tocar ação nativa do relógio | Atalhos/automação; se não, iniciar no celular. |
| Como enviar alerta? | Teste real com notificação personalizada | Da Fit, Gadgetbridge ou implementação própria. |
| Pode existir interface interativa no relógio? | Evidências de runtime SDK ou apps instaláveis específicos ao modelo | Se não, usar mostradores e notificações como interface limitada. |

## 7. Critérios de êxito do projeto

O projeto será considerado útil quando for possível executar uma sessão real com prescrição carregada, registro temporal confiável do que o hardware permitir, indicação de incerteza, alertas não intrusivos, avisos no pulso e histórico analisável. A automação total será avaliada por números reais (erro nos limites das séries, taxa de identificação, confiabilidade da vibração). O alcance final fica condicionado ao hardware demonstrado, não ao nome comercial do produto.

## 8. Referências de investigação (consultadas em 09/10/2026)

- Família Moyoung/Da Fit, lista de recursos e ressalvas: https://gadgetbridge.org/gadgets/wearables/moyoung/
- Estrutura do protocolo Moyoung V1/V2: https://gadgetbridge.org/internals/specifics/moyoung-protocol/
- Automações e ações recebidas de dispositivos: https://gadgetbridge.org/internals/automations/events/
- Inspeção de Bluetooth e logs HCI: https://gadgetbridge.org/internals/development/bluetooth/
- nRF Connect (documentação): https://github.com/NordicSemiconductor/Android-nRF-Connect/blob/main/documentation/README.md
- Permissões Bluetooth Android 11: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions?hl=pt-br
- Comunicação BLE em segundo plano: https://developer.android.com/develop/connectivity/bluetooth/ble/background?hl=pt-br

**Nota de precisão:** os recursos publicados para a família Moyoung **não comprovam** suporte no aparelho `GTS 3 MOY-VLP2-2.04`. Somente testes no dispositivo poderão confirmar.
