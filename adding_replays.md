# Adding replays to the Android app and releasing updates

## 1. Adding files to the project

* Place the json files into the `/wehe/src/main/assets` folder

## 2. Adding replay to the app

* If the app does not have an icon uploaded, add the image to `wehe/src/main/res/drawable`
* Icons for ports:
  * Created in MS Paint on 512 x 512 px canvas
  * Font: Arial
  * Font size: 135 for number, 60 for description
  * Port number and description both center aligned
  * Bottom of number is 216 px from the top of the canvas
  * Top of the description is 296 px from the top of the canvas
* Open `/wehe/src/main/assets/apps_list.json` and either add a new entry or edit an existing one
* `apps_list.json` contains apps in alphabetical order, followed by small port and large port replays

Replay format:
```json
    {
      "name": "Spotify", 
      "size": 8, //size of one replay, in megabytes (MB), should be an integer
      "time": 15, //time to run one replay, in seconds, should be an integer
      "image": "spotify", //image name, without extension
      "datafile": "Spotify.pcap_client_all.json", //original replay file name
      "randomdatafile": "SpotifyRandom_01042019.pcap_client_all.json", //random replay file name
      "category": <"VIDEO" | "MUSIC" | "CONFERENCING" | "SMALL_PORT" | "LARGE_PORT">, //depends on the tab the app/port is displayed on
      "englishOnly": true, //for apps that appear only in the English version
      "frenchOnly": true //for apps that appear only in the French version
    }
```

## 3. Releasing an update for internal testing

1. Open `/wehe/build.gradle` and bump the `versionCode` value by 1
2. Make sure the `versionName` value is the same as the one in the iOS app
3. Go to `Build` > `Generate Signed Bundle / APK...` > `Next`
4. Fill out the fields for `Key store path`, `Key store password`, `Key alias`, and `Key password` using the existing credentials
5. Uncheck the `Export encrypted key for ...` option, if one exists
6. Click `Next` > `release` > `Finish`. Do note the destination directory that is displayed at the top
7. The new APK will probably be placed in `/wehe/release/wehe-release.aab`
8. Go to the [Google Play Console](https://play.google.com/apps/publish) page to publish an update
9. Click on `Wehe` in the table
10. In the menu on the left, `Setup` > `Internal app sharing`
11. Click the link under the `Manage uploaders` section
12. Click `Upload`, and upload the `wehe-release.aab` file
13. The link can be copied by clicking the icon to the right of the current version
