# CuboidDroid's Support Mod

CuboidDroid's support mod for the Cuboid Outpost modpack.

This mod is currently not meant to be used directly and is 
currently heavily biased towards the needs of the Cuboid Outpost
modpack.

If there is enough interest, this may change in future to allow 
for wider usage in other modpacks too (by adding things like more
configuration options, etc.)

For now though, it's best to just consider this as a requirement 
mod for Cuboid Outpost, which you should can find on CurseForge
here:

[Cuboid Outpost Modpack!](https://www.curseforge.com/minecraft/modpacks/cuboid-outpost)

---

## About This Fork

This fork was created to fix issues CuboidDroid/cuboidmod#15 and CuboidDroid/cuboidmod#17 in the 0.3.3 version of the official mod.

This fixes two bugs:
1. Recipes for a number of singularities in the Singularity Resource Generator were not working.
2. SRG recipes added by JSON/KubeJS crashed the game.

These fixes should be implemented in a way that this version can be a drop-in replacement for the version shipped with the pack. This, along with the other fixes in v0.3.3 should allow the pack to work in multiplayer, as long as the versions on both server and clients are updated. This is still untested, however.

For details, see the diffs on the relevant commit -- I've added comments in the code explaining the issues.

---

### What versions will be supported?

<table>
	<tr>
		<td></td>
		<th>1.16</th>
		<th>< 1.20</th>
		<th>1.20</th>
		<th>> 1.20</th>
	<tr>
	<tr>
		<th>Vulnerabilities?</th>
		<td>❔</td>
		<td>❔</td>
		<td>✅</td>
		<td>❔</td>
	<tr>
	<tr>
		<th>Fixes?</th>
		<td>❔</td>
		<td>❌</td>
		<td>✅</td>
		<td>❔</td>
	<tr>
	<tr>
		<th>Updates?</th>
		<td>❌</td>
		<td>❌</td>
		<td>✅</td>
		<td>❔</td>
	<tr>
</table>

`❌ - Denied`
`❔ - Possible`
`✅ - Planned`
