# Mapbox Vision SDK for Android

The Vision SDK uses highly efficient neural networks to process imagery directly on user’s
mobile or embedded devices, turning any connected camera into a second set of eyes
for your car. In doing so, the Vision SDK enables the following user-facing features:

- Augmented reality navigation with turn-by-turn directions
- Classification and display of regulatory and warning signs
- Object detection for vehicles, pedestrians, road signs, and traffic lights
- Semantic segmentation of the roadway into 14 different classes (other, road, road_markup, flat_non_road, sky, building, car, cycle, person, road_markings_other, curb, double_yellow, traffic_sign, traffic_light)
- Distance detection that indicates spacing to lead vehicle

#### Components of the Vision SDK
There are three components to the Vision SDK: VisionCore, VisionSDK, and VisionAR.

VisionCore is the core logic of the system, including all machine learning models; it exists as compiled library for each platform with a user-facing API.

[VisionSDK](https://github.com/mapbox/mapbox-vision-android) is a framework written in native language (Kotlin for Android, Swift for iOS) that encapsulates core utilization and platform-dependent tasks. It calls VisionCore.

[VisionAR](https://github.com/mapbox/mapbox-vision-ar-android) is a native framework with dependency on the Mapbox Navigation SDK. It takes information from the specified navigation route, transfers it to VisionCore via VisionSDK, receives instructions on displaying the route, and then finally renders it on top of camera frame using native instruments.

#### Hardware requirements

VisionSDK requires Android 6 (API 23) and higher, with QC Snapdragon 650 // 710 // 8xx with Open CL support

Some of devices that will work with VisionSDK:
- Samsung Galaxy S8, S8+ // S9, S9+ // Note 8
- Xiaomi Mi 6 // 8
- HTC U11, U11+ // U12, U12+
- OnePlus 5 // 6

You can also check more details at [Vison SDK FAQ](https://vision.mapbox.com/faq).

## Setup

You can look for an example of complete integration in the [Mapbox Vision SDK Teaser repo](https://github.com/mapbox/mapbox-vision-android-examples).

1. Add the dependencies:

Add the following dependency to your project's `build.gradle`:

```
implementation 'com.mapbox.vision:mapbox-android-vision:0.1.0'
```

and to your top-level `build.gradle`:

```
repositories {
     maven { url 'https://mapbox.bintray.com/mapbox' }
}
```

2. Set your [Mapbox access token](https://www.mapbox.com/help/how-access-tokens-work/):

```
class DemoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        VisionManager.init(this, "<access token>")
    }
}
```

3. Setup permissions:

Mapbox Vision SDK will require the following list of permissions to work:

```
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

You should grant them all before calling the SDK.

4. Add VisionView to the activity layout:

VisionView will render the image that was produced by Vision SDK.

You can add it with the following snippet:

```
    <com.mapbox.vision.view.VisionView
        android:id="@+id/vision_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:visualization_mode="detection" />
```

`app:visualization_mode` specifies what will be rendered on VisionView - clear video source, segmentations or detections.

5. Lifecycle methods:

You will need to call the following lifecycle methods of `VisionManager` from the activity or fragment containing VisionView:

```
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    ...
    VisionManager.create()
}

override fun onResume() {
    super.onResume()
    ...
    VisionManager.start()
}

override fun onPause() {
    super.onPause()
    ...
    VisionManager.stop()
}

override fun onDestroy() {
    super.onDestroy()
    ...
    VisionManager.destroy()
}
```
