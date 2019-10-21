# Changelog

## v0.10.0

- Added support for Snapdragon 855
- Improved `VisionView` lifecycle management
- Updated API of `VisionManager`

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
