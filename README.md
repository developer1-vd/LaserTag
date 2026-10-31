# LaserTag

A decent laser tag game :)

A fast, top-down arena shooter with an 80s arcade look, written in plain Java (Swing, no
external libraries). Tag your opponents with a laser that ricochets off walls, play
single-player against bots or with a friend on the same keyboard, or play online over the
Internet or your home network.

Everything is drawn at a low 480x360 resolution and scaled up with chunky pixels and CRT
scanlines, using a hand-drawn bitmap font and chiptune-style sound effects.

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

Hold `Tab` for the scoreboard and connection info. `Esc` returns to the menu, `P` pauses
(offline only), `M` toggles sound and `C` toggles the CRT scanlines.

The menu works with the keyboard (arrows to choose, left/right to change a setting, Enter
to start, just type to edit a text field) or the mouse.

## Game modes

### Single player and offline

- **Solo vs Bots**: you against 1–7 bots. Set the number of bots and their skill
  (Easy, Normal or Hard) under *Settings*.
- **2 Players 1 Keyboard**: two people share a keyboard, with optional bots.

### Online (Internet or home network)

- **Host Online Game**: starts a match that others can join. Bots fill the empty slots and
  step aside when people join (up to 8 players).
  - The game asks your router to open the port automatically (UPnP), then shows an
    **Internet code** such as `M17PN-0W4YE`. Send it to your friends. Hold `Tab` in game to
    see it again.
  - If the info screen says the port could not be opened, your router has UPnP turned off.
    Forward TCP port `47777` (or the port you chose) to your computer in the router's settings.
  - If it says you are behind another network (CGNAT), some internet providers block
    incoming connections. Use a dedicated server on a machine with a public address instead.
  - Players on the same Wi-Fi don't need a code. Your game shows up in their
    *On your network* list.
- **Join Online Game**: type the host's Internet code, IP address or hostname in the *Join*
  field (for example `M17PN-0W4YE`, `203.0.113.5`, `192.168.1.20:47777` or
  `play.example.com`), then choose *Join Online Game*. You can also pick a game from the
  *On your network* list.

To find its public address, the host's game asks your router. If the router can't say,
it asks `api.ipify.org`.

#### Dedicated server

You can run a match with no window, for example on a server:

```bash
./run.sh --server 47777 0 2 normal
```

The arguments are, in order (all optional): port, arena (`0` Crossfire, `1` Bunkers,
`2` Corridors), number of bots, and bot skill (`easy`, `normal`, `hard`). The server tries
to open its port on the router and prints its Internet code. Run it on a VPS or cloud
machine for a server that anyone can always join.

## How it works

- `GameWorld` holds the whole match and runs the simulation. Offline, it runs on your
  computer. Online, the host's copy is the only one that runs it.
- Clients send only their inputs (`I` lines) over TCP, and the host sends back a full
  snapshot of the world (`S` lines) 30 times a second. Clients smooth player movement
  between snapshots.
- Hosts announce themselves with a UDP broadcast on port `47778` so that games on your
  network appear in the menu.
- An Internet code packs the host's public IPv4 address and port into 10 characters
  (Crockford base 32) with a small checksum that catches about two thirds of typos.
- Arenas are tile maps written as one quarter and mirrored both ways, so every map is
  symmetric. Add more maps in `Arena.java`.
