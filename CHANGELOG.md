# Changelog

## v0.4.2

- Fixed wrong objects' location send to server

## v0.4.1

### Vision
- Changed behaviour on simultaneous `VisionManager`
 and `VisionReplayManager` instances creation to throwing an exception
- Fixed wrong `CameraParams` in replay mode (reason for incorrect AR lane display)
- Fixed possible crash on `VisionSafetyManager`/`VisionArManager` _create_/_destroy_

## v0.4.0

### Vision
- Added `startRecording` and `stopRecording` methods on `VisionManager` to record sessions.
- Added `VisionReplayManager` class for replaying recorded sessions.
- `Detection.boundingBox` now stores normalized relative coordinates.

### VisionAr
- Added `VisionArManager.setLaneLength` method to customize the length of `ArLane`.
