# Jett Mobility — Fabric 1.21.11

Habilidades de mobilidade da Jett, desbloqueadas **permanentemente** por crafting. Funcionam por tecla (não é preciso segurar item), têm HUD, partículas e sons de vento, e funcionam em multiplayer (servidor autoritativo).

| Tecla | Habilidade | O que faz | Cooldown |
|---|---|---|---|
| Q | Updraft | Impulso vertical + rastro de vento | 8 s |
| E | Cloudburst | Nuvem de vento onde você olha (até 12 blocos), dura 8 s (visual) | 15 s |
| C | Tailwind | Dash horizontal na direção do olhar | 6 s |
| X | Tempestade (ultimate visual) | Anel de "lâminas" de vento girando por 7 s. **Sem dano.** | 60 s |

## Desbloqueio
Cada habilidade tem um item (`Desbloqueio: …`) craftável com itens vanilla (receita sem forma, mesa de crafting):

- **Updraft**: 2 penas + 1 bola de slime + 1 pistão
- **Cloudburst**: 2 lãs brancas + 1 bola de neve + 1 garrafa de vidro
- **Tailwind**: botas de couro + 2 penas + 1 açúcar
- **Tempestade**: 2 diamantes + 1 olho do Ender + 2 membranas de phantom + 1 pena

Use o item com o botão direito **uma vez**: ele é consumido e a habilidade fica desbloqueada para sempre (salva no jogador e mantida ao morrer). Depois disso, é só apertar a tecla. Os itens também aparecem na aba criativa "Ferramentas e utilitários".

## Teclas e conflitos
Os padrões Q/E/C/X conflitam com ações vanilla (Q = soltar item, E = inventário). Enquanto a habilidade estiver **desbloqueada**, o mod consome o clique da ação vanilla que usa a mesma tecla; habilidades ainda bloqueadas não interferem. Para voltar a soltar itens com Q, rebinde as teclas em *Opções > Controles > Jett Mobility*.

## Compilar
Requisitos: JDK 21 e internet (Gradle baixa Minecraft, Fabric Loom e Fabric API).

```
gradle wrapper --gradle-version 9.2.1   # só na primeira vez
./gradlew build                         # Windows: gradlew.bat build
```
O JAR fica em `build/libs/jett-mobility-1.1.0.jar` (use o que **não** termina em `-sources`).
Alternativa sem instalar nada: suba o projeto no GitHub; o workflow `.github/workflows/build.yml` compila e publica o JAR como artefato.

Dependências (conferidas em 2026-11-14): Minecraft 1.21.11, Fabric Loader 0.18.1, Fabric Loom 1.14, Fabric API 0.141.6+1.21.11, mapeamentos Mojang (1.21.11 é a última versão ofuscada; Yarn não é atualizado depois dela).

## Estado de verificação
Este código **não foi compilado nem executado no Minecraft** pela sessão que o escreveu (sem acesso de rede aos repositórios Fabric/Mojang). Foram conferidos apenas: JSON dos recursos válido e sintaxe Java sem erros. Se o primeiro `gradle build` acusar erro de símbolo, é provável que seja um nome de API do 1.21.11 a ajustar.
