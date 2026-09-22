---
sidebar_position: 2
title: Instalação
---

# Instalação

## Requisitos

| Item                     | Versão     | Obrigatório                                   |
| ------------------------ | ---------- | --------------------------------------------- |
| Servidor Hytale          | `>= 0.6.0` | sim                                           |
| Caskara (banco de dados) | `>= 3.0.0` | sim                                           |
| RuneCore                 | 1.0.12     | sim — usado para aplicar dano e cura aos NPCs |
| Java                     | 25         | apenas para compilar (build)                  |

:::info De onde vêm esses números
`ServerVersion` e `Dependencies` no `manifest.json` do mod. A dependência do RuneCore não
está declarada lá, mas o código chama `com.cookie.runecore.api.StatHelper` — sem esse jar, a fome
não consegue tirar nem restaurar a vida.
:::

## Instalar

1. Solte `SimTale-1.0.0.jar`, `Caskara.jar` e `RuneCore-1.0.12.jar` na pasta `Mods/` do servidor.
2. Inicie o servidor.
3. Entre em um mundo e crafte seu primeiro Contrato de Imigração.

```
[SimTale] Scan found 2 new beds and 1 new chests. Totals: 2 beds, 1 chests
```

Essa linha é o rastreio de móveis que roda quando você entra em um mundo. Ela só aparece quando encontra
algo novo.

## Compilando a partir do código (Build)

```bash
./gradlew deploy
```

A task `deploy` compila o jar e o copia para a pasta `Mods` do Hytale.

:::warning Feche o jogo antes de compilar
A cópia é atômica justamente para evitar problemas, mas compilar com o jogo fechado ainda é mais seguro.
O Hytale monitora a pasta `Mods` e recarrega o mod sozinho quando o arquivo muda — com um
jar de 11 MB, um recarregamento disparado no meio da gravação gerou um erro `ZipException: invalid LOC header` e nenhum NPC foi carregado.
:::

## Verificando se funciona

Entre no jogo, faça (craft) um **Contrato de Imigração** (Immigration Contract), e use-o.

Se um NPC aparecer com um nome próprio e um diamante flutuando sobre a cabeça (o _plumbob_,
que mostra o humor), o mod está rodando.

## Desinstalação

Remova o jar da pasta `Mods`. Os dados dos NPCs continuam no banco de dados do Caskara; reinstalar o mod trará todo mundo
de volta.
