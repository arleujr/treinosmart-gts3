# Compilar e testar v0.3 pelo GitHub (sem Android Studio)

1. Descompacte o ZIP. Envie para a **raiz** do repositório o conteúdo de dentro do ZIP. No navegador, o upload de arquivos pode não preservar automaticamente a pasta oculta `.github`: confirme em **Code** se existe `.github/workflows/gerar-apk.yml`.
2. Clique em **Actions → Gerar APK Android**. Use **Run workflow** caso ainda não apareça execução.
3. Clique na execução. **Verde** significa que compilou; **vermelho** significa erro — abra a etapa vermelha, copie o trecho do erro e envie para correção.
4. Ao final da execução verde, em **Artifacts**, clique em `TreinoSmart-APK-debug` (logado na sua conta GitHub). O download será ZIP.
5. Extraia `app-debug.apk`, passe ao celular por cabo USB ou diretamente por download, abra pelo gerenciador de arquivos e instale.
6. Abra o TreinoSmart; teste primeiro a **Demo automática** (não requer relógio) e **Testar aviso**. Depois configure a planilha, conecte o GTS 3 e teste uma sessão real com marcação manual.
7. A nova compilação pode ter assinatura de debug diferente. Se o Android não instalar sobre a anterior, **exporte os dados da v0.2**, desinstale-a e instale a v0.3.

## O que relatar

- Abre sem erro? A demonstração passa de série → descanso → série e conclui? O aviso toca/vibra no celular?
- O BPM ainda aparece quando você inicia modo atividade no GTS 3?
- O serviço registra os eventos com a tela bloqueada?
- O CSV foi exportado? Se quiser compartilhar, elimine informações pessoais ou envie apenas exemplos fictícios.

Não envie ao repositório público CSV com medidas pessoais.
