# Registros de Decisões Arquiteturais (ADRs)

> **TL;DR**: Documento que cataloga as principais decisões de design técnico tomadas durante o desenvolvimento do SimTale (ADRs), detalhando o contexto, alternativas consideradas e os motivos da escolha.

---

## ADR 01: Cadeia de Regras Funcionais (Rules Engine) para Interações sociais

### Contexto
As interações sociais com NPCs (dar presentes, conversar, sugerir profissões, piadas, cantadas) contêm regras de negócios complexas baseadas no humor do NPC, traços de personalidade, nível de amizade, compatibilidade e rolagens aleatórias.
Anteriormente, isso era tratado por cadeias aninhadas de `if/else if/else`. À medida que novos traços de personalidade e novos tipos de presentes foram adicionados, os métodos cresceram e tornaram-se propensos a bugs de priorização (ex: um NPC aceitar um presente odiado por causa de um traço avaliado antes).

### Alternativas Consideradas
1.  **Mecanismo de State Pattern**: Criar estados de interação separados em classes. Descartado por gerar excesso de classes curtas e verbosidade desnecessária para operações que são puramente lógicas de decisão.
2.  **Cadeia Funcional de Regras (Rules Engine)**: Mapear as regras como uma lista ordenada de registros (`GiftRule`, `ProfessionRule`, etc.) contendo um Predicate (condição) e uma Function (retorno de resultado).

### Decisão
Adotar a **Cadeia Funcional de Regras** (Chain of Responsibility funcional).

### Consequências
*   🟢 **Positivas**: A ordem de priorização das regras de interação social está explicitada e visível em uma lista estática. Adicionar novas regras sociais de comportamento exige apenas adicionar um item na lista de regras, reduzindo o risco de quebrar o fluxo principal de interações.
*   🔴 **Negativas**: Leve aumento no consumo de referências de memória para a criação de contextos temporários a cada clique de interação (desprezível para a escala do servidor).

---

## ADR 02: Otimização do Scan de Bounding Box na Construção

### Contexto
O `ConstructionHelper` calcula o deslocamento necessário para ancorar a visualização rotacionada de uma construção em relação à sua coordenada 3D de âncora. Anteriormente, o loop varria os 8 cantos da caixa delimitadora tridimensional para encontrar o menor deslocamento tridimensional após a rotação.

### Alternativas Consideradas
1.  **Scan Tridimensional Completo (8 pontos)**: Verificar as coordenadas X, Y e Z de todos os 8 cantos do cubo da caixa delimitadora.
2.  **Scan Bidimensional de Base (4 pontos XZ)**: Analisar apenas os cantos no plano horizontal.

### Decisão
Adotar o **Scan Bidimensional de Base (4 pontos XZ)**.

### Consequências
*   🟢 **Positivas**: Redução matemática e numérica de 8 para 4 iterações no loop de cálculo de rotação. Como o Hytale rotaciona estruturas apenas no eixo Y (plano horizontal), o Y permanece estático, tornando a verificação de cantos verticais matematicamente redundante. O código ficou mais rápido e compacto.
*   🔴 **Negativas**: Nenhuma. O resultado geométrico final é identicamente correto.
