package com.mapbox.vision.examples;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Environment;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.vision.VisionReplayManager;
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener;
import com.mapbox.vision.mobile.core.models.AuthorizationStatus;
import com.mapbox.vision.mobile.core.models.Camera;
import com.mapbox.vision.mobile.core.models.Country;
import com.mapbox.vision.mobile.core.models.FrameSegmentation;
import com.mapbox.vision.mobile.core.models.classification.FrameSignClassifications;
import com.mapbox.vision.mobile.core.models.detection.FrameDetections;
import com.mapbox.vision.mobile.core.models.frame.PixelCoordinate;
import com.mapbox.vision.mobile.core.models.position.GeoCoordinate;
import com.mapbox.vision.mobile.core.models.position.GeoLocation;
import com.mapbox.vision.mobile.core.models.position.VehicleState;
import com.mapbox.vision.mobile.core.models.road.RoadDescription;
import com.mapbox.vision.mobile.core.models.world.WorldCoordinate;
import com.mapbox.vision.mobile.core.models.world.WorldDescription;
import com.mapbox.vision.view.VisionView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.view.View.GONE;

public class POIActivity extends BaseActivity {

    // POI will start to appear at this distance, starting with transparent and appearing gradually
    private static final int MIN_DISTANCE_METERS_FOR_DRAW_LABEL = 400;

    // POI will start to appear from transparent to non-transparent during this first meters of showing distance
    private static final int DISTANCE_FOR_ALPHA_APPEAR_METERS = 150;
    private static final int LABEL_SIZE_METERS = 16;
    private static final int LABEL_ABOVE_GROUND_METERS = 8;
    // Download session from tutorial and push to device
    private static final String SESSION_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/session";

    private List<POI> poiList = new ArrayList<>();
    private boolean visionReplayManagerWasInit = false;

    @Nullable
    private ImageView poiView = null;
    @Nullable
    private VisionView visionView = null;
    @Nullable
    private TextView cameraCalibrationView = null;

    private VisionEventsListener visionEventsListener = new VisionEventsListener() {

        private boolean cameraCalibrated = false;
        private Paint paint = new Paint();
        private Canvas canvasCameraFrame = new Canvas();
        private Bitmap bitmapCameraFrame = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);

        public void onAuthorizationStatusUpdated(@NonNull AuthorizationStatus authorizationStatus) { }

        public void onFrameSegmentationUpdated(@NonNull FrameSegmentation frameSegmentation) { }

        public void onFrameDetectionsUpdated(@NonNull FrameDetections frameDetections) { }

        public void onFrameSignClassificationsUpdated(@NonNull FrameSignClassifications frameSignClassifications) { }

        public void onRoadDescriptionUpdated(@NonNull RoadDescription roadDescription) { }

        public void onWorldDescriptionUpdated(@NonNull WorldDescription worldDescription) { }

        public void onCameraUpdated(@NonNull Camera camera) {
            if (camera.getCalibrationProgress() == 1.0f && !cameraCalibrated) {
                cameraCalibrated = true;
                bitmapCameraFrame = Bitmap.createBitmap(camera.getFrameWidth(), camera.getFrameHeight(), Bitmap.Config.ARGB_8888);
                canvasCameraFrame = new Canvas(bitmapCameraFrame);
                runOnUiThread (() -> {
                    if (cameraCalibrationView != null) {
                        cameraCalibrationView.setVisibility(GONE);
                    }
                });
            } else {
                runOnUiThread ( () -> {
                    if (cameraCalibrationView != null) {
                        String text = getString(R.string.camera_calibration_progress, (int)(camera.getCalibrationProgress() * 100));
                        cameraCalibrationView.setText(text);
                    }
                });
            }
        }

        public void onCountryUpdated(@NonNull Country country) { }

        public void onUpdateCompleted() { }

        public void onVehicleStateUpdated(@NonNull VehicleState vehicleState) {
            if (cameraCalibrated) {
                updatePOIStateAndDraw(vehicleState.getGeoLocation());
            }
        }

        private void updatePOIStateAndDraw(GeoLocation newVehicleGeoLocation) {
            if (poiList.isEmpty()) {
                return;
            }

            List<POIState> poiStateList = calculatePOIStateListRegardingVehicle(newVehicleGeoLocation.getGeoCoordinate());
            poiStateList = filterPOIByDistance(poiStateList);
            if (poiStateList.isEmpty()) {
                return;
            }
            final List<POIDrawData> poiDrawDataList = preparePOIDrawData(poiStateList);
            updateBitmapByPOIList(poiDrawDataList);
            runOnUiThread(() -> {
                if (poiView != null) {
                    poiView.setImageBitmap(bitmapCameraFrame);
                }
            });
        }

        // Calculate POI distance to vehicle and WorldCoordinates regarding the vehicle
        private List<POIState> calculatePOIStateListRegardingVehicle(@NonNull GeoCoordinate currentVehicleGeoCoordinate) {
            final List<POIState> poiStateList = new ArrayList<>();
            final LatLng currentVehicleLatLng = new LatLng(currentVehicleGeoCoordinate.getLatitude(), currentVehicleGeoCoordinate.getLongitude());
            for (POI poi: poiList) {
                final LatLng latLng = new LatLng(poi.getLatitude(), poi.getLongitude());
                final GeoCoordinate geoCoordinate = new GeoCoordinate(latLng.getLatitude(), latLng.getLongitude());
                final WorldCoordinate worldCoordinate = VisionReplayManager.geoToWorld(geoCoordinate);
                if (worldCoordinate == null) {
                    continue;
                }
                int distanceToVehicle = (int)latLng.distanceTo(currentVehicleLatLng);
                POIState poiState = new POIState(poi, distanceToVehicle, worldCoordinate);
                poiStateList.add(poiState);
            }
            return poiStateList;
        }

        // Show only POI which is close enough and behind the car
        @NonNull
        private List<POIState> filterPOIByDistance(List<POIState> poiStateList) {
            final List<POIState> result = new ArrayList<>();
            for (POIState poiState: poiStateList) {
                double x = poiState.worldCoordinate.getX();
                if (x > 0 && poiState.getDistanceToVehicle() < MIN_DISTANCE_METERS_FOR_DRAW_LABEL) {
                    result.add(poiState);
                }
            }
            return result;
        }

        @NonNull
        private List<POIDrawData> preparePOIDrawData(List<POIState> poiStateList){

            // Prepare bounding rect for POI in mobile screen coordinates
            final List<POIDrawData> poiDrawDataList = new ArrayList<>();
            for (POIState poiState: poiStateList) {
                final Rect poiBitmapRect = calculatePOIScreenRect(poiState.getWorldCoordinate());
                final int poiLabelAlpha = calculatePOILabelAlpha(poiState);
                final POIDrawData poiDrawData = new POIDrawData(poiState.getPOI().getBitmap(), poiBitmapRect, poiLabelAlpha);
                poiDrawDataList.add(poiDrawData);
            }
            return poiDrawDataList;
        }

        private Rect calculatePOIScreenRect(@NonNull WorldCoordinate poiWorldCoordinate) {

            // Calculate left top coordinate of POI in real world using POI world coordinate
            final WorldCoordinate worldLeftTop = poiWorldCoordinate.copy(
                    poiWorldCoordinate.getX(),
                    poiWorldCoordinate.getY() + (float) LABEL_SIZE_METERS / 2,
                    poiWorldCoordinate.getZ() + LABEL_ABOVE_GROUND_METERS + LABEL_SIZE_METERS
            );

            // Calculate right bottom coordinate of POI in real world using POI world coordinate
            final WorldCoordinate worldRightBottom = poiWorldCoordinate.copy(
                    poiWorldCoordinate.getX(),
                    poiWorldCoordinate.getY() - (float) LABEL_SIZE_METERS / 2,
                    poiWorldCoordinate.getZ() + LABEL_ABOVE_GROUND_METERS
            );

            Rect poiBitmapRect = new Rect(0, 0, 0, 0);

            // Calculate POI left top position on camera frame from real word coordinates
            PixelCoordinate pixelLeftTop = VisionReplayManager.worldToPixel(worldLeftTop);
            if (pixelLeftTop == null) {
                return poiBitmapRect;
            }
            poiBitmapRect.left = pixelLeftTop.getX();
            poiBitmapRect.top = pixelLeftTop.getY();

            // Calculate POI right bottom position on camera frame from real word coordinates
            PixelCoordinate pixelRightTop = VisionReplayManager.worldToPixel(worldRightBottom);
            if (pixelRightTop == null) {
                return poiBitmapRect;
            }
            poiBitmapRect.right = pixelRightTop.getX();
            poiBitmapRect.bottom = pixelRightTop.getY();

            return poiBitmapRect;
        }

        private int calculatePOILabelAlpha(@NonNull POIState poiState) {
            int minDistance = Math.min(MIN_DISTANCE_METERS_FOR_DRAW_LABEL - poiState.distanceToVehicle, DISTANCE_FOR_ALPHA_APPEAR_METERS);
            return (int)((minDistance / (float)DISTANCE_FOR_ALPHA_APPEAR_METERS) * 255);
        }

        private void updateBitmapByPOIList(@NonNull List<POIDrawData> poiDrawDataList) {
            canvasCameraFrame.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            for (POIDrawData drawPoi : poiDrawDataList) {
                paint.setAlpha(drawPoi.getPOIBitmapAlpha());
                canvasCameraFrame.drawBitmap(drawPoi.getPOIBitmap(), null, drawPoi.getPOIBitmapRect(), paint);
            }
        }
    };

    @Override
    public void onPermissionsGranted() {
        startVisionManager();
    }

    @Override
    public void initViews() {
        setContentView(R.layout.activity_poi);
        poiView = findViewById(R.id.poi_view);
        visionView = findViewById(R.id.vision_view);
        cameraCalibrationView = findViewById(R.id.camera_calibration_text);
        poiList = providePOIList();
    }

    @Override
    public void onStart() {
        super.onStart();
        startVisionManager();
    }

    @Override
    public void onStop() {
        super.onStop();
        stopVisionManager();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (visionView != null) {
            visionView.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (visionView != null) {
            visionView.onPause();
        }
    }

    private void startVisionManager() {
        if (allPermissionsGranted() && !visionReplayManagerWasInit && visionView != null) {
            VisionReplayManager.create(SESSION_PATH);
            VisionReplayManager.setVisionEventsListener(visionEventsListener);
            visionView.setVisionManager(VisionReplayManager.INSTANCE);
            VisionReplayManager.start();
            visionReplayManagerWasInit = true;
        }
    }

    private void stopVisionManager() {
        if (visionReplayManagerWasInit) {
            VisionReplayManager.stop();
            VisionReplayManager.destroy();
            visionReplayManagerWasInit = false;
        }
    }

    private List<POI> providePOIList() {
        POI poiGasStation = new POI(27.674764394760132, 53.9405971055192, getBitmapFromAssets("ic_gas_station.png"));
        POI poiCarWash = new POI(27.675944566726685, 53.94105180084251, getBitmapFromAssets("ic_car_wash.png"));
        return Arrays.asList(poiGasStation, poiCarWash);
    }

    private Bitmap getBitmapFromAssets(@NonNull String asset) {
        final AssetManager assetManager = this.getAssets();
        try {
            final InputStream stream = assetManager.open(asset);
            return BitmapFactory.decodeStream(stream);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static class POI {

        @NonNull
        private final Bitmap bitmap;
        private final double longitude;
        private final double latitude;

        POI(double longitude, double latitude, @NonNull Bitmap bitmap) {
            this.longitude = longitude;
            this.latitude = latitude;
            this.bitmap = bitmap;
        }

        double getLongitude() {
            return longitude;
        }

        double getLatitude() {
            return latitude;
        }

        @NonNull
        Bitmap getBitmap() {
            return bitmap;
        }
    }

    private static class POIDrawData {

        @NonNull
        private Bitmap poiBitmap;
        private Rect poiBitmapRect;
        private int poiBitmapAlpha;

        POIDrawData(@NonNull Bitmap poiBitmap, Rect poiBitmapRect, int poiBitmapAlpha) {
            this.poiBitmap = poiBitmap;
            this.poiBitmapRect = poiBitmapRect;
            this.poiBitmapAlpha = poiBitmapAlpha;
        }

        @NonNull
        Bitmap getPOIBitmap() {
            return poiBitmap;
        }

        Rect getPOIBitmapRect() {
            return poiBitmapRect;
        }

        int getPOIBitmapAlpha() {
            return poiBitmapAlpha;
        }
    }

    private static class POIState {

        @NonNull
        private POI poi;
        @NonNull
        private WorldCoordinate worldCoordinate;
        private int distanceToVehicle;

        POIState(@NonNull POI poi, int distanceToVehicle, @NonNull WorldCoordinate worldCoordinate) {
            this.poi = poi;
            this.distanceToVehicle = distanceToVehicle;
            this.worldCoordinate = worldCoordinate;
        }

        @NonNull
        POI getPOI() {
            return poi;
        }

        @NonNull
        WorldCoordinate getWorldCoordinate() {
            return worldCoordinate;
        }

        int getDistanceToVehicle() {
            return distanceToVehicle;
        }
    }
}
