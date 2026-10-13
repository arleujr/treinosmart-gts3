# Interface TreinoSmart no pulso: investigação sem gravar firmware

Objetivo acordado: iniciar sessão e ver estado da série, duração, descanso, BPM e alertas no GTS 3 sem manusear o telefone.

O GTS 3 usa MOYOUNG-V2, mas não foi identificada API para instalar apps independentes nele. Os mostradores `.bin` podem ter campos atualizáveis via comandos Moyoung em alguns modelos: https://github.com/jphein/moyoung-watch . A compatibilidade do GTS 3 com firmware `MOY-VLP2-2.0.4` não foi verificada, e outros GTS 3 têm variantes de firmware e resolução diferentes.

O arquivo `preview.html` é um **protótipo visual local**, não um arquivo instalável no relógio e não realiza upload. É apenas para alinhar posição de exercício, série, descanso e BPM. Antes de gerar um `.bin`, precisaríamos de uma cópia do mostrador ORIGINAL compatível com este firmware, determinar formato e dimensões reais, e validar campos dinâmicos no relógio. Não modificar firmware principal nem tentar upload de `.bin` não validado.
