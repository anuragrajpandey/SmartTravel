# FlipDoor

FlipDoor is a native Android travel companion built with Kotlin and Android Views/Material Components.

## Around you
- On launch FlipDoor asks for location permission and builds Explore around where you are: landmarks within ~10 km, nearby areas/towns with distance, hidden gems and the famous food of your state or region.
- Without permission the app still works: search any city, or tap **Allow location** on the home screen.
- Opening an area shows its places, weather, hotels, and the **famous food of that region/state** (curated dishes for every Indian state/UT, Wikipedia-discovered dishes elsewhere), each with photo and full details in-app.

## Hotels
- **Hotels** tab: real hotels, hostels, guest houses and resorts from OpenStreetMap near you (or any searched city), ranked by rating, details and distance. Sort by best match, price or distance.
- Pricing: OpenStreetMap rarely stores rates, so each card shows an **estimated** nightly range (by star rating and type, labelled "ESTIMATE") unless a price is listed. **Live price** opens today's actual rates for that hotel in an in-app browser.

## In-app maps and information
- Google Maps runs inside the app (place pins, nearby hotels/restaurants/attractions/hospitals, route from your location). No redirect to the Maps app.
- Place, dish and city information is shown in the app (full text sections) instead of sending you to Wikipedia. Websites and booking pages also open in an in-app browser.
- Optional: set `MAPS_EMBED_KEY=<your key>` in `gradle.properties` to use the official Google Maps Embed API; otherwise Google's keyless embed is used.

## Weather
A dedicated weather screen: sky gradient that follows conditions and day/night, large temperature, hourly strip, 7-day forecast and past-7-day views with temperature range bars, and detail tiles (feels like, humidity, wind, UV, visibility, pressure, sunrise/sunset, rain). Search any city or use your location.

## Trip planner
- Create a named trip and destination.
- Select start and end dates with native date pickers.
- Enter traveller count, accommodation per night, food per person/day, transport and activities.
- Calculate trip total, per-traveller estimate and duration.
- Save multiple trip plans locally on the device. No account or cloud sync is currently provided.

## Other features
- Explore, Hotels, Gems, Tools and Trips navigation with vector icons; edge-to-edge layout that respects the notch/camera cutout and system bars.
- Quick budget calculator.
- Google ML Kit translation with downloadable language models for offline use after download.
- Launcher icon generated from `assets/FlipDoor.png`.

## Data providers and limitations
- Weather and geocoding: [Open-Meteo](https://open-meteo.com/). Reverse geocoding: [Nominatim](https://nominatim.org/).
- Maps and live hotel prices: Google Maps / Google Travel pages displayed in an in-app WebView.
- Places: © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright), ODbL. Listings depend on map coverage; prices, ratings and availability are not verified.
- City and food research: [Wikipedia/Wikimedia](https://www.mediawiki.org/wiki/Wikimedia_REST_API). Results are references, not popularity rankings.
- Public Overpass services can be busy. A production launch should use a managed provider or controlled backend/cache.
- Hotel booking, transport tickets, payments, user accounts, cloud sync and turn-by-turn navigation are not implemented. Hotel price ranges are estimates unless a live price is opened.
- Check provider terms before commercial use. Open-Meteo's free access is intended for non-commercial use.

## Build
GitHub Actions → **Build FlipDoor APK** → Run workflow → select your branch (e.g. `update`).
The debug APK is uploaded as `FlipDoor-debug-apk`.

## Stack
Kotlin · Android SDK 35 · AndroidX · Material Components · Google ML Kit · Open-Meteo · OpenStreetMap Overpass · Wikimedia REST API · Gradle · GitHub Actions

Package: `com.anuragrajpandey.smarttravel`
