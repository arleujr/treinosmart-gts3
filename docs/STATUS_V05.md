# Estado v0.5

## Comprovado pelo usuário até v0.4
- Conexão BLE direta Android -> GTS 3.
- Leitura de HR 0x2A37 quando medição no relógio/mode esportivo ativa.
- Compilação em GitHub Actions após correção do MainActivity.

## Implementado neste código, mas ainda não confirmado no GTS 3
- Comandos Moyoung 0x68, 0x6D enviados via 0xFEE2 em enquadramento V2.
- Tentativa de vibração por 0x61 (localizar dispositivo).
- Treino com pedido de BPM dinâmico no início; detector experimental baseado apenas em BPM; descanso estimado e aviso Bluetooth.
- Tela visual conceito em `watchface/preview.html` (não instalada, sem upload).

## Faltando
- Compilar v0.5 no Android SDK (aqui não disponível) e testar comandos no GTS 3.
- Validar taxa, estabilidade e latência das amostras com comandos.
- Validar precisão da detecção comparada ao registro manual.
- Projetar formato de mostrador original do firmware deste GTS 3; investigar interação sem alterar firmware.

Nenhuma dependência de apps intermediários na execução. Documentação pública do protocolo usada só como referência, não como app instalado.
