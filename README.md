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
There are three components to the Vision SDK: Vision, VisionAr, and VisionSafety.

[Vision](https://github.com/mapbox/mapbox-vision-android/tree/dev/MapboxVision) is the primary SDK, needed for any application of Mapbox Vision. 
Its components enable camera configuration, display of classification, detection, and segmentation layers, lane feature extraction, and other interfaces. 
Vision accesses real-time inference running in VisionCore.

[Vision Ar](https://github.com/mapbox/mapbox-vision-android/tree/dev/MapboxVisionAR) is an add-on module for Vision used to create customizable augmented reality experiences. 
It allows configuration of the user’s route visualization: lane material (shaders, textures), lane geometry, occlusion, custom objects, and more. 

[Vision Safety](https://github.com/mapbox/mapbox-vision-android/tree/dev/MapboxVisionSafety) is an add-on module for Vision used to create customizable alerts for speeding, nearby vehicles, cyclists, pedestrians, lane departures, and more. 

#### Hardware requirements

VisionSDK requires Android 6 (API 23) and higher, with QC Snapdragon 650 // 710 // 8xx with Open CL support

Some of devices that will work with VisionSDK:
- Samsung Galaxy S8, S8+ // S9, S9+ // Note 8
- Xiaomi Mi 6 // 8
- HTC U11, U11+ // U12, U12+
- OnePlus 5 // 6

You can also check more details at [Vision SDK FAQ](https://vision.mapbox.com/#faq).

## Installation and setup

Follow [installation instructions](https://vision.mapbox.com/install/) to install Vision.
Check [the rest of documentation](https://docs.mapbox.com/android/vision/overview/).
