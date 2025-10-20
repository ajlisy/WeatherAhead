# WeatherAhead

A Hubitat driver powered by OpenWeatherMap One Call 3.0:
- WeatherAhead 8hr Precipitation Driver

Install via Hubitat Package Manager (HPM) using the package manifest in this repo, or copy the Groovy driver into Hubitat Drivers Code manually.

Configuration:
- OpenWeatherMap API Key (required)
- Latitude, Longitude (optional; default to hub location)
- Units: imperial or metric (optional; default from hub temp scale)
- Poll interval minutes (min 5)

Notes:
- Update `packageManifest.json` location to your raw Git URL if you fork.
- Uses One Call 3.0 endpoint and excludes unused blocks to reduce payload.
