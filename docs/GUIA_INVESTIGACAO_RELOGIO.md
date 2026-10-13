# Guia prático — Investigação Bluetooth do smartwatch GTS 3 / MOY-VLP2-2.04

**Versão:** 0.1 | **Ambiente:** smartphone Android 11, relógio usando Da Fit, PC Windows 10.  
**Objetivo:** verificar sinais recebíveis, capacidade de vibração/notificação, comandos do relógio para o celular e limites de personalização, sem alterar o firmware.

## Regra inicial: não escrever comandos desconhecidos

Nesta fase vamos **observar**, não modificar firmware ou parâmetros críticos. NÃO usar recursos como `DFU`, `Firmware update`, `Flash`, `OTA`, `Upload`, `Write` para características desconhecidas, nem tentar instalar arquivos de relógio de outros modelos. Isso pode desconfigurar o dispositivo. A leitura de serviços e características e a escuta de notificações conhecidas são nossa prioridade.

## TESTE 0 — Documentar o aparelho (5–10 minutos)

Com o relógio carregado e conectado no Da Fit:

1. Anotar o nome comercial da caixa, se existir, e o nome exibido na aba Dispositivo do Da Fit.
2. No relógio, abrir **Configurações → Sobre / Informações** e registrar exatamente `GTS 3 MOY-VLP2-2.04` ou o que de fato aparecer; se houver outro campo de firmware/hardware, copiar.
3. No Da Fit, observar se existem as opções de notificações, atualização, intervalos de BPM e mostradores.
4. Verificar no relógio a presença de **Encontrar celular**, **Controle de música**, **Controle de câmera**, **Cronômetro**, **Temporizador**, **Esportes**, **Botão lateral**. Só anotar, sem assumir que os eventos são programáveis.
5. Anotar se Bluetooth do Android mostra **um ou dois dispositivos** relacionados ao relógio. Em relógios com chamadas Bluetooth, uma das conexões pode ser de áudio e outra de dados. Não confundir os perfis.

**Resultado desejado:** inventário confiável do modelo e dos recursos que já existem.

## TESTE 1 — Inspeção BLE com nRF Connect, sem programar

Aplicativo de diagnóstico: **nRF Connect for Mobile**, da Nordic Semiconductor. Documentação oficial: https://github.com/NordicSemiconductor/Android-nRF-Connect/blob/main/documentation/README.md

1. Instalar o nRF Connect for Mobile pela Google Play Store, verificando editor **Nordic Semiconductor**.
2. No Android 11, ativar Bluetooth e **Localização** nas configurações e conceder permissão de localização ao nRF Connect. No Android 11, a descoberta BLE normalmente depende disso.
3. Deixar o smartwatch próximo e acionar **SCAN** no nRF Connect.
4. Procurar nomes compatíveis com `GTS 3`, `MOY`, `Zeblaze` ou similares. Não presumir que o nome comercial aparecerá exatamente.
5. Anotar **nome anunciado**, tipo de anúncio BLE, intensidade aproximada de sinal, serviços anunciados e *Manufacturer Data* (se houver). Alguns desses dados podem estar ausentes. Evite divulgar endereço MAC real publicamente.
6. Tentar **CONNECT** somente se o app conseguir fazê-lo sem desconectar indevidamente o uso normal. Se o Da Fit estiver mantendo uma sessão que impede outra conexão, **não concluir que BLE é incompatível**; primeiro registrar que houve conflito.
7. Quando for possível conectar, usar a opção **Discover services** ou a lista automática de serviços. Abrir e anotar os serviços UUID e características com os atributos `READ`, `NOTIFY`, `INDICATE` e `WRITE`.
8. Se houver `0x180A` (Device Information Service), ler **características legíveis** como `0x2A29` (Manufacturer Name) e `0x2A26` (Firmware Revision) quando disponíveis.
9. Se houver `0x180D` (Heart Rate Service), observar se contém `0x2A37` com `NOTIFY`. **Não é obrigatório** que relógios proprietários publiquem o BPM por esse serviço padrão.
10. Nas características explicitamente identificadas e seguras para leitura, acompanhar alterações passivas; **não habilitar envio de comandos proprietários nem usar WRITE**.

**O que significa cada resultado:**
- **Viu GTS 3 no scan:** o dispositivo anuncia alguma interface BLE; isso **não confirma** acesso a sensores.
- **Viu `0x180D` / `0x2A37`:** há caminho possível para notificações de BPM, mas ainda é preciso comprovar fluxo real.
- **Viu apenas UUIDs proprietários:** pode continuar perfeitamente viável por Moyoung, exigindo decodificação.
- **Não conectou com Da Fit ativo:** pode ser conflito de conexão e não bloqueio definitivo.
- **Não apareceram leituras do acelerômetro:** não prova que o acelerômetro não existe; apenas que não foi disponibilizado por esse caminho.

**Registre em captura de tela:** dispositivo na busca, lista de serviços GATT, características relevantes, nome do fabricante (se legível). Oculte MACs e dados pessoais antes de publicar.

## TESTE 2 — Confirmar saída de alertas (Da Fit → relógio)

1. Deixar relógio vinculado ao Da Fit e permitir notificações no sistema Android e no Da Fit.
2. Usar um aplicativo comum para enviar a si mesmo uma notificação de teste e verificar **texto, latência e vibração**.
3. Futuramente, quando nosso APK existir, habilitar especificamente notificações dele no Da Fit e disparar o texto `Descanso concluído — próxima série`.
4. Anotar se aparecem notificações mesmo com **tela apagada**, e se a economia de bateria ou o modo não perturbe interfere.

**Critério de sucesso:** uma notificação do nosso app produz vibração e/ou texto confiável no relógio. Se falhar, investigar a configuração; não considerar o caminho comprovado antes do teste com o APK.

## TESTE 3 — Verificar Gadgetbridge (somente depois de documentar Da Fit)

Documentação da família: https://gadgetbridge.org/gadgets/wearables/moyoung/  
Pareamento: https://gadgetbridge.org/basics/pairing/  
Site oficial: https://gadgetbridge.org/

1. Salvar qualquer dado que você queira preservar no Da Fit antes de mudar o pareamento.
2. Obter o **Gadgetbridge original por Freeyourgadget** em um link oferecido pelo site oficial. Há avisos oficiais sobre aplicativos de mesmo nome; confira o desenvolvedor.
3. Seguir as orientações de pareamento do Gadgetbridge, que podem exigir desvincular o relógio do Da Fit e remover o pareamento anterior do Android.
4. Na descoberta, habilitar **dispositivos não suportados** se necessário. A documentação da família Moyoung indica que modelos não listados podem funcionar por protocolo semelhante.
5. Se houver tentativa de selecionar um modelo genérico/compatível, não presumir que todas as funções vão aparecer. Registrar qual perfil foi escolhido e o resultado.
6. Verificar: (a) conexão; (b) bateria; (c) BPM histórico ou ao vivo; (d) rastreamento de atividade ao vivo; (e) notificação/vibração; (f) opções de HR; (g) sincronização de treinos; (h) eventos do relógio.
7. Se falhar, registrar a mensagem exata de erro e o que estava habilitado. É possível retornar ao Da Fit por novo pareamento; a sequência varia por fabricante.

**Importante:** `Live activity tracker` no Gadgetbridge **não é sinônimo** de dados brutos do acelerômetro. Precisamos verificar o conteúdo e a resolução temporal das amostras recebidas.

## TESTE 4 — Investigar iniciar treino pelo próprio relógio

Três experimentos, todos **sem alterar firmware**:

A. **Encontrar celular:** pressionar essa função e observar se o celular reage. Se Gadgetbridge estiver conectado, verificar se a ação pode gerar broadcast/configuração por automações (funcionalidade depende do aparelho).

B. **Controle de música:** acionar play/pause no relógio e verificar se algum aplicativo do smartphone recebe evento. No futuro, poderemos testar uma `MediaSession` Android própria como alvo, mas não há garantia de que o relógio controle o aplicativo que desejamos.

C. **Controle de câmera / botão físico:** verificar se o comportamento gera um evento identificável do lado do telefone. Reaproveitar eventos só será viável se pudermos observar e direcionar o sinal; não substituir o aplicativo da câmera às cegas.

**Resultado:** classificar cada comando em `observável no Android`, `somente recurso fechado do Da Fit`, `não encontrado`. Um atalho que inicia a sessão no celular pode resolver a experiência no pulso mesmo sem app instalável no relógio.

## TESTE 5 — Investigar a interface do relógio

1. Abrir no Da Fit a área de **mostradores / watch faces**.
2. Conferir se permite apenas fotografia de fundo, personalização de elementos estáticos ou editor mais completo.
3. Conferir se algum mostrador possui **botões/atalhos interativos configuráveis** (não presumir).
4. Procurar no próprio relógio evidência de loja de aplicativos ou SDK específico. Não confundir 'loja de watch faces' com 'loja de aplicativos'.
5. Anotar em três níveis: **A)** mostrador editável; **B)** comandos de funções nativas reaproveitáveis; **C)** aplicativos instaláveis. Nível C não está comprovado para este relógio.

Não testar firmware de outro modelo ou arquivos não oficiais para tentar habilitar apps.

## TESTE 6 — Investigação avançada via registros HCI, opcional

Somente se os testes anteriores não esclarecem o protocolo e você estiver confortável com depuração. A documentação do Gadgetbridge descreve a técnica: https://gadgetbridge.org/internals/development/bluetooth/

1. Em **Configurações → Sobre o telefone**, habilitar *Opções do desenvolvedor* pelo procedimento do seu Android (normalmente tocar repetidamente no número da versão).
2. Nas opções, ativar **Bluetooth HCI snoop log** e depuração USB se você usar ADB.
3. Desligar e religar Bluetooth, caso necessário, e então realizar **uma ação por vez** no Da Fit: conectar, medir BPM, alterar mostrador/abrir esporte, localizar telefone.
4. Desativar o registro quando terminar.
5. Se o fabricante permitir, usar **ADB bugreport** (`adb bugreport relatorio.zip`) para recuperar logs, que podem ser analisados com Wireshark usando filtro `btatt`. Em alguns aparelhos, o HCI log não estará acessível sem recursos adicionais. Não é necessário root para tentar o bugreport.
6. Comparar as mensagens observadas entre ações; **não** escrever mensagens arbitrárias de volta no relógio.

**Privacidade:** HCI e bugreport podem conter endereços, identificadores de dispositivos, trechos de mensagens e informações sensíveis. **Nunca publicar o ZIP bruto no GitHub** nem compartilhá-lo inteiro. Extraia só capturas/hex relevantes e remova identificadores. Desligue o log após o teste.

## Como avaliar se há sinal suficiente para detectar séries

Para cada fonte encontrada, preencher:

| Fonte | Existe? | Amostras/segundo ou intervalo | Latência | Dados brutos ou resumidos? | O que poderíamos inferir? |
|---|---|---|---|---|---|
| BPM | ? | ? | ? | ? | Resposta cardiovascular, mas não delimitação exata por si só |
| Movimento do pulso | ? | ? | ? | ? | Potencial para execução vs descanso |
| Eventos de botões | ? | Por evento | ? | Evento | Início/parada pelo relógio |
| Dados de atividade esportiva | Sim, relógio mostra corrida | Verificar transmissão | ? | Desconhecido | Pode ser apenas resultado final |
| Notificações para relógio | Usuário já observou | Por evento | A medir | Texto/vibração | Alertas e instruções |

**Os primeiros resultados que precisamos:** (1) captura do menu Sobre; (2) captura do dispositivo no scan do nRF Connect; (3) captura da lista de serviços GATT, se acessível; (4) relação de comandos nativos vistos no relógio; (5) resultado de vibração por notificação.

## O que NÃO concluir precocemente

- Medir corrida/pace não comprova acelerômetro bruto acessível; pace pode vir de passos, GPS assistido pelo celular e algoritmos internos.
- Receber notificação não significa que é possível instalar aplicativo no relógio.
- Suporte à família Moyoung não significa suporte garantido ao firmware `MOY-VLP2-2.04`.
- Um app nRF Connect não mostrar amostras não exclui protocolo proprietário.
- Alterar imagens de watchface não equivale a criar interface interativa com navegação.
- BPM pode chegar por sincronização histórica, não em tempo real.

## Referências técnicas

- https://gadgetbridge.org/gadgets/wearables/moyoung/
- https://gadgetbridge.org/internals/specifics/moyoung-protocol/
- https://gadgetbridge.org/basics/pairing/
- https://gadgetbridge.org/internals/automations/events/
- https://gadgetbridge.org/internals/development/bluetooth/
- https://github.com/NordicSemiconductor/Android-nRF-Connect/blob/main/documentation/README.md
- https://developer.android.com/develop/connectivity/bluetooth/bt-permissions?hl=pt-br
