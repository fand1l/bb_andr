# Block Puzzle

[![Android](https://github.com/fand1l/bb_andr/actions/workflows/android.yml/badge.svg)](https://github.com/fand1l/bb_andr/actions/workflows/android.yml)

An offline Android block-puzzle game in the Block Blast style — the same core loop, none of
the advertising. Written in Kotlin with Jetpack Compose.

**No ads, no tracking, no analytics, no in-app purchases.** The manifest does not request
`INTERNET`, so the app physically cannot reach the network. The only permission it asks for
is `VIBRATE`.

## Gameplay

The loop is the genre standard, implemented in full:

- **8x8 playfield.** Pieces are dragged from a tray of three onto any free space that fits.
  They cannot be rotated — placement is the whole puzzle.
- **Three pieces at a time.** A new set of three is dealt only once all three have been used,
  so the order you spend them in matters.
- **Rows and columns clear.** Any full row *and* any full column go at the same time; a drop
  that completes both is worth two lines, and the shared cell is counted once.
- **Combos.** Clearing on consecutive drops raises a combo level that multiplies the clear
  bonus (+50% per level, plateauing at level 10).
- **All clear bonus.** Emptying the board pays a flat 300 on top.
- **Game over** when none of the pieces still in the tray fits anywhere.

### Scoring

| Event | Points |
| --- | --- |
| Placing a piece | 1 per cell |
| Clearing *n* lines at once | `10 · n(n+1)/2` → 10 / 30 / 60 / 100 … |
| Combo level *c* | multiplies the clear bonus by `1 + 0.5(c−1)`, capped at *c* = 10 |
| Board left empty | +300 |

The numbers live in one place, `ScoreRules`, if you want to tune them.

### Piece dealing

44 silhouettes: 1–5 cell bars in both orientations, 2x2 / 2x3 / 3x2 / 3x3 blocks, small and
large corners, J/L/T tetrominoes in all four rotations, S/Z, a plus, and 2- and 3-cell
diagonals. Each carries a weight, so awkward large pieces show up less often.

**A freshly dealt tray can always be emptied.** Not "one of the three fits" — there is an
order and a set of anchors that drops all three, counting the room that opens up when a drop
clears a line. The deal never buries you; losing is always a consequence of how you spent the
tray, which is where the difficulty of the genre actually lives. A tray also never holds three
copies of the same silhouette.

That promise is kept two ways:

1. **Roll and check.** A tray is rolled the ordinary weighted way, then `TraySolver` searches
   every order and every anchor — applying clears between drops — for a line of play that
   empties it. On an open board the first roll nearly always passes, so the piece mix stays
   completely natural.
2. **Deal by simulation.** When the board is tight enough that rolls keep failing, the pieces
   are picked one at a time against a board played forward as it goes: each silhouette is
   chosen from those that fit the board *as it will look* once the previous pieces have
   landed. The tray then arrives with a witness by construction, no search needed.

Step 2 cannot stall: every pool contains the single-cell piece, so it only runs dry on a
completely full board — and a settled board is never full, because filling the last free cell
would complete its row and clear it.

Measured over 2 800 deals in simulated games, 8.1% of them would not have been fully playable
without this — 1.2% would have been an outright dead hand. The search runs on a bitboard (an
8x8 playfield is exactly 64 cells, so occupancy is one `Long`) and costs a few tens of
microseconds per deal.

### Tailored trays

Every so often the dealer stops rolling and goes looking for the three silhouettes that open
the current board up the most. It is still only an opportunity — the tray makes a clearing
line of play *exist*, and finding it stays the player's job.

Two things trigger the hunt:

- **An opener**, on a cooldown of several deals and a dice roll, with better odds when the
  board is getting away from you. It looks for the tray that wipes the most.
- **A sweep**, whenever taking the whole board off is actually within reach. Emptying an 8x8
  board with three pieces needs what is left to be concentrated — all of it inside a few rows
  or a few columns, close enough to complete that one tray can finish them. A single cheap
  pass rules that out for nearly every position, so the beam search only runs when there is
  something to find. That window is narrow and short-lived, which is why it does not wait on
  the long cooldown.

The search optimises for the *emptiest moment* along the line of play rather than the state
after the last piece — the third piece has to land somewhere, and what the player sees is the
board going clean.

Simulated over 400 games each way, with a player that plans the whole tray:

| | without tailored trays | with |
| --- | --- | --- |
| games containing a full wipe | 2.3% | **13.3%** |
| mean pieces placed | 141 | 200 |
| mean score | 1 178 | 1 720 |

Tailored trays land on about 8% of deals. The cooldown is persisted with the save, so closing
the app cannot farm them.

### Feel

- The held piece floats about one cell above your finger and keeps the grab point you picked
  up, so it never snaps awkwardly to a corner.
- The target cells are previewed on the board, and any row or column the drop would complete
  lights up before you let go.
- Dropped blocks pop in; cleared blocks expand and fade out.
- Procedurally synthesised sound effects (no bundled audio assets) with pitch that climbs
  through a combo, plus vibration. Both can be switched off.
- Your run is saved after every move, so closing the app mid-game loses nothing.
- English and Ukrainian localisation.

## Project layout

```
engine/   Pure Kotlin/JVM rules engine — board, pieces, dealing, scoring, save format.
          No Android dependencies, covered by unit tests.
app/      Android app: Compose UI, drag and drop, animations, sound, persistence.
```

Keeping the rules in a plain JVM module is what makes them testable without an emulator.
`engine/src/test` holds 64 tests. Among them: a fuzz pass that plays 120 complete games with
random legal drops and checks the invariants on every turn (cell accounting, no surviving full
line, monotonic score, combo bookkeeping, unique piece identities, save/restore parity, and
that every fresh deal can be emptied); and a cross-check of the bitboard search against a
deliberately simple reference search over 400 random positions, where both have to agree and
both witnesses have to be playable.

## Building

Requires Android Studio (Ladybug or newer) or a local Android SDK with API 35.

### JDK requirement

**Build on JDK 21 or 17. JDK 22+ will not work.**

The Kotlin DSL compiler bundled in Gradle 8.11.1, the Kotlin Gradle Plugin 2.0.21 and AGP
8.7.3 all reject newer version strings. On JDK 25 the build fails while compiling the
`.kts` scripts, with nothing but the version number as the error message:

```
* What went wrong:
25.0.4
```

`gradle/gradle-daemon-jvm.properties` pins the Gradle JVM to 21, so a matching JDK is picked
automatically when one is installed. If it is missing you get a message that says so plainly
instead of the cryptic failure above.

Installing one, if needed:

```bash
sudo dnf install java-21-openjdk-devel     # Fedora / RHEL
sudo apt install openjdk-21-jdk            # Debian / Ubuntu
brew install openjdk@21                    # macOS
```

In Android Studio the bundled JetBrains Runtime already satisfies this; check it under
*Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK*.

### Commands

```bash
./gradlew :engine:test        # rules engine unit tests
./gradlew :app:assembleDebug  # debug APK -> app/build/outputs/apk/debug/
./gradlew :app:assembleRelease
```

### Getting an APK without building it

Every push runs the tests and builds a debug APK on GitHub Actions. It is signed with the
auto-generated debug key, so it installs on a phone as-is:

> Actions → **Android** → the run you want → **Artifacts** → `block-puzzle-debug-apk`

Pushing a `v*` tag additionally builds a release APK and attaches it to a GitHub release.
That build is signed only if these repository secrets exist, and produces an unsigned
(therefore uninstallable) APK otherwise:

| Secret | What it holds |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the keystore file, base64 encoded |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias inside the keystore |
| `ANDROID_KEY_PASSWORD` | key password |

Creating one, if you do not have a keystore yet:

```bash
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias blockpuzzle
base64 -w0 release.jks    # paste into ANDROID_KEYSTORE_BASE64
```

Nothing about signing lives in the repository — `app/build.gradle.kts` reads it from the
environment and skips signing entirely when it is absent.

- `minSdk` 24, `targetSdk`/`compileSdk` 35, Java 17.
- Release builds are minified and resource-shrunk.
- Signing is not configured; add your own keystore before shipping a release build.

Toolchain: AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01, Gradle 8.11.1 (wrapper included).
