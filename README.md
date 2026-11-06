# RiftKingExtras (Fabric, Minecraft 26.2, client-side)

Extras for Hypixel SkyBlock:

- **Collection tracker HUD:** track up to **7 collections** at once. Each one shows amount, Elite rank, session gain and
  items per hour. Every 5 minutes the mod syncs with Elite; pickups are counted locally in between, so the shown value
  is already right when Elite catches up.
- **Modern HUD:** dark rounded panel with a header, one card per collection and a colored bar on the left of each card.
  The Rift collections have fixed colors: Caducous Stem red, Living Metal Heart blue, Hemovibe dark red, Timite light
  blue, Half Eaten Carrot orange, Wilted Berberis brown, Agaricus Cap light red. Every other collection gets a stable
  color made from its name; to pick your own, set `"color": "#FF8800"` for that collection in
  `config/riftkingextras.json`. A thin line at the bottom of each card shows how much time is left until the rate timer
  pauses. Position **and size** (25% to 200%) are set under Move HUD (`/rke hud`).
- **Dropped items:** dropping an item and picking it up again is no gain (what you dropped is remembered for 5 minutes,
  the time an item needs to despawn). If Elite shows less than the mod shows, pickups of the last 10 minutes are kept
  (Elite can be a few minutes behind), anything beyond that was counted wrongly and is removed from the total and from
  the +N.
- **Gains you did not see:** when the Elite value is higher than what the mod counted (e.g. items taken out of a minion
  menu, or 1 Hemoglass = 160 Hemovibe), the difference is added to the green +N of the session. Items per hour still
  uses only what was seen entering the inventory.
- **Rate timer with auto pause:** the items/hour value is calculated from the time the timer was actually running.
  Each collection has its own timer: the first gain starts it, and it pauses automatically after 60 seconds without a
  gain. The next gain resumes it. Every collection shows **Up** (time the timer was running), **Down** (time it was
  paused) and a status (WAITING / ACTIVE / PAUSED). When the timer pauses, the idle minute is moved from Up to Down, so
  Down is the real pause time and the rate is not dragged down by it. The rate stays frozen while paused. The timer
  A separate **ALL** line shows the overall uptime and downtime of all collections together (active while any
  collection gains, paused after 60 seconds without any gain; its label is green/yellow/gray like the status). The timer
  lines can be switched off in the settings ("Timers in HUD"). Timers only run while you are in a world and restart each
  game session.
- **Minion Time Warp HUD:** a second, separate HUD in the same style. When the chat message "You have successfully
  warped time for your Minion" appears, a **48 hour real-time countdown** starts. The HUD shows the remaining time, when
  it is ready again (your local time), a progress bar and a pulsing READY when you can use it again. The end time is
  saved, so the countdown keeps running while the game is closed. It has its own position and size (`/rke timewarp hud`)
  and can be switched off in the settings. The message on Hypixel is "TIME WARP! You have successfully warped time for
  your Minions!" (the Enchanted Clock sends the same line for the Chocolate Factory and Aging Items, those are ignored).
  The "TIME WARP!" prefix is skipped, then the message has to *start* with the phrase, so other players cannot trigger it
  by typing it in chat. The phrase can be changed with `timewarpMessage` in the config. `/rke timewarp last` shows the
  last chat line the mod saw that contained "warped time", to check what arrives. If you missed the message:
  `/rke timewarp start` starts the timer by hand, `/rke timewarp reset` clears it.
- **Scoreboard HUD:** the Hypixel SkyBlock sidebar redrawn in the same style as the other HUDs (dark rounded panel,
  title in the header, accent colored line, thin separators). The vanilla sidebar is hidden while it is shown.
  - Lines are sorted into elements (date & lobby code, SkyBlock date, time, area, purse/piggy, bits, other currencies,
    objective, slayer quest, events, other lines, footer) plus extras: real time, the Minion Time Warp cooldown, **ping**
    and **server TPS** (the TPS is an estimate from the corrections of the world time that the server sends).
    Under `Lines & order` every element can be switched on/off and moved up/down, and separators can be added or removed.
  - Numbers of currency lines can be shown as on Hypixel, short (1.2M) or long (1,234,567).
  - Custom title and custom footer with `&` color codes, accent color, text shadow, text and title alignment, line
    spacing, hiding separators at the start/end, condensing consecutive separators.
  - Hide it while Tab, chat or F3 is open. "Outside SkyBlock" restyles other scoreboards too.
  - Position and size (25% to 200%) with `/rke scoreboard hud`; without a saved position it sits at the right edge,
    vertically centered. While you are not on SkyBlock, the editor shows a sample board.
  - The mod finds the vanilla sidebar HUD element by itself; `/rke scoreboard status` shows what it hooked.
    `/rke scoreboard dump` lists the lines it reads, with the element each one was sorted into.
  - If the vanilla sidebar was visible at startup although "Hide vanilla one" is ON, or the game stops at startup with an
    error mentioning `scoreboard_sidebar`, set `"hideVanilla": false` in `config/riftkingextras-scoreboard.json`.
    Turning the option ON again needs a game restart (turning it OFF works immediately).
- **Inventory HUD:** a fourth HUD in the same style. Top: health bar and mana bar with numbers. Left: the 4 equipment
  items, right: the 4 armor pieces, in the middle your whole inventory (3 rows) above the hotbar, the selected hotbar slot
  is marked. Bottom: speed (left), the rift time bar with its number (middle) and rift damage (right).
  - Every item gets a **1 pixel frame in its rarity color** (taken from the rarity line of the item lore: common,
    uncommon, rare, epic, legendary, mythic, divine, special, very special, ultimate ...). The frame goes around the item
    slot, it does not follow the shape of the item texture.
  - **Rift or not:** the mod detects the Rift (the area line of the sidebar starts with the Rift symbol, or the action
    bar shows the rift time). In the Rift: health from the vanilla hearts, bottom bar = rift time, bottom right = rift
    damage. Everywhere else on SkyBlock: health from the action bar, bottom bar = SkyBlock level and xp to the next
    level (the vanilla xp bar shows both), bottom right = defense. Mana comes from the action bar in both cases.
  - The bars are thin (8 pixels); change `BAR_H` in `InventoryHud.java` if you want another height.
  - It **hides the vanilla hotbar** while it is shown (option, on by default). A second option (also on by default)
    hides the vanilla hearts, armor and food bars, the xp bar with its level number and the action bar. It only shows on
    SkyBlock unless you switch that off.
  - Mana and rift time are read from the action bar ("214/214 Mana", "73m16s Left"), speed and rift damage from the
    tab list. In the Rift the health is not in the action bar, so the bar shows your vanilla hearts (15 hearts = 15/15);
    on the other islands it uses the real health from the action bar. A wrong format only shows "-".
    Speed comes from the tab list when it has a "Speed" line. In the Rift it does not: open `/stats` once, the mod reads the
    speed from the menu and saves it. It also uses that number to calibrate your movement speed attribute, so the shown
    speed then follows equipment changes by itself. Without a calibration the attribute is used as value x 1000.
    `/rke stats` shows which source is used and which 'speed' items the last stats menu had.
    Speed and rift damage show gray while they are the last known values (saved in the config) and in color once the tab
    list has given a fresh one.
    The action bar is read directly from the game, so hiding it does not stop the mod from reading it.
    `/rke stats` shows what was read and what arrives.
  - SkyBlock keeps the equipment out of the normal inventory: the mod reads it when the "Your Equipment and Stats" menu
    (`/stats`) is open, remembers it and saves it in the config.
  - **Ability cooldown timers:** hotbar items with an ability (e.g. Weird Tuba) get a timer. The mod reads the ability
    and its cooldown from the item lore. When you use it, SkyBlock shows its name in the action bar (`-120 Mana (Howl)`):
    that starts the timer. The item is darkened like with the vanilla cooldown, the layer shrinks, and the seconds left
    are shown on it. Cooldown reductions (e.g. from your class) are not known to the mod, so the timer can run longer
    than the real cooldown. Only abilities that cost mana show their name in the action bar, so only those are timed.
    Switch it off with "Ability cooldown timers".
  - The **equipment** is saved in the config when you open `/stats`, so it is still there after a restart (the
    saved items are read again once you are in a world).
  - Settings: `/rke inventory`, position and size: `/rke inventory hud`.
- **Music HUD:** a fifth HUD in the same style, modeled after the SpotifyOverlay mod. It shows what is playing: cover,
  title (long titles scroll), artist, a progress bar with the times, and below it the **synced lyrics** that scroll
  smoothly in time with the song (3, 5 or 7 lines, current line highlighted, timing adjustable in steps of 100 ms).
  - **Works with the free version of Spotify, no Premium and no account link:** the song comes from the operating
    system's media session (the one behind your media keys), the lyrics come from the free public service
    [LRCLIB](https://lrclib.net) and are fetched while you play, nothing is stored in the mod. If LRCLIB has no synced
    lyrics for a song, the HUD says so.
  - Windows: a small helper program (`RiftKingMusicHelper.exe`, built from `MusicHelper/` and packed into the jar)
    reads the media session. It uses the **.NET 8 Runtime (x64)**, which must be installed on the PC (the plain runtime is
    enough, not the SDK or the Desktop runtime). Linux: install `playerctl`. macOS is not supported.
  - Keys (all changeable under Options > Controls): Right = next song, Left = previous, Down = restart the song,
    Up = play/pause, Page Up / Page Down = volume of the Spotify app (the volume shows in the header for a moment).
  - The profanity filter replaces strong swear words in the lyrics with stars (option, on by default).
  - Settings: `/rke music`, position and size: `/rke music hud`, `/rke music status` shows the source, the song, the
    lyrics state and the cover. Without a song playing the editor shows a sample.
- **Inventory screen (key E):** all features of the RiftKingInv mod, in the style of the HUDs. The normal inventory is
  replaced by a dark rounded panel with the RiftKingExtras header: armor on the left, the four **equipment** items
  (necklace, cloak, belt, gloves) on the right, your character in the middle, the inventory and hotbar below. Every slot
  has a **1 pixel frame in the rarity color** of its item (gray for an item without rarity, dark gray for an empty slot).
  Two columns of **menu buttons** sit at the sides; left click and right click open different SkyBlock menus (hover for
  the command): collection / recipes, quests / chapters, skills / levels, guides, craft, storage / sacks, pets /
  loadouts, warps / is. A click on an equipment slot runs `/stats`. Switch it off with "Inventory screen (E)" under
  `/rke inventory`. **Remove the RiftKingInv jar**, both mods would replace the inventory.
- **Tracked players:** add up to 6 player names (`/rke players`). The HUD shows a table with each player's collection,
  amount and rank and how much more (+) or less (-) you have than them. **Every player has his own collection**: click
  the collection button of a player to switch it, so player 1 can be on Timite while player 2 is on Agaricus Cap. A newly
  added player gets the collection set in "New player's collection". Their values are from the last Elite sync, yours is
  live, so the difference can be off by what Elite has not seen yet. Older configs are converted (all players keep the
  collection they were compared on before).
- **Settings screen** with the RiftKingExtras logo: press **H** (changeable in Options > Controls) or type `/rke`.
  From there: collections, tracked players, move and resize the HUD (sliders, live preview) and a manual sync.
- **Mushroom helper:** when the brown mushroom you look at turns into a red mushroom, the mod sends a single left click
  to break it, 2 to 5 ticks (random) after the change. The click is cancelled if you look away or the block changes in
  between. It never rotates your view, moves you or changes your hotbar slot.

> **Warning:** automatically clicking in reaction to the game state counts as a macro on Hypixel and can get you banned.
> You use this at your own risk. The tracker HUD alone only reads data.

## Adding collections
Open the settings > Collections and type the collection name as on the Elite leaderboard, e.g. `Wilted Berberis`
(leaderboard `wilted-berberis`). The name is also used to count pickups: an item named exactly like that entering your
inventory counts as +1 (not while a menu is open).

Some collections are fed by several items. **Timite** is built in: Youngite, Timite and Obsolite each count as 1 for it.
For any other collection, list the items in `itemNames` of that collection in `config/riftkingextras.json`, e.g.
`"itemNames": ["Item A", "Item B"]`; every listed item counts as 1.

## Commands
```
/rke                 open the settings screen
/rke collections     add or remove tracked collections
/rke players         add or remove tracked players
/rke hud             move the HUD
/rke music           music HUD settings
/rke music hud       move and resize the music HUD
/rke music status    what the music HUD currently reads
/rke inventory       inventory HUD settings
/rke inventory hud   move and resize the inventory HUD
/rke stats           show what the inventory HUD read (health, mana, rift time, speed, items, hooks)
/rke scoreboard      scoreboard settings
/rke scoreboard dump  show the sidebar lines the mod reads and how it sorts them (for debugging)
/rke scoreboard status  show how the vanilla sidebar was hooked
/rke session reset   clear the session gain and all timers (they otherwise only reset when the game restarts)
/rke scoreboard hud  move and resize the scoreboard HUD
/rke sync            sync with Elite right now
/rke test            show the raw Elite responses for every collection (for debugging)
```

## Config (config/riftkingextras.json)
`apiBase`, `profilePath` and `rankPath` use the same defaults as the NameTagMod (config/nametaglines.json).
If you changed them there, use the same values here. A fresh config starts with Agaricus Cap.

## Privacy
Your Minecraft UUID is sent to the Elite API (api.eliteskyblock.com) for the lookups. For tracked players, the name is
sent to Mojang (name -> UUID) and the UUID to the Elite API. Please make sure you are allowed to use that API before
sharing the mod with others.

## Building (GitHub)
1. Create a new GitHub repository and upload the contents of this folder (keep `.github/` and `MusicHelper/`).
2. Open the **Actions** tab, wait for the `build` run, then download the artifact **RiftKingExtras-jar**.
3. Put `RiftKingExtras.jar` into your `mods` folder (needs Fabric Loader + Fabric API for 26.2, Java 25).

The workflow installs the .NET 8 SDK and builds the Windows music helper into the jar. The helper does not bring its own
runtime, so it stays small; it needs the .NET 8 Runtime on the PC where you play. If a local build has no `dotnet`, the
mod still builds, but the music HUD then only works on Linux.

Local build: `gradle wrapper && ./gradlew build` (JDK 25) -> `build/libs/RiftKingExtras.jar` (not the `-sources.jar`).

## If the build fails on API names
The project was written against the API names of the working NameTagMod, but it could not be compiled here.
The places most likely to need a fix:
- `TrackerHud.rect(...)`: the fill method of `GuiGraphicsExtractor` (everything in the HUD is drawn with it)
- `MushroomBreaker`: `mc.hitResult`, `mc.gameMode.startDestroyBlock(...)`, `swing(...)`
- `CollectionTracker.countInInventory`: `getInventory()`, `getHoverName()`
- `InventoryHud`: `Player.getItemBySlot(EquipmentSlot)`, `Player.inventoryMenu`; the selected hotbar slot is found by reflection
- `mixin/SlotAccessor`, `mixin/AbstractContainerScreenAccessor`, `RiftInventoryScreen`: taken over from RiftKingInv, which builds on 26.2 (item drawing, `fill`, `text(Component)`, tooltips and the character preview are verified by it)
- `ItemRarity`: `DataComponents.LORE` / `ItemLore.lines()`
- `EquipmentStore`: `ItemStack.CODEC`, `RegistryOps.create(JsonOps.INSTANCE, ...)`, `mc.level.registryAccess()`
- `NetStats`: `mc.level.getGameTime()`, `ClientPacketListener.getPlayerInfo(UUID)`, `PlayerInfo.getLatency()`
- `AbilityCooldowns`: `DataComponents.LORE` / `ItemLore.lines()`
- `StatsTracker`: `mc.getConnection().getOnlinePlayers()`, `PlayerInfo.getTabListDisplayName()`
- `MusicArt`: `NativeImage.read(...)`, `DynamicTexture`, `TextureManager.register(...)`, `RenderPipelines.GUI_TEXTURED` (copied from the SpotifyOverlay code, which runs on 26.1)
- `MusicKeybinds`: `InputConstants.KEY_RIGHT/LEFT/UP/DOWN/PAGEUP/PAGEDOWN`
- `MusicHelper/`: the C# helper needs the .NET 8 SDK; if the Windows part does not build, send me the log of the `buildMusicHelper` step
- `ScoreboardReader`: the scoreboard classes (`Scoreboard`, `Objective`, `PlayerScoreEntry`, `PlayerTeam`, `DisplaySlot`)
- `RiftKingExtrasClient`: `HudElementRegistry.replaceElement(...)` with the id `scoreboard_sidebar`
- `ScoreboardHud`: `text(...)` with a `Component`, `mc.debugEntries.isOverlayVisible()`, `mc.options.keyPlayerList.isDown()`
- `RiftKingExtrasClient`: `ClientReceiveMessageEvents.GAME` (chat message for the Timewarp timer)
- `CollectionsScreen` / `TrackedPlayersScreen`: `EditBox.getValue()` / `setHint(...)`

## Credits
The inventory screen comes from RiftKingInv (also by MelonKingDe).

The music HUD is modeled after the SpotifyOverlay mod (CC0, public domain); `MusicHelper/` is a rewrite of its helper and
`VolumeControl.cs` comes from it.

The scoreboard feature set is modeled after the CustomScoreboard mod by meowdding (LGPL 2.1), used with its author's
friend's permission for personal use. RiftKingExtras contains its own implementation and no code from that mod.
