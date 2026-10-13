# Status — v0.4

## Evidência confirmada em testes anteriores
- Firmware do relógio: MOYOUNG-V2 / MOY-VLP2-2.0.4.
- O serviço BLE de BPM 0x180D/0x2A37 transmitiu valores ao app TreinoSmart quando o relógio estava em modo ativo de medição ou exercício.
- Histórico esportivo e SpO2 foram vistos no Gadgetbridge, com perfil experimental.

## Novas experiências incluídas no código
1. Detector de eventos temporais a partir de janelas RMS do acelerômetro do **celular**. Baseado em limiares heurísticos; NÃO prova execução de exercícios e NÃO conta repetições.
2. Transições automáticas `READY/REST -> ACTIVE -> REST` (ou FINISHED) com timestamps estimados e fonte explícita `phone_motion_estimate`.
3. Cronômetro do descanso disparado pela transição estimada, com notificações locais.
4. Encaminhamento de notificações ao Gadgetbridge por Intent PebbleKit, dependente de conexão e configuração.
5. Registro de janelas `phone_motion_1s`, candidatos `automatic_candidate`, eventos e BPM (quando transmitidos).

## Limitações, próximos experimentos
- Gravar vídeos ou anotações para estabelecer ground truth de início/fim **sem incluir dados pessoais no repositório público**.
- Comparar erro de timestamps e taxa de detecções falsas por exercício e local de transporte do telefone.
- Avaliar separadamente se o Gadgetbridge encaminha avisos ao relógio quando controla a conexão.
- Incorporar fonte de movimento do relógio **somente após descobrir streaming documentado ou validar engenharia reversa**.
- Confirmar se existe comando de vibração direto seguro para o GTS 3, sem escrita proprietária desconhecida.
- Futuro: sincronização offline-first com FastAPI/PostgreSQL, detector robusto em Rust/Python, análise longitudinal de sono.
