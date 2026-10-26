# Crystal Optimizer — Custom HUD (Fabric, Minecraft Java 1.21.1)

Crystal Optimizer is the project name for this client-side custom HUD demo for Lunar Client's Fabric add-on. It draws a locally simulated balance overlay; it is not a performance optimizer. You can position the overlay over the server's balance display to visually cover it; turn the HUD off to reveal the normal server display again. It does **not** read or alter Donut SMP's server-side money, send real `/pay` commands, or change server data.

## Transparency note
The displayed balance and payment simulator are local-only visual/demo features. They do not represent real server money or real payments. Use the overlay as a personal HUD/demo, not as proof of your actual balance to other players.

## Settings menu
- Press **Right Shift** in-game to open the customization menu.
- Set the fake balance and the label text.
- Adjust HUD X/Y coordinates to place the overlay over the balance you want to visually cover.
- Toggle the HUD on/off. When off, this mod stops drawing the overlay, so the server's normal balance display is visible again.
- Toggle the opaque background on/off.
- Save settings; they persist in `config/donut-fake-hud.json`.

The mod cannot automatically detect or replace Donut SMP's balance text inside the server scoreboard/chat, since that is server UI and can vary. Position the overlay manually. The fake balance is only visible on your own client.

## Commands
- `/fakebal` — show your fake balance in chat
- `/fakebal set <amount>` — set it
- `/fakebal add <amount>` — add fake money
- `/fakebal remove <amount>` — remove fake money
- `/fakebal toggle` — toggle the fake HUD
- `/fakepay <player> <amount>` — simulate a payment locally and subtract it from your fake balance
- `/fakepaylast` — show the last simulated payment

Fake payments are local simulations only; they do not send a server payment or notify the named player.

## Build the mod JAR
Requires Java 21 and an internet connection for Gradle/Maven dependencies. From this folder, run:

```bash
gradle build
```

The built JAR will be in `build/libs/`.

## Add to Lunar Client
Use a compatible Minecraft 1.21.1 Fabric setup in Lunar Client and add the built JAR through Lunar's supported mod installation flow. Compatibility is not guaranteed for every third-party mod.
