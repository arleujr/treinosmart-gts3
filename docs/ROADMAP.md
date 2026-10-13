# Roadmap técnico fundamentado em dados reais

## Objetivo do produto (inalterado)

Plano fornecido pelo professor, início de sessão pelo relógio se possível, detecção automática de períodos de série/descanso, notificações/vibração, feedback contextual durante o treino, avaliação posterior de duração, volume planejado versus confirmado, consistência e histórico. Dormir/recuperação serão estudos longitudinais futuros, não prescrição clínica. Aplicativo Android offline-first com backend Python e ferramentas Rust.

## Estado do hardware — evidências versus hipóteses

| Estado | Informação |
|---|---|
| Confirmado | GTS 3 BLE MOYOUNG-V2, serviço HR 0x180D, notificação 0x2A37 com ~1 BPM/s quando medição acionada no pulso. |
| Confirmado | Recepção de notificação em FEE3; o significado das mensagens proprietárias não está totalmente decifrado. |
| Confirmado | Gadgetbridge perfil experimental consegue acessar atividades passadas e executar medição pontual. |
| Ainda não confirmado | BPM BLE contínuo durante modo Academia, acionamento automático do sensor via comando, estabilidade com tela desligada. |
| Ainda não confirmado | Acelerômetro bruto, cadência individual, evento nativo de iniciar treino, watchface com comandos ou apps instaláveis no relógio. |

## Desenvolvimento por entregas

1. **Coleta BLE Android (versão 0.1):** validada no Android 11 real: conexão e BPM recebidos quando o relógio mede.
2. **Serviço em primeiro plano (versão 0.2):** implementado modo `connectedDevice`, recepção no serviço com tela apagada, logs de silêncio e lacunas. **Ainda requer validação no aparelho real**. Room e motor de descanso em segundo plano continuam pendentes.
3. **Controle cardíaco sem relógio:** estudar apenas comandos documentados do Gadgetbridge Moyoung V2; implementar teste de início/parada no aplicativo, com reconhecimento de perfis. Nunca enviar bytes de firmware/dados desconhecidos.
4. **Movimento:** analisar qual sinal temporal a interface GATT ou protocolo realmente oferece, e qual sua resolução e latência. Se não houver acelerômetro, avaliar outros sinais e um método alternativo não intrusivo. Reconhecer limites dos dados.
5. **Detecção automática:** máquina de estados (preparado/executando/descansando), segmentação estatística e depois classificação em Python com dados rotulados. Validar início/fim contra marcações manuais.
6. **Alertas em tempo real:** avisos confiáveis, diferenciando tempo observado de erro de execução; envio para o relógio por notificações conforme compatibilidade do encaminhamento.
7. **Backend:** FastAPI com ingestão idempotente, PostgreSQL com sessões/eventos/samples, sincronização offline-first e histórico; um frontend web pode ser feito para análises.
8. **Rust como componente real:** evoluir decodificador de frames, testes e codec documentado; avaliar biblioteca FFI para decodificação compartilhada em Android/servidor se simplificar (não obrigatório no primeiro APK).
9. **Extras:** watchface customizada, gatilhos no relógio, sono, exportação para Strava (avaliar políticas da API e disponibilidade).

## Critérios de sucesso da detecção

- Erro absoluto nos timestamps de início/fim da série em relação à referência manual.
- Precisão e recall de janelas classificadas como execução.
- Taxa de falsos inícios (andar, beber água, mexer no equipamento).
- Cobertura de dados, duração e número de reconexões por sessão.
- Zero série inventada quando o sinal está ausente.

## Arquitetura final pretendida

```
GTS 3 --BLE--> Android Kotlin (BluetoothGatt + foreground service)
                           |-- motor local de estados/inferencia
                           |-- dados em Room; notificacoes
                           +-- fila de sincronizacao -> FastAPI -> PostgreSQL
                                                        +-- Python analytics/ML
                                                        +-- Rust parser/protocolo
```

## Confiabilidade

A leitura de BPM pode atrasar ou falhar durante movimento. Não inferir estado clínico de pressão, oxigenação ou recuperação somente a partir do relógio. Alertas de ritmo devem ser formulados como hipóteses baseadas em tempo, sem acusar execução incorreta. Em caso de perda BLE, registrar lacuna e continuar cronômetro pelo tempo monotônico do Android; não interpolar limites desconhecidos fingindo certeza.
