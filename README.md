# Ceva Club Android

MVP acadêmico de um clube de cervejas, desenvolvido em Kotlin com layouts XML. O app funciona offline e todas as contratações são simuladas.

## Como executar

1. Abra este repositório no Android Studio com suporte ao AGP 9.3. Selecione o JDK 25 como Gradle JDK.
2. Instale o Android SDK Platform 37.0 e o Build Tools 36.0.0 pelo SDK Manager.
3. Sincronize o Gradle e execute o módulo `app` em um emulador ou dispositivo com Android 7.0 (API 24) ou superior.

No terminal, com o SDK configurado:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

No Windows, use `gradlew.bat` no lugar de `./gradlew`. O APK de desenvolvimento é gerado em `app/build/outputs/apk/debug/app-debug.apk`.

## Roteiro de demonstração

1. Crie uma conta com nome, e-mail e senha de pelo menos oito caracteres. Tente cadastrar o mesmo e-mail novamente e confira o feedback.
2. Entre com a conta criada. Pela Home, abra **Minha assinatura** para conferir o estado vazio e volte para responder ao **Quiz cervejeiro**.
3. Responda às cinco perguntas. Confira o perfil e o plano sugerido no resultado.
4. Compare **Essencial**, **Descoberta** e **Reserva**. Escolha um plano e confirme a simulação.
5. Confira o plano, o valor mensal e o status em **Minha assinatura**. Feche e reabra o app para verificar a persistência.
6. Em **Meu perfil**, confira os dados e saia da conta. Entre novamente e verifique o perfil e a assinatura. Teste a troca ou o cancelamento simulado.
7. Use uma segunda conta para verificar que cada uma tem seu próprio resultado e assinatura.

## Como os dados são armazenados

O SQLite guarda contas, respostas, classificação do quiz e estado da assinatura por usuário. A sessão guarda o identificador da conta autenticada localmente e é removida no logout. As senhas são armazenadas como hashes PBKDF2 com salt individual. O quiz usa cinco respostas com pontuação de zero a dois. Totais de 0 a 3 indicam Clássico, de 4 a 6 indicam Explorador e de 7 a 10 indicam Especialista.
