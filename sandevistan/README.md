# Sandevistan – Hyper Speed (Fabric, Minecraft Java 1.21.1)

## Como compilar
1. Instale o **JDK 21**.
2. Abra a pasta no IntelliJ IDEA (ele baixa o Gradle sozinho) **ou**, com Gradle 8.10+ instalado:
   `gradle wrapper --gradle-version 8.10` e depois `./gradlew build`
3. O mod fica em `build/libs/sandevistan-1.0.0.jar`.
4. Coloque o .jar em `.minecraft/mods` junto com **Fabric Loader 0.16+** e **Fabric API 0.105.0+1.21.1**.
   Em servidor, instale o mod no servidor E nos clientes.

## Uso
- Tecla padrão: **V** (configurável em Controles > Sandevistan). Aperte para ligar, aperte de novo para desligar.
- Config do servidor: `config/sandevistan.json` (duração, recarga, velocidade, fator de lentidão, raio, blacklist...).

## Como a manipulação temporal funciona (real, não ilusão)
Mixin em `World.tickEntity` (usado por ServerWorld e ClientWorld para TODA entidade, inclusive de outros mods).
Enquanto há um Sandevistan ativo, cada entidade afetada só executa o `tick()` 1 vez a cada N ticks
(`worldTickRate`, padrão 5: mundo a 5 ticks/s enquanto você fica nos 20). Isso reduz de verdade IA, movimento, gravidade, cooldown de ataque e projéteis.
Jogadores (inclusive você) nunca são afetados, nem montarias com jogador, nem projéteis disparados por você.
Entidades puladas custam quase zero de CPU, então o servidor não trava.
Servidor e cliente usam a mesma regra, então não há dessincronia.

## Limitações conhecidas
- No servidor o mob se move em "passos" (é isso que reduz a frequência de update); o cliente suaviza
  interpolando a posição, então visualmente ele desliza. Projéteis ainda andam em passos.
- Duração infinita e sem recarga são o padrão (`durationSeconds` 0 = infinito, `cooldownSeconds` 0).
  Os buffs de velocidade/pulo/ataque ficam desligados (1.0); ligue na config se quiser.
- Blocos (fornalhas, plantações), clima e relógio do mundo não são desacelerados; só entidades.
- Sons usam sons vanilla (beacon/conduit) pois não há arquivos .ogg customizados.
