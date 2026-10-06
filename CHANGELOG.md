# Changelog

## 3.0 — Portal Transfer Overhaul
**Minecraft 1.21.1 · NeoForge · Create 6.0.10**

### Portal kinetics (shafts & cogs)
- Portal shaft / cog placement now follows Create’s train-track portal rules
- Fixed duplicate auto-placed shafts/cogs on wide portals (4+ blocks)
- Auto-placed exits no longer re-trigger linking during `setBlock` neighbor updates
- Breaking either side of a shaft link removes the partner; auto-placed exits never drop items

### Portal fluids (pumps & pipes)
- Place **one mechanical pump** against a portal — the other dimension gets a **fluid pipe**, not a second pump
- Fluids cross through a shared portal bridge; only a real tank behind the pump can feed the bridge
- Open pipe ends are no longer treated as infinite sources (fixes unlimited fluid with no tank attached)
- Breaking the pump removes the exit pipe (no free pipe item)
- Breaking the exit pipe breaks the linked pump and returns the pump item
- Auto-placed exit pipes never drop when the link is torn down

