# TreinoSmart — requisitos pactuados e status v0.3

## Experiência desejada (visão de produto)

O treino é prescrito previamente pelo professor: ordem dos exercícios, número de séries, repetições, cargas e intervalos. O usuário idealmente inicia no próprio GTS 3, guarda o celular, e o assistente detecta automaticamente início/fim de cada série, cronometra a execução, inicia o descanso, alerta no relógio e acompanha mudanças durante a sessão. Depois, compara previsto e observado, detecta divergências de duração, volume, ritmo e descanso. Correções posteriores devem estar disponíveis. O aplicativo também deve coletar séries temporais úteis e analisar tendências entre sessões; em etapa posterior, explorar sono, exportação Strava e ajustes de descanso a partir de observações confiáveis. Construído para portfólio com Kotlin, Rust, Python, backend e dados, sem dependência da internet durante treino.

## Funciona no protótipo v0.3 (a validar em Android real)

- Editor local de plano (vários exercícios em sequência; séries, reps, carga e descanso previstos; faixa opcional de tempo por série).
- Gerenciador de sessão e fluxo de séries/descansos em serviço Android com notificação persistente.
- Início do descanso após término *registrado*; aviso de descanso concluído no **celular**.
- Alerta em tempo real se a duração em andamento ultrapassar o máximo definido para a série (não equivale a erro técnico).
- Feedback informativo após série quando tempo foge da faixa prescrita ou diverge marcadamente da anterior com mesmo número de reps confirmadas.
- BPM via GATT padrão **quando o relógio transmite** e indicadores de lacuna de coleta.
- Tonelagem somente com reps e carga efetivamente confirmadas; total de séries e duração média.
- Exportação CSV; análise Python; parser Rust parcial; endpoint FastAPI opcional separado.
- Demonstração automática de duas séries de teste com fonte `simulation`.

## Não está resolvido (sem prometer funcionamento)

1. **Detecção automática real da execução/descanso.** Falta um fluxo confiável de sinal de movimento ou padrão esportivo suficientemente granular. BPM sozinho não indica início/fim exatos; somente estudo estatístico de tendências.
2. **Iniciar treino no relógio como gatilho do aplicativo.** Até agora há atividade iniciada no relógio, mas evento BLE para iniciar sessão Android ainda não validado.
3. **Medição cardíaca comandada e mantida pelo Android.** Não são enviados comandos Moyoung de ativação, por segurança do dispositivo.
4. **Vibração garantida no relógio.** Notificação Android não é o mesmo que comando nativo do relógio. Testar retransmissão ou descobrir comando validado.
5. **Interface própria no relógio.** Firmware proprietário sem confirmação de instalação de aplicativo. Mostradores personalizados e atalhos podem ser alternativas, ainda não implementadas.
6. **Dados de sono, SpO2, sensores brutos, integração Strava e sincronização com backend.** Exploráveis futuramente, mas não priorizados para detecção básica.
7. **Persistência/restauração de sessão após morte de processo** e restauração completa de sessão ativa; versões futuras.

## Estratégia técnica para a detecção real

1. Capturar logs `hr`, notificações Moyoung e marcas de série feitas pelo usuário para produzir dados rotulados; validar a qualidade temporal e as desconexões.
2. Pesquisar se o firmware fornece acelerometria/atividade em tempo real. Se não fornecer, avaliar alternativas viáveis sem inventar sensor acessível.
3. Com dados suficientes, comparar classificadores simples de janelas e regras de estado em Python com validação cruzada por sessão (erro de fronteira em segundos, precisão de inícios/fins e alarmes falsos).
4. Implementar classificador local no Android ou Rust FFI **somente se houver vantagem mensurável**.
5. Calibrar feedback por exercício e duração real, com grau de confiança. Não equiparar rapidez total de série a execução errada.

## Dados conhecidos sobre o GTS 3

- BLE: `GTS 3`, fabricante identificado por GATT como `MOYOUNG-V2`, software `MOY-VLP2-2.0.4`, firmware `JLQFNHWQ1.0`.
- `0x180D/0x2A37`: frequencia cardíaca, leitura observada aproximadamente a cada segundo quando sensor ativo.
- `0xFEEA` / `0xFEE3` e características proprietárias: pacotes observados; sem semântica completa confirmada.
- Gadgetbridge com perfil Moyoung experimental sincronizou atividades anteriores, BPM durante atividades e algumas medições de saúde.
- Ativar atividade física no próprio relógio ajudou a disponibilizar BPM para o app v0.1/v0.2.

## Salvaguardas

Não enviar bytes desconhecidos para características proprietárias, não gravar firmware; não inferir execução confirmada a partir de prescrição; não afirmar que o BPM é medido continuamente quando a característica não envia novas amostras. Desconexões devem ser registradas, não preenchidas artificialmente. Trabalhar apenas com dados próprios e consentidos.
