# PescaPlus

Plugin de pesca para Paper/Spigot: peces coleccionables con peso y rareza, niveles, venta,
y peces que se dañan si no se guardan en una heladera.

## Instalación
1. Compila el plugin (ver abajo) o descarga el `.jar` desde GitHub Actions.
2. Copia `PescaPlus-1.0.0.jar` a la carpeta `plugins/` del servidor.
3. Reinicia el servidor. Vault es opcional (si no está, se usa un saldo interno).

### Compilar
Requiere JDK 21+ y Maven:

    mvn clean package

El `.jar` queda en `target/PescaPlus-1.0.0.jar`. En GitHub, la compilación se hace sola
en la pestaña **Actions** (el `.jar` se descarga dentro de un zip, en *Artifacts*).

## Comandos
| Comando | Descripción |
|---|---|
| `/pesca info` | Tu nivel, XP y estadísticas |
| `/pesca coleccion` | Menú con las especies descubiertas |
| `/pesca vender` | Vende todos los peces frescos del inventario |
| `/pesca vender mano` | Vende solo el pez que llevas en la mano |
| `/pesca limpiar` | Tira los peces podridos |
| `/pesca reload` | Recarga la configuración (`pesca.admin`) |
| `/pesca heladera [jugador]` | Entrega una heladera (`pesca.admin`) |

Permisos: `pesca.use` (todos por defecto) y `pesca.admin` (solo operadores).

## Cómo funciona

### 1. Pescar
Cuando pescas un pez normal de Minecraft (bacalao, salmón, pez tropical o pez globo), el
plugin lo cambia por uno suyo:
- Elige la especie según tu **nivel**: solo salen peces cuyo nivel mínimo ya alcanzaste.
- Le da un **peso al azar** dentro del rango de esa especie.
- Te da **XP** de pesca y lo anota en tu colección.

Los tesoros y la basura de la pesca normal no se tocan.

### 2. Niveles
- Cada pez da XP, y subir de nivel cuesta cada vez más.
- Al subir se desbloquean peces nuevos, que dan más dinero.
- Cuanto más nivel tienes, más probable es que salgan los peces raros.
- Cada nivel aumenta un 1% el precio de venta.

Todo se ajusta en `config.yml`.

### 3. Valor y venta
El precio depende de la especie y del peso: un pez más pesado que el promedio de su especie
vale más. El dinero va a Vault si está instalado; si no, a un saldo interno del plugin.

### 4. Peces que se dañan
Cada pez nace con una **hora de caducidad** (por ejemplo, 30 minutos una sardina y 4 horas
un pez dorado). Tiene tres estados:
- **Fresco**.
- **Se está dañando**: queda poco tiempo; se avisa en la descripción del item.
- **Podrido**: se convierte en carne podrida y **ya no se puede vender**. Solo se puede tirar
  con `/pesca limpiar`.

El plugin no lleva un reloj por cada pez: guarda la hora de caducidad dentro del item y cada
30 segundos actualiza el estado de lo que llevas en el inventario.

### 5. La heladera
Es un barril especial: se craftea con 7 lingotes de hierro, un barril y hielo compacto, o se
entrega con `/pesca heladera`.
- Al **cerrarla**, los peces de adentro se congelan y dejan de envejecer.
- Al **abrirla**, se descongelan y conservan el tiempo que les quedaba.
- Los peces ya podridos no se pueden salvar.
- Las tolvas no pueden meter ni sacar items de ella, y las explosiones no la rompen.
- Si se rompe, el barril conserva su marca de heladera.

### 6. Colección
`/pesca coleccion` muestra todas las especies. Las no pescadas aparecen como "???"; las
descubiertas muestran cuántas llevas y tu récord de peso.

### 7. Guardado de datos
El progreso se guarda en un archivo por jugador en `plugins/PescaPlus/data/`. Se carga al
entrar, se guarda cada 5 minutos y al salir, siempre en segundo plano para no afectar el
rendimiento del servidor.

## Limitaciones
- No es compatible con Folia.
- Los cambios de `heladera.receta` en la config requieren reiniciar el servidor.
