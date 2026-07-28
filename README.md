# SimTale

Um mod de simulação social para **Hytale**. NPCs com personalidade, necessidades, memória e relacionamentos — que moram em casas de verdade, dormem em camas de verdade, trabalham, se apaixonam, têm filhos e crescem.

Pense em *The Sims* rodando dentro do seu mundo de Hytale.

---

## O que o mod faz

**NPCs vivos.** Cada NPC nasce com personalidade própria (gentileza, humor, agressividade, carisma), traços de caráter (`GREEDY`, `SHY`, `LAZY`, `AGGRESSIVE`, `FUNNY`, `PARANOID`, `NEEDY`, `LOYAL`) e gostos imutáveis: comidas favoritas, itens odiados, hobby, clima e estação preferidos.

**Necessidades reais.** Fome, energia, social, diversão e higiene decaem com o tempo. Um NPC com sono procura uma cama livre e dorme nela. Com fome, procura um baú com comida e consome um item de verdade. Sujo, vai tomar banho na água mais próxima. Se a fome zerar, ele morre — e o Grim Reaper aparece para colher a alma.

**Relacionamentos com profundidade.** Afinidade, amizade, romance e confiança são eixos separados: um NPC pode te amar e ainda assim não confiar em você. O status evolui de `STRANGER` até `MARRIED`, e cada interação (conversa, piada, paquera, insulto, presente) é filtrada pelo humor atual, pelos traços e pela memória recente do NPC.

**Vida social entre NPCs.** NPCs carentes procuram outro NPC disponível por perto, caminham até ele e conversam. A conversa restaura a necessidade social dos dois, constrói uma relação NPC↔NPC e contagia humor — alguém feliz anima quem estava pra baixo, alguém bravo azeda o ambiente. NPCs ociosos também dão voltas em torno de casa em vez de ficarem parados.

**Família e ciclo de vida.** Casamento, gravidez com trimestres e sintomas, parto, genética herdada, e crescimento em cinco fases — Bebê → Toddler → Criança → Adolescente → Adulto. Os pais se revezam nos cuidados do bebê automaticamente.

**Casas de verdade.** O mod reconhece estruturas construídas pelo jogador via flood fill 3D, exigindo paredes, teto, porta, luz, assento e mesa. NPCs reivindicam camas dentro dessas casas como residência e passam a proteger seus baús.

**Trabalho.** Nove profissões (Minerador, Fazendeiro, Pescador, Lenhador, Guarda, Explorador, Construtor, Caçador). Fazendeiros colhem plantações e replantam sementes; caçadores rastreiam animais e guardam a carne no baú de casa.

**Diálogo por chat.** Fale o nome do NPC no chat (com tolerância a erros de digitação via Levenshtein) e ele responde. O sistema classifica intenção — elogio, insulto, pergunta pessoal, pedido de ajuda, ordem de trabalho — e responde de acordo com o nível de amizade.

**IA generativa (opcional).** Suporte a Gemini, OpenAI e OpenRouter para respostas livres, com o contexto do NPC (personalidade, humor, necessidades, histórico) montado automaticamente.

**Plumbob.** O diamante flutuante clássico, trocando de modelo conforme o humor do NPC.

---

## Instalação

**Requisitos:**

- Hytale Server `2026.03.26-89796e57b` ou compatível
- **Caskara** `>= 1.0.2` (dependência de persistência)

Coloque o `.jar` gerado em `UserData/Mods` na sua instalação do Hytale.

---

## Build

Requer **JDK 21+** (o projeto é configurado para Java 25).

```bash
./gradlew build        # compila e empacota
./gradlew deploy       # compila e copia direto pra pasta de mods
./gradlew runTests     # roda a suíte de testes
```

O caminho da instalação do Hytale é detectado automaticamente, mas você pode fixá-lo criando um `local.properties`:

```properties
hytale.dir=/caminho/para/Hytale
```

---

## Configuração da IA (opcional)

Na primeira execução o mod cria um `simtale-ai.json` na raiz do servidor:

```json
{
  "enabled": true,
  "provider": "gemini",
  "geminiKey": "sua-chave",
  "openaiKey": "",
  "openrouterKey": "",
  "customUrl": "",
  "customModel": ""
}
```

As chaves também podem vir das variáveis de ambiente `GEMINI_API_KEY`, `OPENAI_API_KEY` e `OPENROUTER_API_KEY`. O campo `provider` aceita `gemini`, `openai` ou `openrouter` — se o provider escolhido não tiver chave configurada, o mod avisa no log e mantém o primeiro disponível.

Com `enabled: false` o mod funciona normalmente usando apenas os diálogos pré-escritos.

---

## Comandos

Todos sob `/simtale`:

| Comando | O que faz |
| :--- | :--- |
| `spawn <tipo>` | Spawna um NPC (`HUMAN_MALE`, `HUMAN_FEMALE`, `CHILD_MALE`, `CHILD_FEMALE`) |
| `interact <nome>` | Abre a UI de interação social |
| `marry` | Força casamento com o NPC mais próximo |
| `forcesleep` | Força o NPC mais próximo a dormir ou acordar |
| `forcepreg` / `forcebirth` | Força gravidez e parto |
| `setstage <fase>` | Pula o NPC para uma fase de crescimento |
| `setmood <humor> <intensidade>` | Define o humor ativo |
| `pregnancy` | Abre a UI de informações da gravidez |
| `housecheck` | Valida se a estrutura em mira é uma casa |
| `chestcheck` | Lista baús registrados na vizinhança |
| `debugbeds` / `debugnear` | Inspeciona camas registradas e NPCs próximos |
| `search <nome>` | Busca NPCs salvos no banco |
| `toggleai <on\|off>` | Liga/desliga a IA de rotina |
| `tpall` / `clearall` | Teleporta ou remove todos os NPCs |

Também existem `/build` (construções) e `/simdebug` (painel de depuração).

---

## Arquitetura

O mod é escrito sobre o ECS do Hytale. Componentes guardam dados, sistemas de tick executam lógica:

```
SimTale (JavaPlugin)
├── core/          Dados do NPC: personalidade, necessidades, relacionamentos, família, memória
│   └── lifecycle/ Gravidez, crescimento, genética, cuidados com bebê
├── systems/       Sistemas de tick e helpers de comportamento
├── logic/         Interações do jogador, páginas de UI, profissões e trabalhos
├── ai/            Provedores de LLM, montagem de contexto, componente de rotina
├── db/            Persistência via Caskara
└── engine/        Minigame de adivinhação (estilo Akinator)
```

O `RoutineAISystem` é uma máquina de estados por NPC (`IDLE`, `FINDING_BED`, `SLEEPING`, `SOCIALIZING`, `WANDERING`, `FARMING`, `HUNTING`...) que delega os fluxos maiores para helpers dedicados: `NPCHungerHelper`, `NPCWorkHelper`, `NPCSocialHelper` e `NPCMovementHelper`.

Para evitar varreduras globais caras, os NPCs ativos ficam em listas estáticas (`SimTale.ACTIVE_NPCS`) em vez de serem buscados no mundo a cada tick.

---

## Documentação

A documentação técnica completa está em [`docs/INDICE.md`](docs/INDICE.md), organizada por sistema — arquitetura, IA de rotina, reconhecimento de casas, ciclo de vida, persistência, além do histórico de bugs e das decisões arquiteturais.

O [`testing_checklist.md`](testing_checklist.md) tem o roteiro de testes manuais in-game.

---

## Estado do projeto

Em desenvolvimento ativo. O quadro de recursos implementados e pendentes está em [`simtale_documentation.md`](simtale_documentation.md), e o roadmap do sistema social em [`.planning/features/social_system_roadmap.md`](.planning/features/social_system_roadmap.md).

Faltando hoje: envelhecimento e morte natural, divórcio, sistema de fofoca e restauração da necessidade `fun`.

---

## Créditos

Feito por **cookieukw**. Persistência via **Caskara**.
