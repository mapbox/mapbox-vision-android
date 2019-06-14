package com.mapbox.vision.examples

import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineListener
import com.mapbox.android.core.location.LocationEnginePriority
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.Point
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigationOptions
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import com.mapbox.services.android.navigation.v5.offroute.OffRouteListener
import com.mapbox.services.android.navigation.v5.route.RouteFetcher
import com.mapbox.services.android.navigation.v5.route.RouteListener
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress
import com.mapbox.vision.VisionManager
import com.mapbox.vision.VisionReplayManager
import com.mapbox.vision.ar.VisionArManager
import com.mapbox.vision.ar.core.models.Route
import com.mapbox.vision.ar.core.models.RoutePoint
import com.mapbox.vision.examples.util.FakeLocationEngine
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener
import com.mapbox.vision.mobile.core.models.position.GeoCoordinate
import com.mapbox.vision.mobile.core.models.position.VehicleState
import com.mapbox.vision.performance.ModelPerformance
import com.mapbox.vision.performance.ModelPerformanceConfig
import com.mapbox.vision.performance.ModelPerformanceMode
import com.mapbox.vision.performance.ModelPerformanceRate
import kotlinx.android.synthetic.main.activity_custom_ar.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber

class CustomArActivityKt : AppCompatActivity(), RouteListener, LocationEngineListener, OffRouteListener,
    ProgressChangeListener {

    private val modelPerfomanceConfig = ModelPerformanceConfig.Merged(
        ModelPerformance.On(
            ModelPerformanceMode.FIXED,
            ModelPerformanceRate.LOW
        )
    )

    // Handles navigation.
    private val mapboxNavigation: MapboxNavigation by lazy(LazyThreadSafetyMode.NONE) {
        MapboxNavigation(
            this,
            getString(R.string.mapbox_access_token),
            MapboxNavigationOptions.builder().enableOffRouteDetection(true).build(),
            arLocationEngine
        )
    }
    // Fetches route from points.
    private val routeFetcher: RouteFetcher by lazy(LazyThreadSafetyMode.NONE) {
        RouteFetcher(this, getString(R.string.mapbox_access_token))
    }

    private val arLocationEngine: LocationEngine by lazy(LazyThreadSafetyMode.NONE) {
        if (isReplaying) {
            FakeLocationEngine()
        } else {
            val provider = LocationEngineProvider(this)
            provider.obtainBestLocationEngineAvailable().also {
                it.priority = LocationEnginePriority.HIGH_ACCURACY
                it.interval = 0
                it.fastestInterval = 1000
            }
        }
    }
    private lateinit var directionsRoute: DirectionsRoute
    private lateinit var lastRouteProgress: RouteProgress

    // This dummy points will be used to build route. For real world test this needs to be changed to real values for
    // source and target locations.
    private val ROUTE_ORIGIN = Point.fromLngLat(27.648624, 53.933623)
    private val ROUTE_DESTINATION = Point.fromLngLat(27.689324, 53.945274)

    // use VisionReplayManager instead VisionManager
    private val isReplaying = true
    private val replayPath = "${Environment.getExternalStorageDirectory().path}/Telemetry/Replays/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_ar)
    }

    override fun onResume() {
        super.onResume()

        arLocationEngine.addLocationEngineListener(this)

        initDirectionsRoute()

        // Route need to be reestablished if off route happens.
        mapboxNavigation.addOffRouteListener(this)
        mapboxNavigation.addProgressChangeListener(this)

        routeFetcher.addRouteListener(this)

        if (isReplaying) {
            // Create and start VisionReplayManager.
            VisionReplayManager.create(replayPath)
            VisionReplayManager.setModelPerformanceConfig(modelPerfomanceConfig)
            VisionReplayManager.start(visionEventsListener = object : VisionEventsListener {
                override fun onVehicleStateUpdated(vehicleState: VehicleState) {
                    (arLocationEngine as FakeLocationEngine)
                        .setLocation(
                            Point.fromLngLat(
                                vehicleState.geoLocation.geoCoordinate.longitude,
                                vehicleState.geoLocation.geoCoordinate.latitude
                            )
                        )
                }
            })
            VisionReplayManager.setVideoSourceListener(custom_ar_view)

            VisionArManager.create(VisionReplayManager, custom_ar_view)

        } else {
            // Create and start VisionManager.
            VisionManager.create()
            VisionManager.setModelPerformanceConfig(modelPerfomanceConfig)
            VisionManager.start(visionEventsListener = object : VisionEventsListener {})
            VisionManager.setVideoSourceListener(custom_ar_view)

            VisionArManager.create(VisionManager, custom_ar_view)
        }
    }

    private fun initDirectionsRoute() {
        NavigationRoute.builder(this)
            .accessToken(getString(R.string.mapbox_access_token))
            .origin(ROUTE_ORIGIN)
            .destination(ROUTE_DESTINATION)
            .build()
            .getRoute(object : Callback<DirectionsResponse> {
                override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                    if (response.body() == null || response.body()!!.routes().isEmpty()) {
                        return
                    }

                    directionsRoute = response.body()!!.routes()[0]
                    setRoute(directionsRoute)
                }

                override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                    Timber.e(t)
                }
            })
    }

    override fun onPause() {
        super.onPause()

        VisionArManager.destroy()
        if (isReplaying) {
            VisionReplayManager.stop()
            VisionReplayManager.destroy()
        } else {
            VisionManager.stop()
            VisionManager.destroy()
        }

        arLocationEngine.removeLocationUpdates()
        arLocationEngine.removeLocationEngineListener(this)
        arLocationEngine.deactivate()

        mapboxNavigation.removeProgressChangeListener(this)
        mapboxNavigation.removeOffRouteListener(this)
        mapboxNavigation.stopNavigation()

        routeFetcher.clearListeners()
    }

    // RouteListener
    override fun onErrorReceived(throwable: Throwable?) {
        Timber.e(throwable)
        mapboxNavigation.stopNavigation()

        Toast.makeText(this, "Can not calculate the route requested", Toast.LENGTH_SHORT).show()
    }

    override fun onResponseReceived(response: DirectionsResponse, routeProgress: RouteProgress?) {
        mapboxNavigation.stopNavigation()

        if (response.routes().isEmpty()) {
            Toast.makeText(this, "Cannot calculate route", Toast.LENGTH_SHORT).show()
            return
        }

        lastRouteProgress = routeProgress ?: lastRouteProgress
        directionsRoute = response.routes()[0]
        setRoute(directionsRoute)
    }
    //\ RouteListener

    private fun setRoute(route: DirectionsRoute) {
        mapboxNavigation.startNavigation(route)
        val routePoints = route.getRoutePoints()
        VisionArManager.setRoute(
            Route(
                points = routePoints,
                eta = route.duration()?.toFloat() ?: 0f,
                sourceStreetName = "TODO()",
                targetStreetName = "TODO()"
            )
        )
    }

    //LocationEngineListener
    override fun onLocationChanged(location: Location?) = Unit

    @SuppressLint("MissingPermission")
    override fun onConnected() {
        arLocationEngine.requestLocationUpdates()
    }
    //\LocationEngineListener

    //OffRouteListener \
    override fun userOffRoute(location: Location?) {
        routeFetcher.findRouteFromRouteProgress(location, lastRouteProgress)
    }

    //ProgressChangeListener \
    override fun onProgressChange(location: Location?, routeProgress: RouteProgress) {
        lastRouteProgress = routeProgress
    }

    private fun DirectionsRoute.getRoutePoints(): Array<RoutePoint> {
        val routePoints = arrayListOf<RoutePoint>()
        legs()?.forEach { it ->
            it.steps()?.forEach { step ->
                val maneuverPoint = RoutePoint(
                    GeoCoordinate(
                        latitude = step.maneuver().location().latitude(),
                        longitude = step.maneuver().location().longitude()
                    )
                )
                routePoints.add(maneuverPoint)

                step.intersections()
                    ?.map {
                        RoutePoint(
                            GeoCoordinate(
                                latitude = step.maneuver().location().latitude(),
                                longitude = step.maneuver().location().longitude()
                            )
                        )
                    }
                    ?.let { stepPoints ->
                        routePoints.addAll(stepPoints)
                    }
            }
        }

        return routePoints.toTypedArray()
    }
}
