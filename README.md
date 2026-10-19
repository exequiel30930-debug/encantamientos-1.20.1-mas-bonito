# Selective Enchantment Transfer (beta, codigo fuente)

Transfiere **solo los encantamientos que elijas** de un objeto a un libro, usando el yunque vanilla.
El yunque normal sigue funcionando igual (reparar, renombrar, combinar).

## Version (Forge)
| Carpeta | Minecraft | Java | Extra |
|---|---|---|---|
| `forge-1.20.1` | 1.20.1 | 17 | |

## Idiomas
Ingles (`en_us`) y espanol de Espana (`es_es`). Con cualquier otro idioma del juego se muestra en ingles.

## Como se usa
1. En el yunque: un objeto con **2 o mas encantamientos** (herramienta, armadura o libro encantado) a la izquierda y un **libro normal** a la derecha.
   Tambien vale un objeto con un solo encantamiento de **nivel 2 o mas** si pones **2 o mas libros** a la derecha (para repartir sus niveles).
2. Aparece un libro de vista previa como resultado (cuesta 1 nivel sacarlo; se devuelve).
3. Al sacarlo se abre la pantalla de transferencia. Arriba eliges el modo (**Mover** o **Copiar**) y marcas encantamientos: cada uno queda marcado con el modo que estaba activo en ese momento
   (verde = mover, dorado = copiar). Cambiar de modo no cambia los ya marcados, asi que puedes mover unos y copiar otros en la misma transferencia. Pulsa **Transferir**.
   - **Mover**: salen del objeto y pasan al libro nuevo.
   - **Copiar**: el objeto los conserva. Cuesta 3 veces mas y desgasta la durabilidad del objeto.
4. **Repartir niveles en varios libros**: en un encantamiento marcado de nivel 2 o mas aparece un selector (por ejemplo `2+1`) que reparte sus niveles entre libros:
   Filo III puede ir en 1 libro (3), en 2 libros (2+1) o en 3 libros (1+1+1). Se usa un libro normal por cada libro de salida; los libros que sobren se devuelven.
   Si el origen es un libro encantado y mueves todo, el libro de origen se consume; hace falta repartirlo en 2 o mas libros (si no, saldria el mismo libro).
5. Si cancelas, recuperas todo y no pagas nada.

## Precios (niveles de XP)
Por encantamiento (mover): `peso x nivel`, con peso comun 1.0 · poco comun 1.5 · raro 2.0 · muy raro 3.0, **+4** si es de tesoro. Minimo 1, tope total 39.
Ejemplos: Afilado V 5 · Eficiencia V 5 · Irrompibilidad III 5 · Fortuna III 6 · Toque de seda 3 · Reparacion 6.
Copiar = precio de mover x 3.
Configurable en `serverconfig/selective_enchantment_transfer-server.toml`: `xpCostEnabled`, `costMultiplier`, `maxTotalCost`, `allowCopy`, `copyCostMultiplier`, `allowCopyTreasure`, `sounds`, `debugLog`.

## Menu de ajustes (dentro del juego)
En la pantalla de transferencia hay un boton **Ajustes** (arriba a la derecha). Abre un menu con el mismo estilo arcano que la pantalla de transferencia:
clic = siguiente valor, Mayus + clic = esa opcion vuelve a su valor por defecto, y al pasar el raton sale una explicacion de cada opcion.
Opciones: coste de XP, multiplicador de precio, coste maximo, minimo de encantamientos, sonido, modo copiar,
precio de copiar, copiar encantamientos de tesoro, desgaste al copiar, desgaste base y cantidad de desgaste.
Los ajustes son del mundo/servidor (`serverconfig/selective_enchantment_transfer-server.toml`). En servidores solo los operadores pueden guardar;
en un solo jugador, el dueño del mundo siempre puede.

## Precios, bloqueados y desgaste por encantamiento (archivo de configuracion)
Funcionan con **cualquier encantamiento registrado**, tambien los de otros mods (por ejemplo Apotheosis): se identifican por su id (`mod:encantamiento`).
En `serverconfig/selective_enchantment_transfer-server.toml`:
- `priceOverrides = ["minecraft:mending=12", "minecraft:sharpness=0.5"]` precio propio **por nivel** (sustituye al automatico; 0 = gratis).
- `blockedEnchantments = ["minecraft:mending"]` nunca se pueden mover ni copiar (la fila sale bloqueada).
- `copyBaseWear = 30` desgaste al COPIAR **cualquier** encantamiento: % de la durabilidad maxima de la herramienta/armadura de origen que se pierde por cada encantamiento copiado.
  Debe sobrevivir con al menos 1 de durabilidad. Los libros no tienen durabilidad, asi que no se desgastan.
- `copyWear` desgaste propio de los encantamientos FUERTES (se usa el mayor entre este valor y `copyBaseWear`); los libros encantados no pueden copiar estos.
  Por defecto, Reparacion y los que estan a su nivel gastan un 35 % (el resto, 30 %): Infinidad, Velocidad de alma, Sigilo veloz, Paso helado y varios de Apotheosis
  (Life Mending, Knowledge of the Ages, Endless Quiver, Berserker's Fury, Scavenger, Crescendo of Bolts, Chainsaw, Earth's Boon).
- `autoTreasureWear = 35` desgaste automatico para los encantamientos de tesoro que NO esten en la lista (cubre los de otros mods).
- `copyWearEnabled` y `copyWearMultiplier` activan/escalan el desgaste (tambien en el menu de ajustes).
Mover un encantamiento nunca desgasta el objeto; el desgaste solo existe al copiar.

## Atajo de teclado
En Opciones > Controles, categoria "Transferencia selectiva": **Abrir ajustes del mod** (por defecto la tecla K, se puede cambiar).
Abre el mismo menu de ajustes sin pasar por el yunque (solo funciona en el juego, sin otra pantalla abierta).

## Logros
Cuatro logros que cuelgan de "Encantador" (pestaña Historia): *Encantador selectivo* (primera transferencia), *Copia exacta* (primera copia),
*Divide y venceras* (separar un libro encantado) y *Trabajo pesado* (pagar 30 niveles o mas en una sola transferencia).


## Si el yunque muestra la X roja
La X significa "no hay resultado". Con un libro normal en la derecha, el mod solo actua si el objeto tiene al menos 2 encantamientos
(ajustable: Ajustes > Min. encantamientos, o `minEnchantments` en el archivo de config). Si tiene menos, sale un aviso en la barra de acciones.
Para diagnosticar conflictos con otros mods pon `debugLog = true` en el archivo de config y mira `logs/latest.log` (lineas con `[SET]`).

## Compilar
**GitHub Actions:** sube TODO esto a un repositorio (incluida `.github`), pestaña *Actions* > *Build mod* > *Run workflow*.
Cada version genera su `.jar` en *Artifacts*. 

## Notas
Beta. Si el servidor se cae de golpe con la pantalla abierta, lo que estaba dentro se pierde (como en una mesa de crafteo vanilla).
