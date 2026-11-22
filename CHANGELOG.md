# Changelog

## 3.1 — Two-Pump Fluid Engine & Mining Fixes
**Multi-loader: NeoForge 1.21.1 · Forge 1.20.1 · Fabric 1.20.1 · Create 6**

### Portal Fluids (Two-Pump Mechanical System)
- **Mechanical Pumps on Both Sides**: Placing a mechanical pump facing a portal now automatically creates a linked mechanical pump on the other side—no more unpowered pipes!
- **Rotational Power Required**: Both pumps must be supplied with rotational power to move fluid across dimensions. If either pump stops spinning, fluid flow halts immediately.
- **Direction & Speed**: Respects Create's pump directions and RPM—spinning your pumps faster moves fluid faster across the portal.
- **Zero Loss & No Voiding**: Completely fixed fluid vanishing or getting voided; fluid reliably travels from your source tank, through the portal, and directly into your destination tanks or pipe network.
- Full support for water, lava, and all modded fluids.
- Fluid flow animations and particles now render smoothly through the portal.

### Item Drops & Wrench Dismantling
- **Fixed Missing Drops When Mining**: Breaking a portal shaft, cogwheel, or mechanical pump with a pickaxe (or by hand) now always drops the item right at your feet, regardless of which dimension you are standing in.
- **Wrench Duplication Fix**: Sneak-right-clicking with Create's Wrench now cleanly returns the block straight into your inventory without dropping an extra duplicate item on the other side of the portal.
- **Fair Item Return**: Whether using a pickaxe or a wrench, you will always get exactly 1 item back with zero item duplication and zero item loss.

### Visual & Rendering Fixes
- Fixed Flywheel rendering on NeoForge 1.21.1 so shaft and cogwheel half-stubs align seamlessly with portal frames, matching 1.20.1 visual fidelity.
- Fixed pump-to-portal connection rendering and portal particle effects across dimensions.

---

## 3.0 — Portal Transfer Overhaul
**Multi-loader: NeoForge 1.21.1 · Forge 1.20.1 · Create 6**

### Multi-version project
- Restructured as a multi-project Gradle workspace (`common` / `neoforge-1.21.1` / `forge-1.20.1` / `fabric-1.20.1`), matching Create Easy Villagers Multi-dev
- Fabric 1.20.1 port with Create Fabric 6.0.8.1 (same portal feature set as Forge 1.20.1)

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
