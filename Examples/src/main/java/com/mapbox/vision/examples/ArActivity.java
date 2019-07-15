package com.mapbox.vision.examples;

import android.location.Location;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineCallback;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.location.LocationEngineRequest;
import com.mapbox.android.core.location.LocationEngineResult;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.api.directions.v5.models.LegStep;
import com.mapbox.api.directions.v5.models.RouteLeg;
import com.mapbox.api.directions.v5.models.StepIntersection;
import com.mapbox.geojson.Point;
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation;
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigationOptions;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;
import com.mapbox.services.android.navigation.v5.offroute.OffRouteListener;
import com.mapbox.services.android.navigation.v5.route.RouteFetcher;
import com.mapbox.services.android.navigation.v5.route.RouteListener;
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress;
import com.mapbox.vision.VisionManager;
import com.mapbox.vision.ar.VisionArManager;
import com.mapbox.vision.ar.core.models.Route;
import com.mapbox.vision.ar.core.models.RoutePoint;
import com.mapbox.vision.ar.view.gl.VisionArView;
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener;
import com.mapbox.vision.mobile.core.models.AuthorizationStatus;
import com.mapbox.vision.mobile.core.models.Camera;
import com.mapbox.vision.mobile.core.models.Country;
import com.mapbox.vision.mobile.core.models.FrameSegmentation;
import com.mapbox.vision.mobile.core.models.classification.FrameSignClassifications;
import com.mapbox.vision.mobile.core.models.detection.FrameDetections;
import com.mapbox.vision.mobile.core.models.position.GeoCoordinate;
import com.mapbox.vision.mobile.core.models.position.VehicleState;
import com.mapbox.vision.mobile.core.models.road.RoadDescription;
import com.mapbox.vision.mobile.core.models.world.WorldDescription;
import com.mapbox.vision.performance.ModelPerformance.On;
import com.mapbox.vision.performance.ModelPerformanceConfig.Merged;
import com.mapbox.vision.performance.ModelPerformanceMode;
import com.mapbox.vision.performance.ModelPerformanceRate;
import com.mapbox.vision.utils.VisionLogger;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Example shows how Vision and VisionAR SDKs are used to draw AR lane over the video stream from camera.
 * Also, Mapbox navigation services are used to build route and  navigation session.
 */
public class ArActivity extends AppCompatActivity implements RouteListener, ProgressChangeListener, OffRouteListener {

    private static final String TAG = ArActivity.class.getSimpleName();

    // Handles navigation.
    private MapboxNavigation mapboxNavigation;
    // Fetches route from points.
    private RouteFetcher routeFetcher;
    private RouteProgress lastRouteProgress;
    private DirectionsRoute directionsRoute;
    private LocationEngine arLocationEngine;
    private LocationEngineRequest arLocationEngineRequest;
    private LocationEngineCallback<LocationEngineResult> locationCallback;

    // This dummy points will be used to build route. For real world test this needs to be changed to real values for
    // source and target locations.
    private final Point ROUTE_ORIGIN = Point.fromLngLat(27.654285, 53.928057);
    private final Point ROUTE_DESTINATION = Point.fromLngLat(27.655637, 53.935712);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_ar_navigation);

        // Initialize navigation with your Mapbox access token.
        mapboxNavigation = new MapboxNavigation(
                this,
                getString(R.string.mapbox_access_token),
                MapboxNavigationOptions.builder().build()
        );

        // Initialize route fetcher with your Mapbox access token.
        routeFetcher = new RouteFetcher(this, getString(R.string.mapbox_access_token));
        routeFetcher.addRouteListener(this);

        arLocationEngine = LocationEngineProvider.getBestLocationEngine(this);
        arLocationEngineRequest = new LocationEngineRequest.Builder(0)
                .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                .setFastestInterval(1000)
                .build();
        locationCallback = new LocationEngineCallback<LocationEngineResult>() {
            @Override
            public void onSuccess(LocationEngineResult result) {}

            @Override
            public void onFailure(@NonNull Exception exception) {}
        };

    }

    @Override
    protected void onResume() {
        super.onResume();

        try {
            arLocationEngine.requestLocationUpdates(arLocationEngineRequest, locationCallback, getMainLooper());
        } catch (SecurityException se) {
            VisionLogger.Companion.e(TAG, se.toString());
        }

        initDirectionsRoute();

        // Route need to be reestablished if off route happens.
        mapboxNavigation.addOffRouteListener(this);
        mapboxNavigation.addProgressChangeListener(this);

        // Create and start VisionManager.
        VisionManager.create();
        VisionManager.setModelPerformanceConfig(new Merged(new On(ModelPerformanceMode.FIXED, ModelPerformanceRate.LOW)));
        VisionManager.start(
                new VisionEventsListener() {

                    @Override
                    public void onAuthorizationStatusUpdated(@NotNull AuthorizationStatus authorizationStatus) {}

                    @Override
                    public void onFrameSegmentationUpdated(@NotNull FrameSegmentation frameSegmentation) {}

                    @Override
                    public void onFrameDetectionsUpdated(@NotNull FrameDetections frameDetections) {}

                    @Override
                    public void onFrameSignClassificationsUpdated(@NotNull FrameSignClassifications frameSignClassifications) {}

                    @Override
                    public void onRoadDescriptionUpdated(@NotNull RoadDescription roadDescription) {}

                    @Override
                    public void onWorldDescriptionUpdated(@NotNull WorldDescription worldDescription) {}

                    @Override
                    public void onVehicleStateUpdated(@NotNull VehicleState vehicleState) {}

                    @Override
                    public void onCameraUpdated(@NotNull Camera camera) {}

                    @Override
                    public void onCountryUpdated(@NotNull Country country) {}

                    @Override
                    public void onUpdateCompleted() {}
                }
        );

        VisionArView visionArView = findViewById(R.id.mapbox_ar_view);
        VisionManager.setVideoSourceListener(visionArView);

        // Create VisionArManager.
        VisionArManager.create(VisionManager.INSTANCE, visionArView);
    }

    private void initDirectionsRoute() {
        // Get route from predefined points.
        NavigationRoute.builder(this)
                .accessToken(getString(R.string.mapbox_access_token))
                .origin(ROUTE_ORIGIN)
                .destination(ROUTE_DESTINATION)
                .build()
                .getRoute(new Callback<DirectionsResponse>() {
                    @Override
                    public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                        if (response.body() == null || response.body().routes().isEmpty()) {
                            return;
                        }

                        // Start navigation session with retrieved route.
                        DirectionsRoute route = response.body().routes().get(0);
                        mapboxNavigation.startNavigation(route);

                        // Set route progress.
                        VisionArManager.setRoute(new Route(
                                getRoutePoints(route),
                                directionsRoute.duration().floatValue(),
                                "",
                                ""
                        ));
                    }

                    @Override
                    public void onFailure(Call<DirectionsResponse> call, Throwable t) {}
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        VisionArManager.destroy();
        VisionManager.stop();
        VisionManager.destroy();

        arLocationEngine.removeLocationUpdates(locationCallback);

        mapboxNavigation.removeProgressChangeListener(this);
        mapboxNavigation.removeOffRouteListener(this);
        mapboxNavigation.stopNavigation();
    }

    @Override
    public void onErrorReceived(Throwable throwable) {
        if (throwable != null) {
            throwable.printStackTrace();
        }

        mapboxNavigation.stopNavigation();
        Toast.makeText(this, "Can not calculate the route requested", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onResponseReceived(@NotNull DirectionsResponse response, RouteProgress routeProgress) {
        mapboxNavigation.stopNavigation();
        if (response.routes().isEmpty()) {
            Toast.makeText(this, "Can not calculate the route requested", Toast.LENGTH_SHORT).show();
        } else {
            DirectionsRoute route = response.routes().get(0);

            mapboxNavigation.startNavigation(route);

            // Set route progress.
            VisionArManager.setRoute(new Route(
                    getRoutePoints(route),
                    (float) routeProgress.durationRemaining(),
                    "",
                    ""
            ));
        }
    }

    @Override
    public void onProgressChange(Location location, RouteProgress routeProgress) {
        lastRouteProgress = routeProgress;
    }

    @Override
    public void userOffRoute(Location location) {
        routeFetcher.findRouteFromRouteProgress(location, lastRouteProgress);
    }

    private RoutePoint[] getRoutePoints(@NotNull DirectionsRoute route) {
        ArrayList<RoutePoint> routePoints = new ArrayList<>();

        List<RouteLeg> legs = route.legs();
        if (legs != null) {
            for (RouteLeg leg : legs) {

                List<LegStep> steps = leg.steps();
                if (steps != null) {
                    for (LegStep step : steps) {
                        RoutePoint point = new RoutePoint((new GeoCoordinate(
                                step.maneuver().location().latitude(),
                                step.maneuver().location().longitude()
                        )));

                        routePoints.add(point);

                        List<StepIntersection> intersections = step.intersections();
                        if (intersections != null) {
                            for (StepIntersection intersection : intersections) {
                                point = new RoutePoint((new GeoCoordinate(
                                        step.maneuver().location().latitude(),
                                        step.maneuver().location().longitude()
                                )));

                                routePoints.add(point);
                            }
                        }
                    }
                }
            }
        }

        return routePoints.toArray(new RoutePoint[0]);
    }
}
