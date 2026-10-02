# elytra

A client-side Fabric mod for Minecraft: Java Edition that gives you more control over elytra flight. Each feature can be turned on and off on its own, from the configuration screen or with its own key, and a main switch turns all of them on or off at once. Only your own player is affected.

## Multiplayer warning

**This mod is disabled in multiplayer by default. To use it on a server, change the multiplayer mode on its configuration screen.**

This mod changes player movement. Servers check how players move: the `player_movement_check` and `elytra_movement_check` game rules control vanilla's checks, and many servers also run anti-cheat plugins. Using this mod on a server can get your movement set back, get you kicked, or get you banned, and it may break the server's rules. **The mod does not try to get around any anti-cheat or movement check.** Check each server's rules before you enable it there, and use it in multiplayer at your own risk.

Some features are worth knowing about in particular:

- **Fake Elytra** and **Ground Glide** glide on your client only; the server does not know you are gliding. To the server you are falling, walking, or flying, so vanilla's movement checks treat you like a player without an elytra, and a dedicated server that does not allow flight kicks a player who hovers or climbs for more than about four seconds without gliding.
- **Fully Controlled Flying**, **Partially Controlled Flying**, **Elytra Boost**, and **Autopilot** move you in ways the server does not simulate. With `elytra_movement_check` on (the default), a vanilla server limits how fast a gliding player may move between two updates; anti-cheat plugins are usually much stricter.
- In singleplayer, including worlds you open to LAN, the game does not check your own movement.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Download the jar from this repository's [Releases](https://github.com/urntt/elytra/releases) page. Each release supports a single Minecraft version, shown after the `+` in its version number. For example, `1.0.0+26.3` is for Minecraft 26.3.
3. Put the jar into your `.minecraft/mods` folder.

[Mod Menu](https://modrinth.com/mod/modmenu) is optional. When installed, it opens the mod's configuration screen from its mod list.

## Features

| Feature | Default | What it does |
| --- | --- | --- |
| Fake Elytra | Off | Glide without wearing an elytra: press jump in the air as you would with one. |
| No Gliding | Off | Never start gliding, even with an elytra on. |
| Ground Glide | Off | Keep gliding when you touch the ground instead of landing. The glide ends once it comes to rest. |
| Instant Fly | Off | Pressing jump on the ground jumps and starts gliding right away, without a second press in the air. |
| Stop Flying in Water | Off | Stop gliding when you enter water. |
| Fully Controlled Flying | Off | Fly only as the keys say: movement keys move you horizontally, jump and sneak move you up and down, and with no key held you hover in place. The horizontal and vertical speeds are adjustable. |
| Partially Controlled Flying | Off | Keep vanilla gliding, but forward speeds you up along your facing, back slows you down, jump pushes you up, and sneak pushes you down. The amounts, the natural descent, the natural acceleration, and a maximum speed are adjustable. |
| No Crash | Off | Looks ahead along your velocity and slows you down before you fly into a wall or into a chunk that has not loaded yet, stopping just in front of it. Floors do not count, so you can still land. |
| Autopilot | Off | Flies on forever by diving and climbing in turns, which gains height over time. It only changes your pitch; you still steer left and right. Starting slowly, it first dives up to about 55 blocks to gain speed. |
| Elytra Replace | Off | Swaps in a spare elytra from your inventory when the worn one has the minimum durability (1 by default) or less left. An elytra with 1 durability left can no longer glide, so with the default your glide ends just before the swap; set the minimum to 2 or more to swap without interrupting the glide. |
| Chest Swap | Off | When you try to glide in a chestplate with an elytra in your inventory, puts the elytra on and starts the glide. Puts the chestplate back on when that glide ends. Both steps can be turned off. Also enables the swap key. |
| Elytra Boost | Off | While gliding, the boost key pushes you like a firework rocket, without using one. The boost lasts 20 ticks (1 second) by default. |
| Insta Stop | Off | The stop key ends your glide at once. |

Fake Elytra and No Gliding contradict each other, and so do Fully Controlled Flying, Partially Controlled Flying, and Autopilot: turning one on turns the others in its group off.

All features are off by default.

### Main switch

The main switch, named **elytra** on the configuration screen, decides whether the features you turned on are active. Turning it off pauses all of them without changing which ones are turned on. Like [nojumpdelay](https://github.com/urntt/nojumpdelay)'s single switch, it has a default for singleplayer worlds and one for servers, and it can return to the default whenever you join a world or the first time after restarting the game. It is on by default, so turning a feature on is enough to use it.

### How the features work with the server

- Glides start and stop with the same command the game sends when you press jump in the air. Insta Stop and Stop Flying in Water send it again while you glide, which the server answers by ending the glide.
- Fake Elytra's glide exists only on your client. The server counts its descent as a fall, so landing from a fake glide can hurt.
- When Ground Glide lifts you back into the air with an elytra on, the mod asks the server to glide again, so the server no longer counts the flight as falling.
- Elytra Boost's rocket exists only on your client and is removed after the boost.
- Chest Swap and Elytra Replace move items with the same clicks you would make in your inventory, and only while no other container is open and nothing is on your cursor.

## Usage

The mod adds these key bindings in **Options → Controls → Key Binds**, all unbound by default:

- **Toggle elytra** turns the main switch on or off and shows the new state on the action bar.
- **Toggle &lt;feature&gt;** for each feature turns it on or off and shows the new state on the action bar. If the main switch is off, the message says so.
- On a server that the multiplayer settings rule out, all toggle keys only show that the mod is disabled there.
- **Elytra Boost**, **Insta Stop**, and **Swap Elytra and Chestplate** perform those actions while their features are on.
- **Open elytra Settings** opens the configuration screen. With Mod Menu installed, you can also open it from the mod list.

### Settings

All settings are saved to `config/elytra.json` as soon as you change them. Speeds are in blocks per tick; the game runs 20 ticks per second.

| Setting | Default | Meaning |
| --- | --- | --- |
| elytra | On | The main switch, the same one the Toggle elytra key switches. |
| Singleplayer Default | On | The state a reset restores to the main switch in singleplayer worlds, including worlds you open to LAN. |
| Server Default | On | The state a reset restores to the main switch on servers that the multiplayer mode allows. |
| Reset on World Exit | Off | Every world starts with the main switch in its default state instead of keeping the last state. |
| Reset on Game Exit | Off | After restarting the game, the first world where the mod is allowed starts with the main switch in its default state. |
| Each feature | Off | Whether the feature is turned on, the same setting its toggle key switches. |
| Fully Controlled Flying: Horizontal Speed | 1.00 | Speed while a movement key is held. |
| Fully Controlled Flying: Vertical Speed | 0.50 | Speed while jump or sneak is held. |
| Partially Controlled Flying: Acceleration | 0.05 | Speed added each tick along your facing while forward is held, and taken away while back is held. |
| Partially Controlled Flying: Ascend Rate | 0.08 | Upward speed added each tick while jump is held. |
| Partially Controlled Flying: Descend Rate | 0.04 | Downward speed added each tick while sneak is held. |
| Partially Controlled Flying: Natural Descent | 100% | How strongly gravity pulls the glide down. |
| Partially Controlled Flying: Natural Acceleration | 100% | How quickly the glide turns height into forward speed. |
| Partially Controlled Flying: Max Speed | 3.00 | The glide never goes faster than this. |
| Elytra Boost: Duration | 20 ticks | How long each boost pushes. |
| Elytra Replace: Min Durability | 1 | Swap when the worn elytra has this much durability left or less. |
| Chest Swap: Swap on Jump | On | Put on an elytra when you try to glide without one. |
| Chest Swap: Swap Back | On | Put the chestplate back on when a glide that Chest Swap started ends. |
| Multiplayer mode | Disabled | **Disabled**: never active on servers. **Whitelist**: active only on servers in the server list. **Blacklist**: active on all servers except those in the server list. |
| Server List | Empty | The addresses the whitelist and blacklist modes use. |

The multiplayer mode is a hard limit: on a server it rules out, every feature stays off, whatever the main switch and the feature's own setting say. Joining another player's LAN world or a Realm counts as multiplayer.

Server list entries are compared with the address you connect to, ignoring upper and lower case. An entry without a port, such as `mc.example.com`, matches the server on any port, while an entry with a port, such as `mc.example.com:25566`, matches only that port. The server list screen marks invalid addresses in red and does not save until they are fixed or removed.

## Development

Building requires the JDK version set by `java_version` in `gradle.properties`.

Build the mod:

```bash
./gradlew build
```

The jar is written to `build/libs/`.

Run the client game tests, which start Minecraft and measure every feature against vanilla in singleplayer worlds and on a local dedicated server, along with the key bindings, the main switch and its reset rules, the multiplayer modes, and the saved configuration:

```bash
./gradlew runClientGameTest
```

The game tests need a display. On a headless Linux machine, run them under Xvfb. Xvfb offers no sRGB-capable OpenGL visuals, so install Mesa's Vulkan driver (`mesa-vulkan-drivers` on Ubuntu) for the game to fall back to:

```bash
xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew runClientGameTest
```

Screenshots taken by the tests are saved to `build/run/clientGameTest/screenshots/`.

The multiplayer tests start a local dedicated server, so the test setup in `build.gradle` accepts the [Minecraft EULA](https://aka.ms/MinecraftEULA) for that test server.

## License

[MIT](LICENSE)
