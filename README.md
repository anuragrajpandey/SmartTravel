# FlipDoor

FlipDoor is an Android travel companion for discovering destinations, exploring stays and food, planning budgets, translating languages and checking weather.

## Features
- Bright, polished travel-booking inspired interface with a blue-led palette and colourful category cards.
- Explore, Stay, Discover, Tools and Trips sections with animated screen transitions.
- Destination ideas, regional food guides, stay categories and hidden-place suggestions.
- On-device trip budget estimator and locally saved destinations.
- Google ML Kit translation with downloadable language models for offline use after download.
- Live weather and a 3-day forecast using Open-Meteo.
- Map preview is a mock interface. Live maps, GPS and directions are not connected.
- Android launcher icon is generated from `assets/FlipDoor.png` during the build.

## Build
GitHub Actions → Build FlipDoor APK → Run workflow. The workflow is manual only.
Select the `main` branch. The debug APK is uploaded as the `FlipDoor-debug-apk` artifact.

## Stack
Kotlin · Android SDK 35 · AndroidX · Material Components · Google ML Kit · Open-Meteo

Package: `com.anuragrajpandey.smarttravel`
