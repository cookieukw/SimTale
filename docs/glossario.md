# Glossário de Termos

> **TL;DR**: Definição rápida de termos técnicos, jargões e conceitos específicos utilizados no desenvolvimento e nas mecânicas do mod SimTale.

---

### Leash Point (Ponto de Coleira)
O mecanismo padrão do Hytale SDK para controlar a movimentação de NPCs. Ele define uma coordenada 3D de destino à qual a IA do NPC deve se dirigir e tentar permanecer próxima. O SimTale manipula esse ponto de forma otimizada (throttling) para mover NPCs em suas rotinas diárias.

### Plumbob
O diamante flutuante tridimensional posicionado diretamente acima da cabeça do NPC ou jogador ativo. Ele rotaciona e muda de cor (verde, azul ou vermelho) em tempo real de acordo com as necessidades e humor do personagem.

### Caskara
Framework ORM de persistência de dados local utilizado no ecossistema de Hytale. Ele gerencia o salvamento e carregamento de classes de dados Java serializadas em arquivos formato JSON na pasta do mod.

### Swaddle (Manta)
O formato e modelo visual do bebê recém-nascido no SimTale. Representa o bebê enrolado de forma compacta em panos de maternidade, permitindo que ele seja carregado no inventário como um item.

### Rules Engine (Motor de Regras)
Padrão arquitetural funcional (Chain of Responsibility) implementado no `InteractionManager` que substitui cadeias complexas de `if/else`. Ele varre uma lista estática de regras ordenadas por prioridade para determinar o desfecho de interações sociais.

### Daily Interactions Cooldown
O limitador de interações diárias. Cada NPC aceita até 3 conversas normais por dia no jogo. Ao atingir esse limite, os ganhos de afinidade são zerados até o dia seguinte para evitar abusos (spam de cliques).

### GrowthStage (Fase de Crescimento)
O estágio de desenvolvimento físico de um filho no mod. Abrange as fases `BABY`, `TODDLER` (criança pequena), `CHILD` (criança), `TEEN` (adolescente) e `ADULT` (adulto), alterando a inteligência, escala física e as necessidades da entidade.
