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

## Installation and setup

The Vision SDK is currently in limited public beta. Contact our team to [request access](https://vision.mapbox.com/#application) and receive installation instructions.
