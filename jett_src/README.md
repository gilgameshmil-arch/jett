# Jett Mobility — Fabric 1.21.11 (v1.2.0)

Habilidades de mobilidade da Jett, desbloqueadas **permanentemente** por crafting. Funcionam por tecla (não é preciso segurar item), têm HUD, efeitos de vento próprios e funcionam em multiplayer (servidor autoritativo). Nenhuma habilidade causa dano.

| Tecla | Habilidade | O que faz | Cooldown |
|---|---|---|---|
| Q | Updraft | Impulso vertical + corrente de ar subindo em espiral + anel de choque no chão | 8 s |
| E | Cloudburst | Esfera de fumaça 3D (raio 3,2) assentada no chão onde você olha (até 18 blocos), girando e sumindo aos poucos em 9 s | 15 s |
| C | Tailwind | Dash de ~12 blocos em 5 ticks na direção das teclas de movimento (sem teclas: para a frente) + dupla hélice de vento | 6 s |
| X | Tempestade de Facas | Anel de 8 facas 3D girando em volta de você por 7 s + funil de vento | 60 s |

## O que há de 3D e de customizado
- **Faca de Vento** (`jettmobility:wind_knife`): modelo 3D de elementos (lâmina em folha com gume e espinha, guarda alada, cabo com faixas, pomo em anel, veios de vento brilhantes). Tem ajustes próprios para mão (1ª e 3ª pessoa), inventário, chão e moldura. Arquivo: `assets/jettmobility/models/item/wind_knife.json` (abre no Blockbench). Receita: 1 barra de ferro + 1 pena + 1 fragmento de ametista.
- **Esfera da Cloudburst**: corpo 3D translúcido (8 discos de linhas, 176 elementos) em uma entidade `item_display`, visível a todos; cresce, gira e encolhe. Em volta, uma casca de fumaça girando, miolo fofo e poeira no chão.
- **Anel de facas** (ultimate): 8 facas 3D num único modelo, girando e acompanhando o jogador.
- **Partículas próprias** `smoke_puff` e `wind_wisp`, com texturas próprias (`textures/particle`).
- O servidor manda um pacote "toque o efeito X" e cada cliente próximo gera as espirais; os modelos 3D são entidades reais. Por isso os outros jogadores veem tudo.

## Desbloqueio
Itens `Desbloqueio: …` craftáveis com itens vanilla (receita sem forma). Use com o botão direito **uma vez**: é consumido e a habilidade fica permanente (salva no jogador, mantida ao morrer).

- **Updraft**: 2 penas + bola de slime + pistão
- **Cloudburst**: 2 lãs brancas + bola de neve + garrafa de vidro
- **Tailwind**: botas de couro + 2 penas + açúcar
- **Tempestade**: 2 diamantes + olho do Ender + 2 membranas de phantom + pena

## Teclas e conflitos
Q/E/C/X conflitam com ações vanilla (Q soltar, E inventário). Com a habilidade **desbloqueada**, o mod consome o clique da ação vanilla que usa a mesma tecla. Rebinde em *Opções > Controles > Jett Mobility*. Valores de jogabilidade ficam em `JettTuning.java` (velocidade do dash, raio da esfera, durações).

## Compilar
JDK 21 e internet. `gradle wrapper --gradle-version 9.2.1` e depois `./gradlew build`, ou use o workflow do GitHub Actions. O JAR fica em `build/libs/jett-mobility-1.2.0.jar` (não o `-sources`).

Dependências: Minecraft 1.21.11, Fabric Loader 0.18.1, Loom 1.14, Fabric API 0.141.6+1.21.11, mapeamentos Mojang.

## Estado de verificação
- Verificado: v1.1.0 compilou no GitHub Actions. Na v1.2.0, o JSON dos recursos é válido, a sintaxe Java confere, e a geometria dos modelos foi conferida em uma renderização própria (não é captura do jogo).
- **Não verificado**: a v1.2.0 ainda não foi compilada nem executada no Minecraft. Aparência real dos efeitos, transparência da esfera, posição da faca na mão e comportamento em multiplayer precisam ser testados no jogo.
