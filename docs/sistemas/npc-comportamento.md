# Sistema de Comportamento de NPC (Preferências e Atributos)

> **TL;DR**: Controla a individualidade de cada NPC, definindo traços de personalidade, humor flutuante, e gostos imutáveis (comidas/itens preferidos ou odiados, estações e climas favoritos). Esses dados afetam interações sociais, atribuições de trabalho e presentes.

---

## 1. O que é e para que serve
O sistema de comportamento de NPC é o núcleo de individualidade do SimTale. Ele garante que os personagens não ajam de forma idêntica. Cada NPC gerado recebe uma combinação de traços de personalidade, gostos por comidas e itens, hobbies, climas favoritos e profissões preferidas/detestadas. Esse conjunto dita a afinidade ganha ao presentear, a chance de aceitar ou rejeitar um trabalho, e o estilo de suas falas e reações de humor.

---

## 2. Como funciona por dentro
A individualidade do NPC é estruturada em três classes principais contidas no pacote `com.cookieukw.SimTale.core`:

```
SimNPCComponent
├── Personality (Atributos: gentileza, agressividade, humor, carisma + Traços)
├── NPCPreferences (Gostos de comidas/itens, clima, estação, profissões)
└── Mood (Estado emocional ativo: HAPPY, SAD, ANGRY)
```

1.  **`Personality`**: Define os coeficientes de personalidade (escala de 0 a 100) e armazena os traços ativos (`Trait`): `GREEDY` (ganancioso), `PARANOID` (paranoico), `LAZY` (preguiçoso), `SHY` (tímido), `FUNNY` (engraçado) e `AGGRESSIVE` (agressivo).
2.  **`NPCPreferences`**:
    *   Gerado via `createRandom()` de forma imutável ao instanciar o NPC.
    *   Armazena `Set<String>` com 2 a 3 alimentos e itens favoritos, e 2 a 3 odiados.
    *   Contém enums de climas (`Weather`), estações (`Season`) e passatempos (`Hobby`).
    *   Contém os sets `likedProfessions` e `dislikedProfessions` que influenciam na hora do jogador sugerir uma profissão.
3.  **`Mood`**: O humor ativo do NPC. Ele oscila baseado nas interações: dar presentes que ele ama ativa `HAPPY` por tempo prolongado, enquanto insultos ou presentes odiados disparam `SAD` ou `ANGRY`.

---

## 3. Decisões de Design e Por Quê
*   **Imutabilidade das Preferências**: As preferências do NPC são finais (`final Set<String>`) e inicializadas no construtor.
    *   *Por quê*: A imutabilidade garante que os gostos do NPC não mudem acidentalmente durante operações ou clonagens no ciclo de vida (como o crescimento de criança para adulto).
*   **Tradução em Código**: Mantemos um switch-case centralizado `getFoodDisplayName(itemId)` dentro de `NPCPreferences.java` mapeando os nomes dos itens em português.
    *   *Por quê*: Como Hytale é um jogo multilíngue em desenvolvimento, as IDs de itens no backend do servidor são universais e estáticas. Centralizar as traduções em Java garante que as UIs e o prompt da IA Generativa exibam strings amigáveis de forma consistente, sem precisar encher o arquivo `.lang` do cliente com todas as variações de IDs de itens de Hytale.

---

## 4. Nível de Complexidade e Robustez
*   **Nível**: 🟢 Simples e estável
*   **Análise**: Uma vez gerados e persistidos no banco de dados, os campos são apenas de leitura para os sistemas de interação e conversação. Não há lógica de ticks complexa operando sobre a personalidade.

---

## 5. Dificuldades Encontradas e Resolvidas

### Perda de gostos e preferências ao crescer/clonar NPCs
*   **Sintoma**: Sempre que uma criança subia de estágio ou o NPC sofria modificações, ele perdia seus gostos gerados (ficava em branco ou resetava para novos valores).
*   **Causa Raiz**: O construtor de clonagem em `SimNPCComponent.java` criava uma instância em branco de `NPCPreferences` e usava `.addAll()` para copiar os dados. Como a classe foi refatorada para usar `Set` imutáveis e campos finais, o construtor antigo causava erros silenciosos de clonagem.
*   **Correção**: O construtor de clonagem do componente foi atualizado para invocar o novo construtor parametrizado de `NPCPreferences`, repassando os sets originais de forma segura:
    ```java
    clone.preferences = new NPCPreferences(
        preferences.getFavoriteFoods(),
        preferences.getHatedFoods(),
        // ...
    );
    ```

---

## 6. Pontos em Aberto / Dívida Técnica
*   **Hobby Inativo**: O hobby (`Hobby`) é sorteado com sucesso na criação, mas ainda não se traduz em ações físicas na IA autônoma (ex: um NPC com hobby de pescaria não vai ativamente pescar no rio de forma autônoma).

---

## 7. Perguntas Frequentes (FAQ)

### Como a IA generativa sabe dos gostos do NPC?
O construtor do prompt (`NpcContextBuilder`) lê as preferências do componente do NPC e as anexa estruturalmente nas diretivas de sistema do prompt enviado ao LLM (ex: "Você odeia Torta de Maçã e ama Barra de Ouro"). Isso força a IA a falar e responder de acordo com esses parâmetros.

### Um NPC pode gostar e odiar o mesmo item?
Não. O gerador aleatório `createRandom()` executa uma subtração lógica (`subtract()`) do pool de itens favoritos antes de selecionar os itens candidatos para a lista de odiados, prevenindo sobreposição de preferências.
