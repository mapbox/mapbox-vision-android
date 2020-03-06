# Mapbox Vision SDK for Android

The Vision SDK uses highly efficient neural networks to process imagery directly on user’s
mobile or embedded devices, turning any connected camera into a second set of eyes
for your car. In doing so, the Vision SDK enables the following user-facing features:

- Augmented reality navigation with turn-by-turn directions
- Classification and display of regulatory and warning signs
- Object detection for vehicles, pedestrians, road signs, and traffic lights
- Semantic segmentation of the roadway into 14 different classes (other, road, road_markup, flat_non_road, sky, building, car, cycle, person, road_markings_other, curb, double_yellow, traffic_sign, traffic_light)
- Distance detection that indicates spacing to lead vehicle

#### Hardware requirements

VisionSDK requires Android 6 (API 23) and higher, with QC Snapdragon 650 // 710 // 8xx with Open CL support

Some of devices that will work with VisionSDK:
- Samsung Galaxy S8, S8+ // S9, S9+ // Note 8
- Xiaomi Mi 6 // 8
- HTC U11, U11+ // U12, U12+
- OnePlus 5 // 6

You can also check more details at [Vision SDK FAQ](https://vision.mapbox.com/#faq).

## Installation and setup

All examples are located in module `Examples`. 

Main steps for setup:
1. Sign up or log in to your Mapbox account and grab a [Mapbox access token](https://www.mapbox.com/help/define-access-token/)
1. Set your Mapbox token as environmental variable `MAPBOX_ACCESS_TOKEN`
1. Set your [Maven credentials](https://vision.mapbox.com/install/)

For detailed information follow [installation instructions](https://vision.mapbox.com/install/) to install Vision and 
check [the rest of documentation](https://docs.mapbox.com/android/vision/overview/).
