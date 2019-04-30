package com.mapbox.vision.examples

import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
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
import com.mapbox.vision.ar.VisionArManager
import com.mapbox.vision.ar.core.models.Route
import com.mapbox.vision.ar.core.models.RoutePoint
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener
import com.mapbox.vision.mobile.core.models.position.GeoCoordinate
import com.mapbox.vision.performance.ModelPerformance
import com.mapbox.vision.performance.ModelPerformanceConfig
import com.mapbox.vision.performance.ModelPerformanceMode
import com.mapbox.vision.performance.ModelPerformanceRate
import kotlinx.android.synthetic.main.activity_ar_navigation.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Example shows how Vision and VisionAR SDKs are used to draw AR lane over the video stream from camera.
 * Also, Mapbox navigation services are used to build route and  navigation session.
 */
class ArActivityKt : AppCompatActivity(), LocationEngineListener, RouteListener, ProgressChangeListener,
    OffRouteListener {

    // Handles navigation.
    private lateinit var mapboxNavigation: MapboxNavigation
    // Fetches route from points.
    private lateinit var routeFetcher: RouteFetcher
    private lateinit var lastRouteProgress: RouteProgress
    private lateinit var directionsRoute: DirectionsRoute
    private lateinit var arLocationEngine: LocationEngine

    // This dummy points will be used to build route. For real world test this needs to be changed to real values for
    // source and target locations.
    private val ROUTE_ORIGIN = Point.fromLngLat(27.654285, 53.928057)
    private val ROUTE_DESTINATION = Point.fromLngLat(27.655637, 53.935712)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContentView(R.layout.activity_ar_navigation)

        // Initialize navigation with your Mapbox access token.
        mapboxNavigation = MapboxNavigation(
            this,
            getString(R.string.mapbox_access_token),
            MapboxNavigationOptions.builder().enableOffRouteDetection(true).build()
        )

        // Initialize route fetcher with your Mapbox access token.
        routeFetcher = RouteFetcher(this, getString(R.string.mapbox_access_token))
        routeFetcher.addRouteListener(this)

        val provider = LocationEngineProvider(this)
        arLocationEngine = provider.obtainBestLocationEngineAvailable()
        arLocationEngine.priority = LocationEnginePriority.HIGH_ACCURACY
        arLocationEngine.interval = 0
        arLocationEngine.fastestInterval = 1000
    }

    override fun onResume() {
        super.onResume()
        arLocationEngine.addLocationEngineListener(this)

        initDirectionsRoute()

        // Route need to be reestablished if off route happens.
        mapboxNavigation.addOffRouteListener(this)
        mapboxNavigation.addProgressChangeListener(this)

        // Create and start VisionManager.
        VisionManager.create()
        VisionManager.setModelPerformanceConfig(
            ModelPerformanceConfig.Merged(
                ModelPerformance.On(
                    ModelPerformanceMode.FIXED,
                    ModelPerformanceRate.LOW
                )
            )
        )
        VisionManager.start(visionEventsListener = object : VisionEventsListener {})

        VisionManager.setVideoSourceListener(mapbox_ar_view)

        // Create VisionArManager.
        VisionArManager.create(VisionManager, mapbox_ar_view)
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
                    mapboxNavigation.startNavigation(directionsRoute)

                    // Set route progress.
                    VisionArManager.setRoute(
                        Route(
                            directionsRoute.getRoutePoints(),
                            directionsRoute.duration()?.toFloat() ?: 0f,
                            "",
                            ""
                        )
                    )
                }

                override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                    t.printStackTrace()
                }
            })
    }

    override fun onPause() {
        super.onPause()
        VisionArManager.destroy()
        VisionManager.stop()
        VisionManager.destroy()

        arLocationEngine.removeLocationUpdates()
        arLocationEngine.removeLocationEngineListener(this as LocationEngineListener)
        arLocationEngine.deactivate()

        mapboxNavigation.removeProgressChangeListener(this)
        mapboxNavigation.removeOffRouteListener(this)
        mapboxNavigation.stopNavigation()
    }

    override fun onLocationChanged(location: Location) {}

    @SuppressLint("MissingPermission")
    override fun onConnected() {
        arLocationEngine.requestLocationUpdates()
    }

    override fun onErrorReceived(throwable: Throwable?) {
        throwable?.printStackTrace()

        mapboxNavigation.stopNavigation()
        Toast.makeText(this, "Can not calculate the route requested", Toast.LENGTH_SHORT).show()
    }

    override fun onResponseReceived(response: DirectionsResponse, routeProgress: RouteProgress?) {
        mapboxNavigation.stopNavigation()
        if (response.routes().isEmpty()) {
            Toast.makeText(this, "Can not calculate the route requested", Toast.LENGTH_SHORT).show()
        } else {
            mapboxNavigation.startNavigation(response.routes()[0])

            val route = response.routes()[0]

            // Set route progress.
            VisionArManager.setRoute(
                Route(
                    route.getRoutePoints(),
                    route.duration()?.toFloat() ?: 0f,
                    "",
                    ""
                )
            )
        }
    }

    override fun onProgressChange(location: Location, routeProgress: RouteProgress) {
        lastRouteProgress = routeProgress
    }

    override fun userOffRoute(location: Location) {
        routeFetcher.findRouteFromRouteProgress(location, lastRouteProgress)
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
