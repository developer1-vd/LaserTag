# LaserTag

A decent laser tag game :)

A fast, top-down arena shooter with an 80s arcade look, written in plain Java (Swing, no
external libraries). Tag your opponents with a laser that ricochets off walls, play
single-player against bots or with a friend on the same keyboard, or play online over the
Internet or your home network.

Everything is drawn at a low 480x360 resolution and scaled up with chunky pixels and CRT
scanlines, using a hand-drawn bitmap font and chiptune-style sound effects.

## Play in your browser

**[Play Laser Tag on the web](https://developer1-vd.github.io/LaserTag/)**. Nothing to
install. It has solo vs bots and two players on one keyboard, and works with touch on
phones and tablets (left thumb moves, right thumb aims and fires). Online play needs the
desktop app below, because browsers can't open the network connections it uses.

## Download and play

Ready-made apps with Java built in are attached to each release on the
[Releases page](https://github.com/developer1-vd/LaserTag/releases). Nothing else needs
installing.

| System | Download | How to install |
| --- | --- | --- |
| **Windows** 10/11 | `LaserTag-…-windows-x64.msi` | Run it and follow the steps. Laser Tag appears in the Start menu and on the desktop. Or unzip `…-windows-x64.zip` and run `LaserTag.exe`. |
| **macOS** (Apple Silicon: M1 or newer) | `LaserTag-…-macos-arm64.dmg` | Open it and drag Laser Tag into Applications. |
| **macOS** (Intel) | `LaserTag-…-macos-x64.dmg` | Same as above. |
| **Linux** (Ubuntu, Debian, Mint, Pop!_OS…) | `lasertag_…_amd64.deb` | `sudo apt install ./lasertag_…_amd64.deb`, then find Laser Tag in your app menu. |
| **Linux** (any) | `LaserTag-…-linux-x64.tar.gz` | Extract it and run `LaserTag/bin/LaserTag`. |
| Anything with Java 17+ | `LaserTag-….jar` | `java -jar LaserTag-….jar` |

### First launch

The apps aren't code-signed, so your system may warn you the first time:

- **Windows:** if *"Windows protected your PC"* appears, click **More info → Run anyway**.
- **macOS:** if it says Laser Tag *"can't be opened"* or *"Apple could not verify"* it,
  open **System Settings → Privacy & Security**, scroll down and click **Open Anyway**.
  Or run `xattr -dr com.apple.quarantine "/Applications/LaserTag.app"` in Terminal.
- **Hosting online:** the first time you host, Windows and macOS ask whether Laser Tag may
  accept network connections. Click **Allow** (on Windows, tick *Private networks*, and
  also *Public networks* if you want to host over the Internet). Otherwise other players
  can't join.

## Run from source

You need a **JDK, version 17 or newer** (check with `javac -version`). Nothing else.

macOS / Linux:

```bash
./run.sh
```

Windows: double-click `run.bat`.

See **[BUILDING.md](BUILDING.md)** for installing Java, building by hand or with Maven,
running the tests, building the Windows/macOS/Linux apps yourself, automatic releases, and
troubleshooting.

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

- The desktop and web versions share the same game code. `GameRenderer` draws through a
  small `Screen` interface, implemented with Swing on the desktop and an HTML canvas in the
  browser. The web version (`web/`) is compiled from Java to JavaScript with
  [TeaVM](https://teavm.org).

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
