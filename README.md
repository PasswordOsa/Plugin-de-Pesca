# PescaPlus

Plugin de pesca para Paper/Spigot: peces coleccionables con peso y rareza, niveles, venta,
y peces que se dañan si no se guardan en una heladera.

## Compilar
Requiere JDK 21+ y Maven:

    mvn clean package

El .jar queda en `target/PescaPlus-1.0.0.jar`. Copialo a `plugins/`. Vault es opcional
(si no está, usa un saldo interno).

## Comandos
- `/pesca info` · `/pesca coleccion` · `/pesca vender [mano]` · `/pesca limpiar`
- Admin (`pesca.admin`): `/pesca reload` · `/pesca heladera [jugador]`

## Heladera
Barril especial (receta: 7 lingotes de hierro + barril + hielo compacto, o `/pesca heladera`).
Los peces dentro NO envejecen mientras la heladera esté cerrada. Sin tolvas.
