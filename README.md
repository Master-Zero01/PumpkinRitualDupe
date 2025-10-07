# 🎃 Pumpkin Ritual Dupe
*A spooky and configurable Halloween event plugin for Minecraft Paper servers.*

![Halloween Banner](https://i.imgur.com/1I4Xg5Q.png)

**PumpkinRitualDupe** adds a custom Halloween ritual mechanic where players can perform a mystical ceremony using pumpkins and lightning to *duplicate* an item they hold — if the pumpkins approve.  

It’s lightweight, configurable, and designed to bring that eerie Halloween atmosphere to your world without relying on datapacks or external dependencies.

---

## 🪄 Features

- 🧱 **Configurable Ritual Setup**
  - Choose your own *center block* (default: `JACK_O_LANTERN`)
  - Choose your own *surrounding block* (default: `CARVED_PUMPKIN`)
- ⚡ **Lightning Ritual Effect**
  - Lightning strikes the ritual circle upon success!
- 💰 **Item Duplication**
  - The held item is duplicated at the ritual center when the ritual succeeds.
- 🎲 **Chance-Based Success**
  - Fully configurable success percentage — from 0% to 100%.
- ⚙️ **Lightweight and Simple**
  - No dependencies, no complex setup, works instantly out of the box.
- 🕯️ **Perfect for Holiday Events**
  - Great for Halloween-themed servers, minigames, or mysterious world lore.

---

## 🧩 How It Works

Players build a **3×3 ritual circle** on the ground, with a *center block* and *8 surrounding blocks*.  
Then, they hold any item in their main hand and **right-click the center block**.

If the ritual is built correctly and the pumpkins approve (based on chance), lightning will strike and the held item will duplicate!

---

### 🎃 Default Ritual Layout

Top-down view:

🎃 🎃 🎃
🎃 💡 🎃
🎃 🎃 🎃

- 💡 = `JACK_O_LANTERN` *(configurable `center-block`)*
- 🎃 = `CARVED_PUMPKIN` *(configurable `surround-block`)*

Right-click the center block while holding the item you wish to duplicate.

---

## ⚙️ Configuration

`config.yml`

```yaml
# Chance (in percent) for the ritual to succeed
success-chance: 100.0

# The block that must be right-clicked to start the ritual
center-block: JACK_O_LANTERN

# The block that must surround the center (8 total)
surround-block: CARVED_PUMPKIN
