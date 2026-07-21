# Sistema Plumbob (Diamante Flutuante)

> **TL;DR**: O `PlumbobSystem` renderiza e atualiza dinamicamente o icônico diamante flutuante (plumbob) acima da cabeça dos NPCs e do jogador selecionado, mudando de cor em tempo real para refletir o humor ativo.

---

## 1. O que é e para que serve
O Plumbob é o indicador visual definitivo do estado dos personagens no SimTale, diretamente inspirado em *The Sims*. Ele flutua acima da cabeça do NPC com quem o jogador está interagindo ou acima do próprio jogador, e serve para dar feedback visual imediato sobre a saúde e o humor do personagem.

---

## 2. Como funciona por dentro
O sistema é controlado pela classe `PlumbobSystem.java` e funciona anexando um modelo visual secundário (`simtale:Plumbob`) acima da cabeça da entidade principal:

```
Plumbob System Tick
├── Identifica a entidade alvo (NPC conversando ou Jogador ativo)
├── Lê o humor ativo (HAPPY, SAD, ANGRY) ou nível de necessidades
├── Atualiza a cor (Verde, Azul, Vermelho)
└── Rotaciona e translada o modelo flutuante no eixo Y
```

*   **Modelo 3D**: O plumbob é um modelo customizado contendo animações de rotação contínua e oscilação vertical (sobe e desce suavemente).
*   **Controle de Cores**: As cores do plumbob são vinculadas ao humor da entidade:
    *   🟢 **Verde** (Padrão / Saudável / HAPPY)
    *   🔵 **Azul** (Triste / SAD)
    *   🔴 **Vermelho** (Fome crítica / Bravo / ANGRY)
*   **Posicionamento**: O sistema calcula a altura vertical da entidade com base no seu modelo e escala visual atual para garantir que o diamante fique sempre posicionado exatamente acima do topo da cabeça, sem entrar na malha 3D da entidade.

---

## 3. Decisões de Design e Por Quê
*   **Uso de Model Attachment Nativo**: O plumbob é acoplado como um anexo de modelo (`ModelAttachment`) nativo de Hytale.
*   **Por quê**: Utilizar o sistema de attachments nativo da engine permite que o plumbob acompanhe automaticamente o movimento de caminhada, pulo e rotação do NPC sem que o servidor precise sincronizar pacotes de posição adicionais a cada tick, economizando banda de rede.

---

## 4. Nível de Complexidade e Robustez
*   **Nível**: 🟢 Simples e estável
*   **Análise**: Sistema independente baseado apenas no estado de humor e na anexação visual. Muito seguro e com impacto nulo de performance.

---

## 5. Dificuldades Encontradas e Resolvidas
*   **Posicionamento em NPCs de Escalas Diferentes**:
    *   *Sintoma*: NPCs em fase de crescimento (como adolescentes e crianças) ficavam com o plumbob flutuando muito acima de suas cabeças ou enfiado dentro do peito.
    *   *Correção*: O sistema foi ajustado para ler dinamicamente o componente de escala (`GrowthComponent`) e multiplicar o offset vertical do anexo pelo fator de escala visual correspondente à fase de vida do NPC.

---

## 6. Pontos em Aberto / Dívida Técnica
*   **Sincronização de Brilho**: O plumbob atualmente usa uma textura padrão. Implementar um shader emissivo (que brilha no escuro) para que ele seja visível à noite está na lista de desejos visuais de assets.

---

## 7. Perguntas Frequentes (FAQ)

### O plumbob é visível para todos os jogadores do servidor?
Sim. Sendo um anexo de modelo registrado no lado servidor, Hytale replica o plumbob visual para todos os clientes conectados próximos, permitindo que outros jogadores vejam o estado de humor dos NPCs ao redor.

### Como o plumbob do jogador funciona durante a gravidez?
Quando a jogadora está nos estágios finais de gravidez (stamina baixa), o plumbob muda dinamicamente para vermelho ou azul para sinalizar a exaustão física, servindo de alerta visual.
