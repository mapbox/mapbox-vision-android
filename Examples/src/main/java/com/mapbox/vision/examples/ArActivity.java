package com.mapbox.vision.examples;

import android.annotation.SuppressLint;
import android.location.Location;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;
import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineListener;
import com.mapbox.android.core.location.LocationEnginePriority;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.api.directions.v5.models.*;
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
import com.mapbox.vision.mobile.core.models.classification.FrameSigns;
import com.mapbox.vision.mobile.core.models.detection.FrameDetections;
import com.mapbox.vision.mobile.core.models.position.GeoCoordinate;
import com.mapbox.vision.mobile.core.models.position.VehicleLocation;
import com.mapbox.vision.mobile.core.models.road.RoadDescription;
import com.mapbox.vision.mobile.core.models.world.WorldDescription;
import com.mapbox.vision.performance.ModelPerformance.On;
import com.mapbox.vision.performance.ModelPerformanceConfig.Merged;
import com.mapbox.vision.performance.ModelPerformanceMode;
import com.mapbox.vision.performance.ModelPerformanceRate;
import org.jetbrains.annotations.NotNull;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * Example shows how Vision and VisionAR SDKs are used to draw AR lane over the video stream from camera.
 * Also, Mapbox navigation services are used to build route and  navigation session.
 */
public class ArActivity extends AppCompatActivity implements LocationEngineListener, RouteListener, ProgressChangeListener, OffRouteListener {

    // Handles navigation.
    private MapboxNavigation mapboxNavigation;
    // Fetches route from points.
    private RouteFetcher routeFetcher;
    private RouteProgress lastRouteProgress;
    private DirectionsRoute directionsRoute;
    private LocationEngine arLocationEngine;

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
                MapboxNavigationOptions.builder().enableOffRouteDetection(true).build()
        );

        // Initialize route fetcher with your Mapbox access token.
        routeFetcher = new RouteFetcher(this, getString(R.string.mapbox_access_token));
        routeFetcher.addRouteListener(this);

        LocationEngineProvider provider = new LocationEngineProvider(this);
        arLocationEngine = provider.obtainBestLocationEngineAvailable();
        arLocationEngine.setPriority(LocationEnginePriority.HIGH_ACCURACY);
        arLocationEngine.setInterval(0);
        arLocationEngine.setFastestInterval(1000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        arLocationEngine.activate();
        arLocationEngine.addLocationEngineListener(this);

        initDirectionsRoute();

        // Route need to be reestablished if off route happens.
        mapboxNavigation.addOffRouteListener(this);
        mapboxNavigation.addProgressChangeListener(this);

        // Create and start VisionManager.
        VisionManager.create(new VisionEventsListener() {
            @Override
            public void onAuthorizationStatusChanged(@NotNull AuthorizationStatus authorizationStatus) {}

            @Override
            public void onSegmentationUpdated(@NotNull FrameSegmentation frameSegmentation) {}

            @Override
            public void onDetectionsUpdated(@NotNull FrameDetections frameDetections) {}

            @Override
            public void onSignsUpdated(@NotNull FrameSigns frameSigns) {}

            @Override
            public void onRoadUpdated(@NotNull RoadDescription roadDescription) {}

            @Override
            public void onWorldUpdated(@NotNull WorldDescription worldDescription) {}

            @Override
            public void onVehicleLocationUpdated(@NotNull VehicleLocation vehicleLocation) {}

            @Override
            public void onCameraUpdated(@NotNull Camera camera) {}

            @Override
            public void onCountryUpdated(@NotNull Country country) {}

            @Override
            public void onClientUpdate() {}
        });
        VisionManager.setModelPerformanceConfig(new Merged(new On(ModelPerformanceMode.FIXED, ModelPerformanceRate.LOW)));
        VisionManager.start();

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
                        directionsRoute = response.body().routes().get(0);
                        mapboxNavigation.startNavigation(directionsRoute);
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

        arLocationEngine.removeLocationUpdates();
        arLocationEngine.removeLocationEngineListener((LocationEngineListener) this);
        arLocationEngine.deactivate();

        mapboxNavigation.removeProgressChangeListener(this);
        mapboxNavigation.removeOffRouteListener(this);
        mapboxNavigation.stopNavigation();
    }

    @Override
    public void onLocationChanged(Location location) {}

    @Override
    @SuppressLint({"MissingPermission"})
    public void onConnected() {
        arLocationEngine.requestLocationUpdates();
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
            mapboxNavigation.startNavigation(response.routes().get(0));
        }
    }

    @Override
    public void onProgressChange(Location location, RouteProgress routeProgress) {
        lastRouteProgress = routeProgress;

        // Set route progress.
        VisionArManager.setRoute(new Route(
                getRoutePoints(routeProgress),
                (float) routeProgress.durationRemaining(),
                "",
                ""
        ));
    }

    @Override
    public void userOffRoute(Location location) {
        routeFetcher.findRouteFromRouteProgress(location, lastRouteProgress);
    }

    private RoutePoint[] getRoutePoints(@NotNull RouteProgress progress) {
        ArrayList<RoutePoint> routePoints = new ArrayList<>();
        DirectionsRoute mapboxNavigation = progress.directionsRoute();
        if (mapboxNavigation != null) {
            List<RouteLeg> legs = mapboxNavigation.legs();
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
        }

        return routePoints.toArray(new RoutePoint[0]);
    }
}
