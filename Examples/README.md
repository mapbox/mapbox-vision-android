## ArManeuverActivityKt

Activity was create to provide developer experience and sample to create custom ***AR*** objects. 

Activity can work in 2 modes:
- Default (based on [***VisionManager***](https://docs.mapbox.com/android/api/vision/vision/0.4.0/com/mapbox/vision/VisionManager.html))
- Replaying (based on
  [***VisionReplayManager***](https://docs.mapbox.com/android/api/vision/vision/0.4.0/com/mapbox/vision/VisionReplayManager.html))
  
Notes:
- *isReplaying* flag defines in which mode Activity works
- *replayPath* is a part of recorded telemetry(make sure it has right path otherwise application will crash). Should be
  filled when *isReplaying=true*
- *ROUTE_ORIGIN* and *ROUTE_DESTINATION* are dummy point that should be filled by real coordinates.
- *FakeLocationEngine* is mocked LocationEngine for Replaying mode
  
## CustomArGlRender

Custom Renderer very similar to *GlRenderer* 

## ManeuverPoints

Contains 2 shaders that have responsibility to draw custom step-line.