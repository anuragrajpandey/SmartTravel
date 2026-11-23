# FlipDoor

FlipDoor is a native Android travel companion built with Kotlin and Android Views/Material Components.

## Live city guide

Search a city from the Explore screen to open the live City Guide. The guide loads:
- City matching and coordinates through Open-Meteo Geocoding.
- Current weather and a five-day forecast through Open-Meteo.
- Nearby mapped stays, restaurants, cafés, museums and attractions through the OpenStreetMap Overpass API.
- City overview and food research links through the Wikimedia REST search API.
- Indicative daily travel-planning ranges. These are broad planning assumptions, not live prices or a verified city-specific average.

The City Guide displays a circular loading spinner while the data is fetched. Individual data sources fail independently where possible, so a weather or places outage does not necessarily hide the rest of the guide.

## Existing features
- Booking-style blue-led interface with colourful category cards.
- Explore, Stay, Discover, Tools and Trips sections.
- Local trip budget calculator and saved destinations.
- Google ML Kit translation with downloadable language models for offline use after download.
- Weather tool and mock map preview.
- Android launcher icon generated from `assets/FlipDoor.png`.

## Data attribution and limitations
- Weather: [Open-Meteo](https://open-meteo.com/), CC BY 4.0. Free API access is intended for non-commercial use; commercial use requires an appropriate plan.
- Places: © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright), ODbL. Listings depend on community map coverage; availability, ratings and prices are not verified.
- Destination and food research: [Wikipedia/Wikimedia](https://www.mediawiki.org/wiki/Wikimedia_REST_API). Search results are references, not popularity rankings.
- The public Overpass service can be busy or temporarily unavailable. A production launch should use a managed provider or a controlled backend/cache and support provider switching.
- The app does not yet have hotel inventory/booking, ticketing, payments, user accounts, live navigation or a city-specific verified cost-of-living feed.

## Build
GitHub Actions → **Build FlipDoor APK** → Run workflow → select `main`.
The debug APK is uploaded as `FlipDoor-debug-apk`.

## Stack
Kotlin · Android SDK 35 · AndroidX · Material Components · Google ML Kit · Open-Meteo · OpenStreetMap Overpass · Wikimedia REST API · Gradle · GitHub Actions

Package: `com.anuragrajpandey.smarttravel`
