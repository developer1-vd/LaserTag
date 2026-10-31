# LaserTag

A decent laser tag game :)

A fast, top-down neon arena shooter written in plain Java (Swing, no external libraries).
Tag your opponents with a laser that ricochets off walls, play offline against bots or a
friend on the same keyboard, or host and join matches over the network.

## Requirements

- Java 17 or newer (JDK, to build)

## Running

```bash
./run.sh
```

On Windows, double-click `run.bat` (or run it from a terminal). Both scripts compile the
game with `javac` and start it. No other tools needed.

If you have Maven you can also build a jar and run the tests:

```bash
mvn package
```

```bash
java -jar target/lasertag.jar
```

## How to play

- Each shot costs energy (4 shots from full), and energy recharges over time.
- Lasers **ricochet off walls twice**, so you can bank shots around corners.
- A tagged player is out for 2.5 s, then respawns with a short shield. Firing drops the shield.
- A round ends when someone reaches **15 tags** or after **3 minutes**. The next round starts automatically.

### Controls

| Mode | Move | Aim | Fire |
| --- | --- | --- | --- |
| Solo / Online | WASD or arrow keys | Mouse | Left click or Space |
| 2 players, player 1 | WASD | Faces where you move | Space (or F) |
| 2 players, player 2 | Arrow keys | Faces where you move | Enter (or `/`, Numpad 0) |

`Esc` returns to the menu, `P` pauses (offline only), and `M` toggles sound.

## Game modes

### Offline

- **Solo vs Bots**: you against 1–7 bots (Easy, Normal or Hard).
- **2 Players, 1 Keyboard**: two people share a keyboard, with optional bots.

Choose the arena (Crossfire, Bunkers or Corridors), the number of bots and bot skill on the
left of the menu.

### Online

- **Host Game**: starts a match on the chosen port (default `47777`). The game shows your
  address in the bottom-left corner. Bots fill empty slots and step aside when people join
  (up to 8 players).
- **Join Game**: enter the host's IP and port, or double-click a game in the
  *Games on your network* list. Hosts on your local network show up there automatically.

To play over the internet, the host must forward TCP port `47777` (or the port they chose)
on their router, and others join using the host's public IP.

#### Dedicated server

You can run a match with no window, for example on a server:

```bash
./run.sh --server 47777 0 2 normal
```

The arguments are, in order (all optional): port, arena (`0` Crossfire, `1` Bunkers,
`2` Corridors), number of bots, and bot skill (`easy`, `normal`, `hard`).

## How it works

- `GameWorld` holds the whole match and runs the simulation. Offline, it runs on your
  computer. Online, the host's copy is the only one that runs it.
- Clients send only their inputs (`I` lines) over TCP, and the host sends back a full
  snapshot of the world (`S` lines) 30 times a second. Clients smooth player movement
  between snapshots.
- Hosts announce themselves with a UDP broadcast on port `47778` so that games on your
  network appear in the menu.
- Arenas are tile maps written as one quarter and mirrored both ways, so every map is
  symmetric. Add more maps in `Arena.java`.
