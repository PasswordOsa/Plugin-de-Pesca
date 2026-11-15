# 🎣 PescaPlus 2.0 — Simulador de pesca

Plugin de pesca para Paper/Spigot pensado como un **simulador**: cada lanzamiento depende del
lugar, la hora y el clima; hay un minijuego de tensión, cañas y carnadas, mercado con oferta y
demanda, misiones, torneos y peces que se pudren si no los conservas.

## Instalación
1. Descarga el `.jar` desde **Releases** (o compílalo, ver abajo).
2. Cópialo a `plugins/` y reinicia el servidor. Vault es opcional (sin Vault usa un saldo interno).

### Compilar
Requiere JDK 21+ y Maven: `mvn clean package` → `target/PescaPlus-2.0.0.jar`.
En GitHub se compila solo con Actions y se publica en Releases.

## Cómo se juega
1. **Lanza la caña.** Dónde pescas importa: la **zona** (océano, río, pantano, frío, jungla…),
   el **día/noche**, la **lluvia** y hasta la **luna llena** cambian qué peces pueden salir.
2. **Pica un pez.** Aparece una barra en la action bar y un aviso de lo que sientes
   (*ligero… ¡ENORME!*). Recoge la caña cuando el cursor esté en la zona verde:
   - **Zona amarilla = captura PERFECTA** (pez más pesado, +50% XP, cuenta para misiones).
   - **Zona verde = captura buena.**
   - **Fuera = se escapa.** Cuanto más raro el pez, más rápido el cursor y más pequeña la zona.
3. **Gestiona tus peces.** Se pudren con el tiempo: guárdalos en una **heladera** o véndelos ya.
4. **Vende en el mercado.** Vender muchos peces de una especie baja su precio; se recupera con
   el tiempo. Cada cierto rato una especie entra en **tendencia** (+30%).
5. **Mejora tu equipo.** Cañas (velocidad, suerte, control) y carnadas en la tienda.
6. **Sube de nivel**, completa **misiones diarias**, entra a **torneos** y bate los
   **récords del servidor**.

## Menús
Todo se maneja desde `/pesca`: perfil con condiciones de pesca en tu ubicación, colección
paginada con filtro por rareza, mercado en vivo, tienda, misiones y estado del torneo.

## Comandos
| Comando | Descripción |
|---|---|
| `/pesca` | Menú principal |
| `/pesca coleccion` · `mercado` · `tienda` · `misiones` | Acceso directo a cada menú |
| `/pesca info` | Estadísticas en el chat |
| `/pesca vender [mano]` | Vende tus peces frescos |
| `/pesca limpiar` | Tira los peces podridos |
| `/pesca torneo` | Estado del torneo actual |
| `/pesca torneo iniciar [min]` · `parar` | Torneos (`pesca.admin`) |
| `/pesca heladera [jugador]` | Da una heladera (`pesca.admin`) |
| `/pesca dar <jugador> <cana\|carnada> <id> [cant]` | Da cañas o carnadas (`pesca.admin`) |
| `/pesca reload` | Recarga la configuración (`pesca.admin`) |

Permisos: `pesca.use` (todos) y `pesca.admin` (operadores).

## Sistemas
- **Cañas:** velocidad (pica antes), suerte (más rarezas), control (zona verde más grande).
  Sus bonus solo funcionan si tu nivel alcanza el requerido.
- **Carnadas:** van en la mano secundaria; se gasta una por lanzamiento.
- **Heladera:** barril especial (7 hierro + barril + hielo compacto). Cerrada, congela el tiempo
  de los peces. Las tolvas no pueden usarla y las explosiones no la rompen.
- **Mercado dinámico:** saturación por venta, recuperación por minuto y especie en tendencia.
- **Misiones diarias:** 3 por día, iguales para todos, con dinero y XP.
- **Torneos:** barra de jefe con el líder; gana quien pesque más kilos. Pueden ser automáticos.
- **Anuncios y récords:** las capturas épicas/legendarias se anuncian; los récords de peso
  por especie se guardan en `records.yml`.

## Rendimiento
- Tablas de probabilidad cacheadas por contexto: elegir un pez es una búsqueda binaria.
- Sin tareas por pez ni por tick: la frescura se calcula de forma perezosa.
- Solo hay una tarea corta (cada 2 ticks) mientras un pez está picando.
- Datos en caché; guardado asíncrono en un único hilo con escritura atómica.
- Los menús resuelven clics por slot y se redibujan sin cerrarse.

## Configuración
Todo está en `config.yml`: peces (zonas, horario, clima, rareza, precio…), cañas, carnadas,
dificultad del minijuego, mercado, misiones, torneos y todos los mensajes.

## Limitaciones
- No es compatible con Folia.
- Los premios del torneo solo se entregan a quienes estén conectados al terminar.
- Los cambios de `heladera.receta` requieren reiniciar el servidor.
