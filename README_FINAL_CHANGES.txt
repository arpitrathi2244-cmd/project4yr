SafeRoad map changes

1. Database remains the persistent source for alerts.
2. Map visibility is independent of alertEnabled.
3. The map now also requests live OpenStreetMap data for a small visible viewport after zoom >= 9. This fixes the situation where the database contains only a couple of imported records.
4. A Clear button removes the clicked-location marker and resets the selected-location box.
5. Clicking the map reverse-geocodes the point with Nominatim and shows the place name plus latitude/longitude.
6. Public Overpass is intentionally NOT queried for the whole of India in one request. That is too large and commonly times out. To get persistent national coverage, use the regional bbox importer in the backend and run the included PowerShell grid sync script.

Important: existing database records are not deleted. The two schools visible before were simply the two SCHOOL records currently present in the database. Live OSM viewport loading can show additional mapped schools/hills/wildlife when you zoom into a region.
