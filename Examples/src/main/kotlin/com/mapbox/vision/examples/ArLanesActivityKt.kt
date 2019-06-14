package com.mapbox.vision.examples

import android.os.Bundle
import android.os.Environment
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute
import com.mapbox.services.android.navigation.v5.route.RouteFetcher
import com.mapbox.services.android.navigation.v5.route.RouteListener
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress
import com.mapbox.vision.VisionReplayManager
import com.mapbox.vision.ar.VisionArManager
import com.mapbox.vision.ar.core.models.Route
import com.mapbox.vision.ar.core.models.RoutePoint
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener
import com.mapbox.vision.mobile.core.models.Camera
import com.mapbox.vision.mobile.core.models.position.GeoCoordinate
import com.mapbox.vision.mobile.core.models.position.VehicleState
import com.mapbox.vision.performance.ModelPerformance
import com.mapbox.vision.performance.ModelPerformanceConfig
import com.mapbox.vision.performance.ModelPerformanceMode
import com.mapbox.vision.performance.ModelPerformanceRate
import kotlinx.android.synthetic.main.activity_ar_navigation.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.TimeUnit

class ArLanesActivityKt : AppCompatActivity(), RouteListener {

    companion object {

        private val REPLAY_PATH = "${Environment.getExternalStorageDirectory().absolutePath}/Telemetry/Replays/"
        private val ROUTE_FROM = Point.fromLngLat(27.639272, 53.9310852)
        private val ROUTE_TO = Point.fromLngLat(27.6897746, 53.9447667)

        private val LEFT_START = GeoCoordinate(53.941834, 27.676722)
        private val LEFT_END = GeoCoordinate(53.941784, 27.676777)
        private val RIGHT_START = GeoCoordinate(53.942614, 27.679201)
        private val RIGHT_END = GeoCoordinate(53.942574, 27.679257)
    }

    private lateinit var routeFetcher: RouteFetcher

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_navigation)

        routeFetcher = RouteFetcher(this, getString(R.string.mapbox_access_token))
        routeFetcher.addRouteListener(this)
    }

    override fun onResume() {
        super.onResume()
        VisionReplayManager.create(REPLAY_PATH)
        VisionReplayManager.start(object : VisionEventsListener {
            override fun onVehicleStateUpdated(vehicleState: VehicleState) {
                lanes_view.post {
                    lanes_view.setPoints(
                        leftLaneStart = VisionReplayManager.worldToPixel(
                            VisionReplayManager.geoToWorld(LEFT_START)
                        ),
                        leftLaneEnd = VisionReplayManager.worldToPixel(
                            VisionReplayManager.geoToWorld(LEFT_END)
                        ),
                        rightLaneStart = VisionReplayManager.worldToPixel(
                            VisionReplayManager.geoToWorld(RIGHT_START)
                        ),
                        rightLaneEnd = VisionReplayManager.worldToPixel(
                            VisionReplayManager.geoToWorld(RIGHT_END)
                        )
                    )
                }
            }

            override fun onCameraUpdated(camera: Camera) {
            }
        })
        VisionReplayManager.setProgress(TimeUnit.SECONDS.toMillis(30))
        VisionReplayManager.setModelPerformanceConfig(
            ModelPerformanceConfig.Merged(
                performance = ModelPerformance.On(ModelPerformanceMode.FIXED, ModelPerformanceRate.LOW)
            )
        )
        VisionReplayManager.setVideoSourceListener(mapbox_ar_view)

        VisionArManager.create(VisionReplayManager, mapbox_ar_view)

        getRoute(origin = ROUTE_FROM, destination = ROUTE_TO)
    }

    private fun getRoute(origin: Point, destination: Point) {
        NavigationRoute.builder(this)
            .accessToken(Mapbox.getAccessToken()!!)
            .origin(origin)
            .destination(destination)
            .build()
            .getRoute(object : Callback<DirectionsResponse> {
                override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                    if (response.body() == null || response.body()!!.routes().size < 1) {
                        return
                    }

                    val route = response.body()!!.routes()[0]
                    setRoute(route)
                }

                override fun onFailure(call: Call<DirectionsResponse>, throwable: Throwable) {
                    throwable.printStackTrace()
                }
            })
    }

    override fun onPause() {
        super.onPause()
        VisionArManager.destroy()

        VisionReplayManager.stop()
        VisionReplayManager.destroy()
    }

    override fun onErrorReceived(throwable: Throwable?) {
        throwable?.printStackTrace()

        Toast.makeText(this, R.string.can_not_calculate_new_route, Toast.LENGTH_SHORT).show()
    }

    override fun onResponseReceived(response: DirectionsResponse, routeProgress: RouteProgress?) {
        if (response.routes().isEmpty()) {
            Toast.makeText(this, R.string.can_not_calculate_new_route, Toast.LENGTH_SHORT).show()
            return
        }

        setRoute(response.routes()[0])
    }

    private fun setRoute(route: DirectionsRoute) {
        VisionArManager.setRoute(
            Route(
                points = route.getRoutePoints(),
                eta = route.duration()?.toFloat() ?: 0f,
                sourceStreetName = "TODO()",
                targetStreetName = "TODO()"
            )
        )
    }

    private fun DirectionsRoute.getRoutePoints(): Array<RoutePoint> {
        val routePoints = arrayListOf<RoutePoint>()
        legs()?.forEach { leg ->
            leg.steps()?.forEach { step ->
                val maneuverPoint = RoutePoint(
                    GeoCoordinate(
                        latitude = step.maneuver().location().latitude(),
                        longitude = step.maneuver().location().longitude()
                    )
                )
                routePoints.add(maneuverPoint)

                step.intersections()
                    ?.map { intersection ->
                        RoutePoint(
                            GeoCoordinate(
                                latitude = intersection.location().latitude(),
                                longitude = intersection.location().longitude()
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
