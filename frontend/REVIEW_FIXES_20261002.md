# SafeRoad frontend review/fixes — 2026-10-02

- Direction route rendering is now immediate after the first OSRM response.
- More practical alternatives are discovered in the background.
- School/Hill/Wildlife remain geofence alert data only.
- New destinations and clears invalidate stale background route calculations.
- AI is not allowed to recommend a route using fabricated "Clear/Low traffic" defaults when live services are unavailable.
- Voice alerts keep the existing browser speech approach and are unlocked after the first user interaction.
- No duplicate HTML IDs were found.
