# Mob Possession

A Fabric mod for Minecraft 26.2 by Kaius Cole.

Right-click a mob (empty hand) to jump into its body and control it.
Sneak to get back out.

## How it works

- Right-click a mob -> its AI turns off, your player goes invisible and
  parks in place, and your movement gets redirected into the mob instead.
- Look direction becomes the mob's facing.
- Sneak (shift), or let the mob die, and you're back in your own body.

## Known limitations (v0.1)

- Jumping isn't wired up yet - the mob won't jump when you press space.
- Only tested against Minecraft 26.2 with Fabric Loader 0.19.5.

## Building

```
./gradlew build
```

Needs a JDK 21+ to run Gradle, and a JDK 25 toolchain for compiling
(Loom will fetch/us it via your configured JAVA_HOME).

The built mod jar lands in `build/libs/`.
