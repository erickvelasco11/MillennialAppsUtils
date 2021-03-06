package co.com.millennialapps.utils.tools;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.Nullable;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.gson.Gson;
import com.google.maps.android.kml.KmlLayer;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import co.com.millennialapps.utils.common.interfaces.IAsyncResponse;
import co.com.millennialapps.utils.common.interfaces.ILatLngEvent;
import co.com.millennialapps.utils.common.interfaces.IMarkerEvent;
import co.com.millennialapps.utils.tools.tasks.PathFinder;

/**
 * Created by erick on 10/7/2017.
 */

public class MapHandler implements SensorEventListener {

    //region Constants
    //Measures
    public static final int METERS = 0;
    public static final int KILOMETERS = 1;

    //Colors
    public static final float HUE_RED = 0;
    public static final float HUE_ORANGE = 30;
    public static final float HUE_YELLOW = 60;
    public static final float HUE_GREEN = 120;
    public static final float HUE_CYAN = 180;
    public static final float HUE_AZURE = 210;
    public static final float HUE_BLUE = 240;
    public static final float HUE_VIOLET = 270;
    public static final float HUE_MAGENTA = 300;
    public static final float HUE_PINK = 330;

    //Zooms
    public static final float CLOSER_ZOOM = 24;
    public static final float DEFAULT_ZOOM = 17;
    public static final float MEDIUM_ZOOM = 11;
    public static final int VIEW_LOCATION = 1;
    public static final int VIEW_CITY = 2;

    public static final int POLYLINE_WIDTH_DEFAULT = 8;
    public static final int POLYLINE_WIDTH_BOLD = 15;

    //Map Types
    public static int MAP_NONE = 0;
    public static int MAP_NORMAL = 1;
    public static int MAP_SATELLITE = 2;
    public static int MAP_TERRAIN = 3;
    public static int MAP_HYBRID = 4;

    //Tilts
    public float TILT_FOR_NAVIGATION = 70;
    //endregion

    //region Variables for map
    private GoogleMap map;
    private Marker userMarker;
    private HashMap<String, Marker> markers = new HashMap<>();
    private LinkedList<Polyline> polylines = new LinkedList<>();
    private Location myLocation;
    private KmlLayer layer;
    //endregion

    //Sensor
    private SensorManager mSensorManager;
    private float[] accelerometerReading;
    private float[] magnetometerReading;
    private float lastDegree;
    private boolean navigating = false;

    //Location
    private LocationManager locationManager;
    private LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            myLocation = location;
            if (navigating) {
                zoomToMyPosition(true, TILT_FOR_NAVIGATION);
            } else {
                zoomToMyPosition(true, 0);
            }

            if (userMarker == null) {
                addUserMarker();
            } else {
                updateUserMarker();
            }
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        public void onProviderEnabled(String provider) {
        }

        public void onProviderDisabled(String provider) {
        }
    };

    //region Constructor
    public MapHandler(GoogleMap map) {
        this.map = map;
    }
    //endregion

    //region Enable Properties
    public void enableCustomInfoWindow(GoogleMap.InfoWindowAdapter adapter) {
        map.setInfoWindowAdapter(adapter);
    }

    public void enableAutoLocation(Activity activity, GoogleApiClient mGoogleApiClient, int viewZoom) {
        if (Permissions.checkPermission(activity, Permissions.GPS_FINE)
                || Permissions.checkPermission(activity, Permissions.GPS_COARSE)) {

            myLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

            if (myLocation != null) {
                map.setMyLocationEnabled(true);
                switch (viewZoom) {
                    case VIEW_LOCATION:
                        zoomToMyPosition(true, 0);
                        break;
                    case VIEW_CITY:
                        zoomToCurrentCity(activity);
                        break;
                }
            }
        }
    }
    //endregion

    public void startNavigate(Context context) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        Sensor accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        navigating = true;
    }

    public void stopNavigate() {
        navigating = false;
        mSensorManager.unregisterListener(this);
    }

    //region Clear
    public void clearMap(boolean clearUser) {
        map.clear();
        markers.clear();
        polylines.clear();
        if (!clearUser && userMarker != null) {
            //userMarker.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.ic_user_marker));
        } else {
            userMarker = null;
        }
    }

    public void clearMarkers() {
        for (Map.Entry<String, Marker> marker : markers.entrySet()) {
            marker.getValue().remove();
        }
        markers.clear();
    }

    public void clearPolylines() {
        for (Polyline polyline : polylines) {
            polyline.remove();
        }
        polylines.clear();
    }
    //endregion

    //region Markers
    public Marker addMarker(String id, LatLng latLng, Object tag) {
        MarkerOptions markerOptions = new MarkerOptions()
                .position(latLng);
        Marker marker = map.addMarker(markerOptions);
        marker.setTag(new Gson().toJson(tag));
        markers.put(id, marker);
        return marker;
    }

    public Marker addMarker(String id, LatLng latLng, Object tag, String title, String description) {
        MarkerOptions markerOptions = new MarkerOptions()
                .position(latLng)
                .title(title)
                .snippet(description);
        Marker marker = map.addMarker(markerOptions);
        marker.setTag(new Gson().toJson(tag));
        markers.put(id, marker);
        return marker;
    }

    public Marker addMarker(String id, LatLng latLng, Object tag, float color, String title, String description) {
        MarkerOptions markerOptions = new MarkerOptions()
                .position(latLng)
                .title(title)
                .snippet(description);
        Marker marker = map.addMarker(markerOptions);
        marker.setTag(new Gson().toJson(tag));
        markers.put(id, marker);
        coloringMarker(id, color);
        return marker;
    }

    private void addUserMarker() {
        if (myLocation != null) {
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(new LatLng(myLocation.getLatitude(), myLocation.getLongitude()));
            userMarker = map.addMarker(markerOptions);
        }
    }

    private void updateUserMarker() {
        if (myLocation != null) {
            userMarker.setPosition(new LatLng(myLocation.getLatitude(), myLocation.getLongitude()));
        }
    }

    public void coloringMarker(String key, float color) {
        if (markers.get(key) != null) {
            markers.get(key).setIcon(BitmapDescriptorFactory.defaultMarker(color));
        }
    }
    //endregion

    //region Zoom
    public void zoomTo(float zoom, LatLng latLng, boolean animate) {
        if (animate) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));
        } else {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));
        }
    }

    public void zoomTo(boolean animate, LatLng... latLng) {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng ll : latLng) {
            builder.include(ll);
        }
        LatLngBounds bounds = builder.build();

        if (animate) {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200));
        } else {
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200));
        }
    }

    public void zoomToMarkers() {
        if (!markers.isEmpty()) {
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            for (Map.Entry<String, Marker> marker : markers.entrySet()) {
                builder.include(marker.getValue().getPosition());
            }
            LatLngBounds bounds = builder.build();
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200));
        }
    }

    public void zoomAllWorld() {
        map.animateCamera(CameraUpdateFactory.zoomTo(0));
    }

    public void zoomToMyPosition(boolean animate, float tilt) {
        if (myLocation != null) {
            LatLng latLng = new LatLng(myLocation.getLatitude(), myLocation.getLongitude());

            CameraPosition camPos = CameraPosition
                    .builder(map.getCameraPosition())
                    .tilt(tilt)
                    .zoom(CLOSER_ZOOM)
                    .target(latLng)
                    .build();
            if (animate) {
                map.animateCamera(CameraUpdateFactory.newCameraPosition(camPos));
            } else {
                map.moveCamera(CameraUpdateFactory.newCameraPosition(camPos));
            }
        }
    }

    public void zoomToCurrentCity(Context context) {
        if (myLocation != null) {
            try {
                Geocoder gcd = new Geocoder(context, Locale.getDefault());
                List<Address> addresses = gcd.getFromLocation(myLocation.getLatitude(), myLocation.getLongitude(), 1);
                if (addresses.size() > 0) {
                    //TODO Return City
                }
            } catch (IOException e) {
                //FirebaseCrash.report(e);
            }
        }
    }
    //endregion

    //region Listeners
    public void addMarkerListener(@Nullable IMarkerEvent event) {
        map.setOnMarkerClickListener(marker -> {
            if (event != null) {
                event.onEvent(marker);
                return true;
            }
            return false;
        });
    }

    public void addMapClickListener(@Nullable ILatLngEvent event) {
        map.setOnMapClickListener(marker -> {
            if (event != null) {
                event.onEvent(marker);
            }
        });
    }

    public void addDragMarkerListener(@Nullable IMarkerEvent dragStart, @Nullable IMarkerEvent drag, @Nullable IMarkerEvent dragEnd) {
        map.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {
                if (dragStart != null) {
                    dragStart.onEvent(marker);
                }
            }

            @Override
            public void onMarkerDrag(Marker marker) {
                if (drag != null) {
                    drag.onEvent(marker);
                }
            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                if (dragEnd != null) {
                    dragEnd.onEvent(marker);
                }
            }
        });
    }

    public void addInfoWindowListener(@Nullable IMarkerEvent click, @Nullable IMarkerEvent close, @Nullable IMarkerEvent longClick) {
        map.setOnInfoWindowClickListener(marker -> {
            if (click != null) {
                click.onEvent(marker);
            }
        });
        map.setOnInfoWindowCloseListener(marker -> {
            if (close != null) {
                close.onEvent(marker);
            }
        });
        map.setOnInfoWindowLongClickListener(marker -> {
            if (longClick != null) {
                longClick.onEvent(marker);
            }
        });
    }
    //endregion

    //region Tools
    public float distance(LatLng from, LatLng to, int measure) {
        float[] results = new float[1];
        Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results);
        switch (measure) {
            case METERS:
                return results[0];
            case KILOMETERS:
                return results[0] / 1000;
            default:
                return results[0];
        }
    }

    public double direction(LatLng from, LatLng to) {
        double dx = to.latitude - from.latitude;
        double dy = to.longitude - from.longitude;
        double angule = Math.atan2(dy, dx) * 180 / Math.PI;
        if (angule < 0) {
            return angule + 360;
        } else {
            return angule;
        }
    }

    public void routes(LatLng from, LatLng to, String googleApiKey, IAsyncResponse response){
        new PathFinder(from, to, googleApiKey, response).execute();
    }

    public void changeMapType(int type){
        map.setMapType(type);
    }
    //endregion

    //region Geometry
    public void addCircle(LatLng center, int radius) {
        map.addCircle(new CircleOptions()
                .center(center)
                .radius(radius));
    }
    //endregion

    //region Polylines
    public void addPolyline(int argbColor, int width, LatLng... latLng) {
        PolylineOptions options = new PolylineOptions()
                .color(argbColor)
                .width(width)
                .add(latLng);
        polylines.add(map.addPolyline(options));
    }

    public void addPolyline(int argbColor, int width, List<LatLng> latLng) {
        PolylineOptions options = new PolylineOptions()
                .color(argbColor)
                .width(width)
                .add(latLng.toArray(new LatLng[latLng.size()]));
        polylines.add(map.addPolyline(options));
    }

    public void changePolylineStyles(int argbColor, int width) {
        for (Polyline p : polylines) {
            p.setColor(argbColor);
            p.setWidth(width);
        }
    }
    //endregion

    public void updateBearing(float bearing) {
        CameraPosition camPos = CameraPosition
                .builder(map.getCameraPosition())
                .bearing(bearing)
                .tilt(TILT_FOR_NAVIGATION)
                .build();
        map.animateCamera(CameraUpdateFactory.newCameraPosition(camPos));
    }

    public void startLocation(Activity activity) {
        if (locationManager == null) {
            locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        }
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                myLocation = location;
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };
        if (Permissions.checkPermission(activity, Permissions.GPS_FINE)) {
            locationManager.requestSingleUpdate(LocationManager.PASSIVE_PROVIDER, locationListener, null);
        }
    }

    public void startLocationUpdates(Activity activity) {
        if (locationManager == null) {
            locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        }
        if (Permissions.checkPermission(activity, Permissions.GPS_FINE)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_MAGNETIC_FIELD:
                magnetometerReading = event.values;
                break;
            case Sensor.TYPE_ACCELEROMETER:
                accelerometerReading = event.values;
                break;
        }
        if (accelerometerReading != null && magnetometerReading != null) {
            final float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrix(rotationMatrix, null,
                    accelerometerReading, magnetometerReading);
            final float[] orientationAngles = new float[3];
            float[] orientation = SensorManager.getOrientation(rotationMatrix, orientationAngles);
            float degree = (float) Math.toDegrees(orientation[0]);
            if (degree < lastDegree - 10 || degree > lastDegree + 10) {
                lastDegree = degree;
                //updateBearing(lastDegree);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    //region Getters
    public GoogleMap getMap() {
        return map;
    }

    public HashMap<String, Marker> getMarkers() {
        return markers;
    }

    public LatLng getMyLocation() {
        if (myLocation != null) {
            return new LatLng(myLocation.getLatitude(), myLocation.getLongitude());
        }
        return new LatLng(0, 0);
    }

    public KmlLayer getLayer() {
        return layer;
    }

    public boolean isNavigating() {
        return navigating;
    }
    //endregion

    //region Setters
    public void setMap(GoogleMap map) {
        this.map = map;
    }

    public void setMapStyle(MapStyleOptions mapStyleOptions) {
        map.setMapStyle(mapStyleOptions);
    }
    //endregion
}
