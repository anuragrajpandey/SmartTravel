# FlipDoor

FlipDoor is a native Android travel companion built with Kotlin and Android Views/Material Components.

## City discovery
- Search any city to load a live destination guide.
- Current weather plus a 15-day weather calendar: seven previous days, today and seven upcoming days.
- Nearby stays, restaurants, cafés, museums and attractions from OpenStreetMap.
- City overview and food research links through Wikimedia.
- Indicative daily budget tiers, clearly labelled as planning estimates rather than verified local averages.

## Weather calendar
The Open-Meteo Forecast API is requested with `past_days=7` and `forecast_days=8`, giving seven previous calendar days plus today and seven days ahead. Past values from this endpoint are archived model forecasts/estimates, not guaranteed observations from a local weather station. Forecast values can change.

## Trip planner
- Create a named trip and destination.
- Select start and end dates with native date pickers.
- Enter traveller count, accommodation per night, food per person/day, transport and activities.
- Calculate trip total, per-traveller estimate and duration.
- Save multiple trip plans locally on the device. No account or cloud sync is currently provided.

## Other features
- Explore, Stay, Discover, Tools and Trips navigation.
- Quick budget calculator.
- Google ML Kit translation with downloadable language models for offline use after download.
- Mock map preview; live map tiles, GPS and routing are not connected.
- Launcher icon generated from `assets/FlipDoor.png`.

## Data providers and limitations
- Weather and geocoding: [Open-Meteo](https://open-meteo.com/).
- Places: © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright), ODbL. Listings depend on map coverage; prices, ratings and availability are not verified.
- City and food research: [Wikipedia/Wikimedia](https://www.mediawiki.org/wiki/Wikimedia_REST_API). Results are references, not popularity rankings.
- Public Overpass services can be busy. A production launch should use a managed provider or controlled backend/cache.
- Live hotel inventory and reservations, transport tickets, payments, user accounts, cloud sync and turn-by-turn navigation are not implemented.
- Check provider terms before commercial use. Open-Meteo's free access is intended for non-commercial use.

## Build
GitHub Actions → **Build FlipDoor APK** → Run workflow → select `main`.
The debug APK is uploaded as `FlipDoor-debug-apk`.

## Stack
Kotlin · Android SDK 35 · AndroidX · Material Components · Google ML Kit · Open-Meteo · OpenStreetMap Overpass · Wikimedia REST API · Gradle · GitHub Actions

Package: `com.anuragrajpandey.smarttravel`
