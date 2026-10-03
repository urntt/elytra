# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Each version carries the targeted Minecraft version as build metadata, for example `1.0.0+26.3`.

## [Unreleased]

### Added

- Instant Landing: stand up as soon as a glide touches the ground, instead of lying and sliding along the ground until the server reports the landing, which takes a round trip on a server. A glide started again before the server catches up goes on at once. Turning it on turns off Ground Glide, and the other way round.

### Fixed

- On a server, Instant Fly no longer breaks off the glide for a tick when you jump right on landing from a glide.

## [1.1.0+26.3] - 2026-10-03

### Added

- Keep Pose While Gliding: glide with the view height, hitbox, and animation of a standing player. Only your client changes; the server and other players still see you gliding.

### Changed

- Rename Insta Stop to Instant Stop. Its saved on/off state carries over, but its toggle key and its stop key have new names, so bind them again.

### Fixed

- Instant Fly now also glides from a jump made right on landing from a glide, so holding jump glides from every jump instead of every other one.

## [1.0.0+26.3] - 2026-10-02

### Added

- Fake Elytra: glide without wearing an elytra.
- No Gliding: never start gliding.
- Ground Glide: keep gliding on the ground instead of landing.
- Instant Fly: start gliding with a single press of jump on the ground.
- Stop Flying in Water: stop gliding on entering water.
- Fully Controlled Flying: fly only as the movement, jump, and sneak keys say, hovering without input, with adjustable horizontal and vertical speeds.
- Partially Controlled Flying: speed up, slow down, climb, and descend with the movement keys on top of vanilla gliding, with adjustable rates, natural descent, natural acceleration, and maximum speed.
- No Crash: slow down before flying into a wall or an unloaded chunk.
- Autopilot: fly on forever by changing only the pitch.
- Elytra Replace: swap in a spare elytra when the worn one is nearly broken, with an adjustable minimum durability.
- Chest Swap: put on an elytra from the inventory when trying to glide in a chestplate, optionally put the chestplate back afterwards, and swap between them with a key.
- Elytra Boost: push the glide like a firework rocket with a key, using a client-side rocket with an adjustable duration.
- Insta Stop: end the glide with a key.
- All features are off by default.
- Add a main switch that turns all features on or off at once, with its own toggle key binding, separate defaults for singleplayer worlds and allowed servers, and options to reset to the default on world exit or game exit.
- Add a toggle key binding for each feature and an "Open elytra Settings" key binding, all unbound by default.
- Add a configuration screen built from vanilla widgets, also available through Mod Menu, which is optional.
- Add multiplayer modes (disabled, whitelist, blacklist) and a server list. The mod is disabled on multiplayer servers by default.
- Save all settings to `config/elytra.json`.
- Add English and Simplified Chinese translations.
