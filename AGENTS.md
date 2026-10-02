# AGENTS.md

Instructions for AI coding agents working in this repository. `CLAUDE.md` imports this file, so keep all agent guidance here.

## Project

`elytra` is a client-side [Fabric](https://fabricmc.net/) mod for Minecraft: Java Edition that gives the local player more control over elytra flight: gliding without an elytra or not at all, starting and stopping glides, steering them with the movement keys, keeping them clear of walls, swapping elytras and chestplates, and boosting with client-side firework rockets.

The configuration screen, its settings layout, and the multiplayer rules follow [urntt/nojumpdelay](https://github.com/urntt/nojumpdelay). Consult it for verified 26.x API usage and screen patterns; keep the two mods' conventions aligned.

## Project decisions

These decisions are settled. Do not deviate from them without the user's explicit approval.

### Minecraft and toolchain

- Development started on Minecraft 26.3 with Java 25. `gradle.properties` is the single source of truth for the mod, Minecraft, Fabric Loader, Loom, Fabric API, Mod Menu, and Java versions. `build.gradle`, `fabric.mod.json`, the mixin config, and the CI workflows read them from there; do not restate them elsewhere.
- Follow the latest official Fabric template ([FabricMC/fabric-example-mod](https://github.com/FabricMC/fabric-example-mod), also available from the [template generator](https://fabricmc.net/develop/template/)): the `net.fabricmc.fabric-loom` Gradle plugin, Mojang's official names with no `mappings` dependency, and `implementation` (not `modImplementation`) for dependencies. Do not use Yarn.
- Pin `loom_version` to a release version instead of the template's `-SNAPSHOT`, so builds are reproducible.
- Target only the latest stable (release) Minecraft version. Updates, fixes, and new features are always developed against it. Snapshots, pre-releases, and release candidates are not supported targets.
- Do not maintain older Minecraft versions and do not set up multi-version builds (no per-version branches, no Stonecutter or other preprocessors). When a new stable version is released, port the mod to it and drop the previous one.

### Identity

| Item | Value |
| --- | --- |
| Mod ID | `elytra` |
| Display name | `elytra` |
| Maven group | `com.urntt` |
| Base package | `com.urntt.elytra` |
| Version format | `<SemVer>+<Minecraft version>`, for example `1.0.0+26.3` |
| License | MIT |

The mod version itself follows [Semantic Versioning](https://semver.org/); the `+<Minecraft version>` suffix is build metadata naming the Minecraft version the build targets. Version numbers start at `1.0.0`.

### Distribution

- Releases are published only as GitHub Releases. Do not publish to Modrinth, CurseForge, or any other mod platform, and do not add publishing tooling for them.
- `README.md` must clearly warn that this is a movement modification: it may conflict with server movement checks (the `player_movement_check` and `elytra_movement_check` game rules) and anti-cheat systems, and using it in multiplayer may get the player set back, kicked, or banned. It must also state that the mod does not try to bypass any anti-cheat and is disabled in multiplayer until the player enables it on the configuration screen.

### Scope and behavior

- Client-only: `fabric.mod.json` declares `"environment": "client"`. There is no server-side component and no networking beyond the vanilla packets a player sends anyway (the "start fall flying" command and inventory clicks).
- The mod does not try to bypass or hide from anti-cheat systems. Do not add packet spoofing (for example faking `onGround` to avoid fall damage), movement disguises, or anything else whose purpose is to get past server checks.
- Only the local player (`LocalPlayer`) is affected. Every other entity, including other players and the integrated server's copy of the local player, keeps vanilla behavior.
- The features are listed in `Feature`. Each has its own toggle key binding, unbound by default; each toggle shows the new state on the action bar and saves it.
- Features that act on their own are off by default. Features that only act through their own key (Elytra Boost, Insta Stop) are on by default, because their keys are unbound by default.
- Contradicting features are grouped in `Feature.Group`: turning one on turns the others in its group off (Fake Elytra and No Gliding; Fully Controlled Flying, Partially Controlled Flying, and Autopilot).
- `FeatureController` is the single owner of whether a feature is active: it is turned on and the current scene (singleplayer or a multiplayer server, determined on join) is allowed.
- The multiplayer mode is a hard limit: `DISABLED` (the default) rules out every server, `WHITELIST` allows only servers in the server list, and `BLACKLIST` allows every server except those in it. On a ruled-out server every feature stays off and the keys only report that the mod is disabled there. Joining another player's LAN world or a Realm counts as multiplayer. Server list entries match like nojumpdelay's: by host (case-insensitive, after IDN conversion, valid domain name or IP address) and by port only when the entry specifies one.
- nojumpdelay's separate singleplayer and server defaults and its reset rules are not part of this mod; the per-feature toggle states simply persist.
- `GlideController` owns gliding on top of the server's fall flying flag. Fake Elytra and Ground Glide keep a client-side glide that the server does not know about; everything else starts and stops glides with the vanilla "start fall flying" command, which the server answers by starting a glide or by stopping one in progress. With a usable elytra, a client-side glide is handed back to the server once the player is airborne again.
- Elytra Boost spawns firework rockets on the client only, with negative entity IDs, and removes them after the configured duration.
- Chest Swap and Elytra Replace move items only with ordinary clicks in the player's own inventory menu, never while another container is open or an item is on the cursor.
- The numeric settings are listed in `Tuning` with their defaults, ranges, and slider steps.
- Autopilot's pitches and switching speeds are constants in `FlightControl`, chosen by simulating vanilla's glide physics (see the comment there). Re-check them if Mojang changes `LivingEntity.updateFallFlyingMovement`.
- The configuration screen is built from vanilla widgets and opens through Mod Menu or the "open settings" key binding, which is also unbound by default.

### Localization

- All user-facing text, including key binding names, the key binding category, action bar messages, and the configuration screen, uses translation keys. Never hard-code display strings.
- Provide translations for `en_us` and `zh_cn`, and keep both complete whenever a translation key is added or changed.

### Dependencies

- Required: Fabric Loader and Fabric API.
- Optional: Mod Menu, declared under `suggests` in `fabric.mod.json`. The mod must load and work normally without it, so Mod Menu classes may only be referenced from the Mod Menu entrypoint.
- Configuration is hand-written without a config library: a JSON file in the Fabric config directory, serialized with Gson (bundled with Minecraft). Any configuration screen uses vanilla widgets.
- Do not add other dependencies without the user's explicit approval.

### Implementation

- Language: Java only.
- Source sets: `src/main` holds only `fabric.mod.json` and the icon. All code and client resources live in `src/client`, and the client game tests live in `src/gametest`.
- Mixins: prefer the MixinExtras injectors bundled with Fabric Loader (for example `@ModifyExpressionValue` and `@WrapOperation`) over `@Redirect` and `@Overwrite`, to stay compatible with other mods and keep porting work small. Every handler checks for `LocalPlayer` before changing anything.

### Testing

- The client game tests in `src/gametest` start Minecraft and measure every feature against vanilla in the same setup:
  - `ElytraLogicGameTest`: defaults, the configuration file, slider values, contradicting features, the multiplayer rules, and address matching.
  - `ElytraGlideGameTest`: toggle keys, Instant Fly, Insta Stop, No Gliding, Fake Elytra, Ground Glide, Stop Flying in Water, and screenshots of the settings screens.
  - `ElytraFlightGameTest`: Fully and Partially Controlled Flying, No Crash at a wall and at an unloaded chunk, Elytra Boost, and Autopilot.
  - `ElytraEquipmentGameTest`: Elytra Replace and Chest Swap, on the client and on the server.
  - `ElytraMultiplayerGameTest`: each multiplayer mode on a local dedicated server.
- Keep them passing and extend them when behavior changes.
- The dedicated server needs `eula = true` in the `configureTests` block of `build.gradle`; it accepts the Minecraft EULA only for that local test server.
- After porting to a new Minecraft version, run the client game tests. A successful build does not prove that the mixins still have the intended effect.
- `README.md` describes how to run them, including on a headless machine.

### CI, releases, and changelog

- GitHub Actions (`.github/workflows/build.yml`) builds the project and runs the client game tests on every push and pull request.
- Maintain `CHANGELOG.md` following [Keep a Changelog](https://keepachangelog.com/). Record every user-visible change under `Unreleased` in the same change that introduces it.
- `.github/workflows/release.yml` builds the mod and publishes a GitHub Release for the project version, with the jar attached and the matching `CHANGELOG.md` section as release notes. It runs when a tag `v<version>` (for example `v1.0.0+26.3`) is pushed, or when started manually on a branch, in which case it creates that tag on the branch's latest commit. It fails if a pushed tag does not match the project version, if the changelog has no section for the version, or if the release already exists.
- Release only when the user asks. To release, set `mod_version` in `gradle.properties`, rename `Unreleased` in `CHANGELOG.md` to `[<version>] - <YYYY-MM-DD>` above a new empty `Unreleased` section, commit, and push. Then start the release workflow on `main`. Claude Code cloud sessions cannot push tags, so start the workflow through the GitHub Actions API instead.

## Engineering principles

- Fix root causes, not symptoms. Diagnose the underlying cause before implementing a permanent fix. If an immediate mitigation is necessary, treat it as temporary and follow through with a root-cause fix.
- Prefer configuration-driven design for values that are expected to vary by environment, deployment, or product requirements. Avoid unexplained or duplicated magic values, but do not introduce configuration where a well-named constant is the clearer source of truth.
- Preserve a single source of truth and clear ownership for data, state, configuration, business logic, and authoritative documentation. Avoid duplicating canonical information across multiple locations.
- Do not maintain parallel legacy and replacement implementations without an explicit migration and removal plan.

## Documentation

- Keep documentation aligned with the code. When a code change affects documented behavior, APIs, architecture, configuration, workflows, or usage, update the relevant documentation in the same change.
- Keep each document's responsibility clear. For example, use `README.md` for project overview and usage, and `VISION.md` for product direction, architectural principles, or long-term decisions.
- Always specify a language identifier for fenced code blocks in Markdown.

## Language

- Communicate with the user in Chinese, including explanations, progress updates, and user-facing planning.
- Use English for development artifacts, including source code, comments, docstrings, documentation, READMEs, Git branch names, commit messages, and other deliverables intended to live in the repository.

## Git

- Do not change or override the Git author or committer identity. When an identity must be configured for commits created during the task, use:
  - Name: `urntt`
  - Email: `urntts@gmail.com`
- Do all actions on the user's behalf. Do not rewrite existing commit authorship unless explicitly requested. Do not add `Co-Authored-By` trailers or session links to commit messages or pull request descriptions.
- Develop on `main` and push directly to it. Branches and pull requests are not required.
- Because changes land on `main` without review, make sure `./gradlew build` and the client game tests pass locally before pushing.
- If a branch is used, give it a category-based prefix that reflects the purpose of the change, such as `feat/`, `fix/`, `refactor/`, `docs/`, `test/`, or `chore/`.
- Follow the [Conventional Commits](https://www.conventionalcommits.org/) specification for commit messages.
