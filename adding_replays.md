# Adding replays to the Android app
## 1. Adding files to the project

* Place the json files into the `/wehe/src/main/assets` folder

## 2. Adding replay to the app

* If the app does not have an icon uploaded, add the image to `wehe/src/main/res/drawable`
* Open `/wehe/src/main/assets/apps_list.json` and either add a new entry or edit an existing one
* Do the same with `/wehe/src/main/assets/random_list.json`

Replay format:
```json
    {
      "name" : "Spotify", 
      "configfile" : "", // always empty
      "datafile" : "Spotify.pcap_client_all.json", // Open replay for apps_list, random fro random_list
      "size" : 16.4,
      "time" : 6.1,
      "image" : "spotify",
      "type" : "combined" // always combined
    }
```


* Add two entries to `/wehe/src/main/res/values/strings.xml`. The value of the name field is the same as in your json entry. The contents of the XML represent the name that will be shown to the user

```xml
<string name="Spotify">Spotify</string>
<string name="Spotify_random">Spotify (random)</string>
```

## 3. Releasing an update

* Open `/wehe/build.gradle` and bump the `versionName` value
* Go to `build` > `Generate Signed APK..`
* Go through and build the APK
* The new APK will be placed in `/wehe/release/wehe-release.apk`
* Go to the [Google Play Console](https://play.google.com/apps/publish) page to publish an update