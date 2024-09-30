# Changelog

## v0.13.0

### Vision
- Add video recording for custom `VideoSource` implementation
- Update Vision, Vision AR and Vision Safety to Android SDK 10
- Add all required permissions to Vision SDK manifest
- Change default resolution of Camera2VideoSourceImpl to 960*540
- Add optional parameter to Camera2VideoSourceImpl constructor to enable\disable autofocus
- Add support of reverse landscape orientation
- Integrated new rendering engine for VisionView and VisionArView with better performance
- Add `VisionManager.setCameraHeight`
- Add `aspectRatio`, `roll`, `pitch`, `yaw`, `height` properties to `Camera`
- Improve lane detection
- Stop sending some inaccurate events until the camera is calibrated
- Introduce automatic camera recalibration
- Expand Japan region to include Okinawa
- Fix bug with speed estimation when a vehicle is stopped
- Fix bug that prevented new China users authorization
- Remove deprecated code for 0.12.0:
	- `VisionManager.setModelPerformanceConfig`
	- `VisionReplayManager.setModelPerformanceConfig`
	- `SystemInfoUtils.getSnpeSupportedBoard`
	- `SystemInfoUtils.getSystemProperty`
	- `enum class SupportedSnapdragonBoards`
	- `class ModelPerformanceConfig`

### AR

- Deprecate `ARCamera` class in favor of utilization of `Camera` class

## v0.12.0

### Device support
- Added support for non-Snapdragon powered devices. Most chips on the market are supported now, 
including Exynos by Samsung, Kirin by Huawei, Mediatek, etc.

### Vision
- Added `Japan` country and support for the detection of Japanese traffic signs
- Added new `SignType`s:
  - `InformationRestrictedParking`
  - `RegulatorySchoolZone`
  - `RegulatoryBicyclesAndPedestriansCrossing`
  - `RegulatoryNoBusesAndHeavyVehicles`
- Added `setProgress`/`getProgress` to VisionReplayManager to control session playback progress
- Added `getDuration` to VisionReplayManager
- Changed VisionReplayManager's behaviour:
  - method start continues session replay from the current progress
  - method stop stops session replay without changing the progress
- Deprecated separate detection and segmentation models configuration. 
Use `setModelPerformance` instead of `setModelPerformanceConfig` to customize performance of the SDK. 
- Improved camera calibration algorithm speed
- Improved lanes detection algorithm
- Utilized new ML models that reduce resource consumption

### AR
- Added `VisionArEventsListener.onRouteUpdated` callback

## v0.11.0

### Vision
- Added `Germany` country
- Added new `SignTypes`:
`InformationCarWashing`, `InformationBusStop`, `RegulatoryPedestriansCrossingUp`,
`RegulatoryPedestriansCrossingDown`, `InformationAutoService`, `InformationFood`,
`InformationTown`, `InformationTownEnd`, `RegulatoryControl`,
`RegulatoryDoubleUTurn`, `SpeedLimitZone`, `SpeedLimitEndZone`
- Added an ability to work with image data as direct `ByteBuffer`'s
- Added an ability to copy `Image` pixel data to `ByteArray`/`ByteBuffer`
- Added proguard consumer config to allow obfuscation on client side
- Added `armeabi-v7a` ABI to abiFilters to build older architecture
- Fixed a crash happening on `VisionManager.destroy`

### Ar
- Added new `Fence` AR style
- Added `FenceVisualParams` class and `VisionArView.setFenceVisualParams` method for customization of `Fence` rendering
- Added `VisionArView.setArQuality` method to set overall quality of AR objects
- Added `VisionArView.setFenceVisible`/`VisionArView.isFenceVisible`/
`VisionArView.setLaneVisible`/`VisionArView.isLaneVisible` to manage displayed AR features
- Added `VisionArView.onPause` method, that should be called when view is
hidden or detached

## v0.10.1

- Fixed bug with session not being recorded

## v0.10.0

- Added support for Snapdragon 855
- `VisionView` renders now with OpenGL ES
- Changed public API of `VisionView`
- Changed `VisionView` lifecycle if `VisionManager` is set
- Change internal camera lifecycle
- Fixed memory leak on `VisionManager.destroy()`
- Fixed gpu memory leak
- Fixed issues with destroying VisionManager
- Renamed `VisualizationMode.Detections` to `VisualizationMode.Detection`
- Added detection of construction cones
- Improved quality of detection/segmentation, especially at night
- Improved segmentation, now it's more focused on road specific elements. New segmentation model recognizes the following classes: Crosswalk, Hood, MarkupDashed, MarkupDouble, MarkupOther, MarkupSolid, Other, Road, RoadEdge, Sidewalk

## v0.9.0

### Vision
- Added to `VisionManager` method `start()`, property `visionEventsListener`. `visionEventsListener` is held as a weak reference.
- Added to `VisionReplayManager` method `start()`, property `visionEventsListener`. `visionEventsListener` is held as a weak reference.
- Deprecated `VisionManager` method `start(VisionEventsListener)`
- Deprecated `VisionReplayManager` method `start(VisionEventsListener)`
- Added new `SignType`s: `RegulatoryKeepLeftPicture`, `RegulatoryKeepLeftText`,`AheadSpeedLimit`,`WarningSpeedLimit`,`RegulatoryNoUTurnRight`,`WarningTurnRightOnlyArrow`

### Ar
- Added to `VisionArManager` method `create(BaseVisionManager)`, property `visionArEventsListener`. `visionArEventsListener` is held as a weak reference.
- Deprecated `VisionArManager` method `create(BaseVisionManager, VisionArEventsListener)`
- Changed `Ar Lane` API, `VisionArView`: `setArManager(VisionArManager)`
- Removed from `VisionArView` following methods: `onArCameraUpdated`, `onArLaneUpdated`, `onNewFrame`, `onNewCameraParameters`
- Changed `VisionArView` doesn't implement `VideoSourceListener` and `VisionArEventsListener`
- Changed `Ar Lane` appearance
- Moved `Ar` rendering to native

## Safety
- Added to `VisionSafetyManager` method `create(BaseVisionManager)`, property `visionSafetyListener`. `visionSafetyListener` is held as a weak reference.
- Deprecated `VisionSafetyManager` method `create(BaseVisionManager, VisionSafetyListener)`

## v0.8.1

### Vision
- Fixed crash on `VisionManager.create()` with custom `VideoSource`

## v0.8.0

### Vision
- Start monitoring performance related device info

## v0.7.1

### Vision
- Fixed ModelPerformanceMode.DYNAMIC not taking effect with ModelPerformanceConfig.Merged
- Fixed detections of sign objects

## v0.7.0

### Vision
- Improved lane detection
- Fixed swapped top and bottom coordinates in `Detection.boundingBox`
- `FrameStatistics` renaming `FPS` -> `Fps`
- Improve performance of `VisionReplayManager` 

## v0.6.0

### Vision
- Added `currentLaneCenter`, `currentLaneWidth` to `RoadDescription` 
- Renamed `currentLanePosition` to `relativePositionInLane` in `RoadDescription`

## v0.5.0

### Vision
- Added support for UK country
- Added method `Lane.contains(worldCoordinate: WorldCoordinate)`
- Added methods `WorldDescription.getObjectsInLane(lane: Lane)` and `getObjectsOfClass(detectionClass: DetectionClass)`
- Changed implementation of lane detector: it has better quality and improved energy efficiency. Only one ego lane is detected right now
- Changed World-Pixel transformation methods to return optional values
- Changed World-Geo transformation methods to return optional values
- Fixed session recording bug when camera parameters where not recorded. 

## v0.4.2

- Fixed wrong objects' location send to server

## v0.4.1

### Vision
- Changed behaviour on simultaneous `VisionManager`
 and `VisionReplayManager` instances creation to throwing an exception
- Fixed wrong `CameraParams` in replay mode (reason for incorrect AR lane display)
- Fixed possible crash on `VisionSafetyManager`/`VisionArManager` `create`/`destroy`

## v0.4.0

### Vision
- Added `startRecording` and `stopRecording` methods on `VisionManager` to record sessions.
- Added `VisionReplayManager` class for replaying recorded sessions.
- `Detection.boundingBox` now stores normalized relative coordinates.

### VisionAr
- Added `VisionArManager.setLaneLength` method to customize the length of `ArLane`.
