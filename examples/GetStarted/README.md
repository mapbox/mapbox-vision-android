## Vision SDK Get Started example
This project contains basic logic to set up and rum [Vision SDK](https://www.mapbox.com/android-docs/vision/overview/). Also it shows different working modes of [VisionView](https://github.com/mapbox/mapbox-vision-android/blob/dev/MapboxVision/src/main/java/com/mapbox/vision/view/VisionView.kt) class.

![MacDown Screenshot](images/get_started.gif)

### Running locally

With the first Gradle invocation, Gradle will take the value of the `MAPBOX_ACCESS_TOKEN` environment variable and save it to `/src/main/res/values/developer-config.xml`. If the environment variable wasn't set, you can create/edit the `developer-config.xml` file. Create an access_token String resource and paste your access token into it:

```xml
<string name="access_token">PASTE_YOUR_TOKEN_HERE</string>
```