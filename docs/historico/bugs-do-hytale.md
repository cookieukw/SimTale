# Bugs do Hytale (não são nossos)

Achados no motor durante o desenvolvimento do SimTale, para reportar ao Hytale. Ficavam no
`testing_checklist.md` como seção 17, mas não são teste de nada nosso — o checklist é sobre validar
o comportamento do mod, e misturar as duas coisas fazia parecer que havia pendência de teste onde na
verdade havia pendência de *reportar*.

Versão testada: `0.6.0-pre.10`.

---

## 1. Cliente inteiro fecha com `.blockymodel` malformado

**Estado:** 📮 a reportar.

Um `null` num quaternion de `orientation` lança exceção não tratada no carregamento e **mata o
processo do cliente**, sem nenhuma mensagem na tela.

O contraste é o que faz disso um bug e não uma limitação: item e receita inválidos são reportados
direito — caminho do arquivo, motivo, e no caso da receita até o trecho com número de linha e
cursor. Só o caminho do modelo derruba tudo em silêncio.

**Repro:** item de mod com `Model` apontando para um `.blockymodel` que tenha, em qualquer nó:

```json
"orientation": { "x": null, "y": null, "z": null, "w": null }
```

## 2. `Asset validation FAILED with 1 reason(s):` não diz o motivo

**Estado:** 📮 a reportar como comentário secundário, não como bug principal.

A linha seguinte é só `Mod X failed to load`. O motivo real existe no log, mas fica centenas de
linhas acima e intercalado com a saída de outras threads. Custou três boots até achar.

É usabilidade de log, mas do tipo caro: a mensagem que promete explicar é justamente a que não
explica.
