# Regressão: plumbob ao trocar de mundo

## Relato

Ao entrar em `instance-forgotten-temple-goblins`, o servidor lançava:

```text
java.lang.IllegalStateException: Incorrect store for entity reference
    at com.hypixel.hytale.component.Ref.validate(...)
    at com.cookieukw.SimTale.systems.PlumbobSystem.tick(...)
```

O cache por UUID ainda guardava o cristal do mundo anterior. `Ref.isValid()` continua verdadeiro
enquanto essa entidade existe, mas não autoriza acessá-la por outro `Store`.

## Correção

- O tick só reutiliza referências válidas que pertencem ao store atual.
- Uma referência antiga é removida do cache e do índice de cristais rastreados. O limpador de
  órfãos já existente a remove quando o chunk do mundo de origem volta a ser processado.
- Um cristal novo pode ser criado no mundo de destino.
- Despawn imediato só usa `CommandBuffer.removeEntity` quando a referência pertence ao store do
  buffer. Isso também cobre a ocultação do cristal por montaria logo após uma transferência.
- O mundo usado pelo tick vem diretamente do store atual.

## Teste automatizado

`tests/PlumbobWorldTests.java`, incluído em `./gradlew runTests`, usa dois stores ECS reais,
sem inicializar mundos ou um servidor. Reproduz a exceção original e verifica transferência,
retorno, referência inválida, independência entre jogadores e remoção local/estrangeira.

## Validação em jogo (pendente)

1. Com o cristal visível, entrar no Forgotten Temple pelo portal.
2. Confirmar que a instância permanece ativa e não há `Incorrect store for entity reference`.
3. Sair do templo e repetir a entrada/saída várias vezes.
4. Confirmar que o cristal reaparece no mundo atual e que o antigo não permanece duplicado após
   o chunk de origem voltar a ser processado.
5. Repetir com dois jogadores, deixando um no mundo original.
6. Repetir com outro portal/troca de mundo e conferir a ocultação do cristal por montaria.

Não é necessário apagar mundos, instâncias ou dados de NPCs. A correção é no ciclo de vida do
cristal, não nos assets do templo.
