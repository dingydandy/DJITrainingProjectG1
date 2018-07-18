package com.dji.training.g1;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.maps2d.AMap;
import com.amap.api.maps2d.CameraUpdate;
import com.amap.api.maps2d.CameraUpdateFactory;
import com.amap.api.maps2d.MapView;
import com.amap.api.maps2d.model.BitmapDescriptorFactory;
import com.amap.api.maps2d.model.LatLng;
import com.amap.api.maps2d.model.Marker;
import com.amap.api.maps2d.model.MarkerOptions;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dji.common.battery.AggregationState;
import dji.common.battery.BatteryState;
import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.FlightMode;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointAction;
import dji.common.mission.waypoint.WaypointActionType;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.battery.Battery;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

public class MissionActivity extends FragmentActivity implements View.OnClickListener, AMap.OnMapClickListener {
    protected static final String TAG = "MainActivity";

    private MapView mapView;
    private AMap aMap;

    private Button locate, add, clear;
    private Button config, upload, start, stop;
    private Button pause, resume, confirmLanding, cancelLanding;

    private TextView altitudeTextview, horizontal_velocity, vertical_velocity, flightMode, batteryRemaining;

    private boolean isAdd = false;

    private double droneLocationLat = 181, droneLocationLng = 181;
    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private Marker droneMarker = null;

    private float altitude = 10.0f;
    private float mSpeed = 10.0f;

    private List<Waypoint> waypointList = new ArrayList<>();

    public static WaypointMission.Builder waypointMissionBuilder;
    private FlightController mFlightController;
    private WaypointMissionOperator instance;
    private WaypointMissionFinishedAction mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
    private WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;

    @Override
    protected void onResume(){
        super.onResume();
        initFlightController();
    }

    @Override
    protected void onPause(){
        super.onPause();
    }

    @Override
    protected void onDestroy(){
        removeListener();
        super.onDestroy();
    }

    /**
     * @Description : RETURN Button RESPONSE FUNCTION
     */
    public void onReturn(View view){
        Log.d(TAG, "onReturn");
        this.finish();
    }

    private void setResultToToast(final String string){
        MissionActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MissionActivity.this, string, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initUI() {

        locate = (Button) findViewById(R.id.locate);
        add = (Button) findViewById(R.id.add);
        clear = (Button) findViewById(R.id.clear);
        config = (Button) findViewById(R.id.config);
        upload = (Button) findViewById(R.id.upload);
        start = (Button) findViewById(R.id.start);
        stop = (Button) findViewById(R.id.stop);
        pause = (Button) findViewById(R.id.pause);
        resume = (Button) findViewById(R.id.resume);
        confirmLanding = (Button) findViewById(R.id.confirmLanding);
        cancelLanding = (Button) findViewById(R.id.cancelLanding);

        locate.setOnClickListener(this);
        add.setOnClickListener(this);
        clear.setOnClickListener(this);
        config.setOnClickListener(this);
        upload.setOnClickListener(this);
        start.setOnClickListener(this);
        stop.setOnClickListener(this);
        pause.setOnClickListener(this);
        resume.setOnClickListener(this);
        confirmLanding.setOnClickListener(this);
        cancelLanding.setOnClickListener(this);

        altitudeTextview = (TextView) findViewById(R.id.altitudeTextview);
        horizontal_velocity = (TextView) findViewById(R.id.horizontal_velocity);
        vertical_velocity = (TextView) findViewById(R.id.vertical_velocity);
        flightMode = (TextView) findViewById(R.id.flightMode);
        batteryRemaining = (TextView) findViewById(R.id.batteryRemaining);
    }

    private void initMapView() {

        if (aMap == null) {
            aMap = mapView.getMap();
            aMap.setOnMapClickListener(this);// add the listener for click for amap object
        }

        LatLng shenzhen = new LatLng(22.5362, 113.9454);
        aMap.addMarker(new MarkerOptions().position(shenzhen).title("Marker in Shenzhen"));
        aMap.moveCamera(CameraUpdateFactory.newLatLng(shenzhen));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.mission_activity);

        mapView = (MapView) findViewById(R.id.map);
        mapView.onCreate(savedInstanceState);

        initMapView();
        initUI();
        addListener();

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConnectChange(DJITrainingApplication.ConnectivityChangeEvent event) {

    }

    private void initFlightController() {

        BaseProduct product = DJITrainingApplication.getProductInstance();
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {
                mFlightController = ((Aircraft) product).getFlightController();
            }
        }

        if (mFlightController != null) {


            mFlightController.setMaxFlightRadiusLimitationEnabled(true, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    setMaxFlightRadius(500);
                    setMaxFlightHeight(200);
                }
            });
            mFlightController.setGoHomeHeightInMeters(20, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {

                }
            });
            getFlightInfos();
            getBatteryInfos();
        }
    }

    //Add Listener for WaypointMissionOperator
    private void addListener() {
        if (getWaypointMissionOperator() != null) {
            getWaypointMissionOperator().addListener(eventNotificationListener);
        }
    }

    private void removeListener() {
        if (getWaypointMissionOperator() != null) {
            getWaypointMissionOperator().removeListener(eventNotificationListener);
        }
    }

    private WaypointMissionOperatorListener eventNotificationListener = new WaypointMissionOperatorListener() {
        @Override
        public void onDownloadUpdate(WaypointMissionDownloadEvent downloadEvent) {

        }

        @Override
        public void onUploadUpdate(WaypointMissionUploadEvent uploadEvent) {

        }

        @Override
        public void onExecutionUpdate(WaypointMissionExecutionEvent executionEvent) {

        }

        @Override
        public void onExecutionStart() {

        }

        @Override
        public void onExecutionFinish(@Nullable final DJIError error) {
            setResultToToast("Execution finished: " + (error == null ? "Success!" : error.getDescription()));
        }
    };

    public WaypointMissionOperator getWaypointMissionOperator() {
        if (instance == null) {
            instance = DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();
        }
        return instance;
    }

    @Override
    public void onMapClick(LatLng point) {
        if (isAdd == true){
            markWaypoint(point);
            Waypoint mWaypoint = new Waypoint(point.latitude, point.longitude, altitude);
            mWaypoint.addAction(new WaypointAction(WaypointActionType.GIMBAL_PITCH, -89));
            mWaypoint.addAction(new WaypointAction(WaypointActionType.START_TAKE_PHOTO, 0));
            mWaypoint.addAction(new WaypointAction(WaypointActionType.GIMBAL_PITCH, 0));
            //Add Waypoints to Waypoint arraylist;
            if (waypointMissionBuilder != null) {
                waypointList.add(mWaypoint);
                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
            }else
            {
                waypointMissionBuilder = new WaypointMission.Builder();
                waypointList.add(mWaypoint);
                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
            }
        }else{
            setResultToToast("Cannot Add Waypoint");
        }
    }

    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    // Update the drone location based on states from MCU.
    private void updateDroneLocation(){

        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        //Create MarkerOptions object
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(pos);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneMarker != null) {
                    droneMarker.remove();
                }

                if (checkGpsCoordination(droneLocationLat, droneLocationLng)) {
                    droneMarker = aMap.addMarker(markerOptions);
                }
            }
        });
    }

    private void markWaypoint(LatLng point){
        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        Marker marker = aMap.addMarker(markerOptions);
        mMarkers.put(mMarkers.size(), marker);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.locate:{
                updateDroneLocation();
                cameraUpdate(); // Locate the drone's place
                break;
            }
            case R.id.add:{
                enableDisableAdd();
                break;
            }
            case R.id.clear: {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        aMap.clear();
                    }

                });
                waypointList.clear();
                waypointMissionBuilder.waypointList(waypointList);
                updateDroneLocation();
                break;
            }
            case R.id.config:{
                showSettingDialog();
                break;
            }
            case R.id.upload:{
                uploadWayPointMission();
                break;
            }
            case R.id.start:{
                startWaypointMission();
                break;
            }
            case R.id.stop:{
                stopWaypointMission();
                break;
            }
            case R.id.pause: {
                pauseWaypointMission();
                break;
            }
            case R.id.resume: {
                resumeWaypointMission();
                break;
            }
            case R.id.confirmLanding: {
                confirmLanding();
                break;
            }
            case R.id.cancelLanding: {
                cancelLanding();
                break;
            }
            default:
                break;
        }
    }

    private void cameraUpdate(){
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        float zoomlevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomlevel);
        aMap.moveCamera(cu);

    }

    private void enableDisableAdd(){
        if (isAdd == false) {
            isAdd = true;
            add.setText("退出");
        }else{
            isAdd = false;
            add.setText("添加");
        }
    }

    private void showSettingDialog(){
        LinearLayout wayPointSettings = (LinearLayout)getLayoutInflater().inflate(R.layout.waypoint_settings, null);

        final TextView wpAltitude_TV = (TextView) wayPointSettings.findViewById(R.id.altitude);
        RadioGroup speed_RG = (RadioGroup) wayPointSettings.findViewById(R.id.speed);
        RadioGroup actionAfterFinished_RG = (RadioGroup) wayPointSettings.findViewById(R.id.actionAfterFinished);
        RadioGroup heading_RG = (RadioGroup) wayPointSettings.findViewById(R.id.heading);

        speed_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.lowSpeed){
                    mSpeed = 3.0f;
                } else if (checkedId == R.id.MidSpeed){
                    mSpeed = 5.0f;
                } else if (checkedId == R.id.HighSpeed){
                    mSpeed = 10.0f;
                }
            }

        });

        actionAfterFinished_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d(TAG, "Select finish action");
                if (checkedId == R.id.finishNone){
                    mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
                } else if (checkedId == R.id.finishGoHome){
                    mFinishedAction = WaypointMissionFinishedAction.GO_HOME;
                } else if (checkedId == R.id.finishAutoLanding){
                    mFinishedAction = WaypointMissionFinishedAction.AUTO_LAND;
                } else if (checkedId == R.id.finishToFirst){
                    mFinishedAction = WaypointMissionFinishedAction.GO_FIRST_WAYPOINT;
                }
            }
        });

        heading_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Log.d(TAG, "Select heading");

                if (checkedId == R.id.headingNext) {
                    mHeadingMode = WaypointMissionHeadingMode.AUTO;
                } else if (checkedId == R.id.headingInitDirec) {
                    mHeadingMode = WaypointMissionHeadingMode.USING_INITIAL_DIRECTION;
                } else if (checkedId == R.id.headingRC) {
                    mHeadingMode = WaypointMissionHeadingMode.CONTROL_BY_REMOTE_CONTROLLER;
                } else if (checkedId == R.id.headingWP) {
                    mHeadingMode = WaypointMissionHeadingMode.USING_WAYPOINT_HEADING;
                }
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(wayPointSettings)
                .setPositiveButton("Finish",new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id) {

                        String altitudeString = wpAltitude_TV.getText().toString();
                        altitude = Integer.parseInt(nulltoIntegerDefalt(altitudeString));
                        Log.e(TAG,"altitude "+altitude);
                        Log.e(TAG,"speed "+mSpeed);
                        Log.e(TAG, "mFinishedAction "+mFinishedAction);
                        Log.e(TAG, "mHeadingMode "+mHeadingMode);
                        configWayPointMission();
                    }

                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }

                })
                .create()
                .show();
    }

    String nulltoIntegerDefalt(String value){
        if(!isIntValue(value)) value="0";
        return value;
    }

    boolean isIntValue(String val)
    {
        try {
            val=val.replace(" ","");
            Integer.parseInt(val);
        } catch (Exception e) {return false;}
        return true;
    }

    private void configWayPointMission(){

        if (waypointMissionBuilder == null){

            waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);

        }else
        {
            waypointMissionBuilder.finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);

        }

        if (waypointMissionBuilder.getWaypointList().size() > 0){

            for (int i=0; i< waypointMissionBuilder.getWaypointList().size(); i++){
                waypointMissionBuilder.getWaypointList().get(i).altitude = altitude;
            }

            setResultToToast("Set Waypoint attitude successfully");
        }

        DJIError error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
        if (error == null) {
            setResultToToast("loadWaypoint succeeded");
        } else {
            setResultToToast("loadWaypoint failed " + error.getDescription());
        }

    }

    private void uploadWayPointMission(){

        getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    setResultToToast("Mission upload successfully!");
                } else {
                    setResultToToast("Mission upload failed, error: " + error.getDescription() + " retrying...");
                    getWaypointMissionOperator().retryUploadMission(null);
                }
            }
        });

    }

    private void startWaypointMission(){

        getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("Mission Start: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });

    }

    private void stopWaypointMission(){

        getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("Mission Stop: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });

    }

    private void pauseWaypointMission() {
        getWaypointMissionOperator().pauseMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                setResultToToast("Mission Pause: " + (djiError == null ? "Successfully" : djiError.getDescription()));
            }
        });
    }

    private void resumeWaypointMission() {
        getWaypointMissionOperator().resumeMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                setResultToToast("Mission Pause: " + (djiError == null ? "Successfully" : djiError.getDescription()));
            }
        });
    }

    private void confirmLanding() {
        if(mFlightController != null) {
            mFlightController.confirmLanding(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    setResultToToast("Confirm Landing: " + (djiError == null ? "Successfully" : djiError.getDescription()));
                }
            });
        }
    }

    private void cancelLanding() {
        if(mFlightController != null) {
            mFlightController.cancelLanding(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    setResultToToast("Cancel Landing: " + (djiError == null ? "Successfully" : djiError.getDescription()));
                }
            });
        }
    }

    private void setMaxFlightRadius(int r) {
        mFlightController.setMaxFlightRadius(r, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {

            }
        });
    }

    private void setMaxFlightHeight(int h) {
        mFlightController.setMaxFlightHeight(h, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {

            }
        });
    }

    private void getFlightInfos() {
        if(mFlightController != null) {
            mFlightController.setStateCallback(new FlightControllerState.Callback() {
                @Override
                public void onUpdate(@NonNull FlightControllerState flightControllerState) {
                    float vx = flightControllerState.getVelocityX();
                    float vy = flightControllerState.getVelocityY();
                    float vz = flightControllerState.getVelocityZ();
                    float vh = (float)Math.sqrt(vx * vx + vy * vy);
                    float vv = vz;
                    updateVelocity(vh, vv);

                    float alt = flightControllerState.getAircraftLocation().getAltitude();
                    updateAltitude(alt);

                    FlightMode mode = flightControllerState.getFlightMode();
                    updateFlightMode(mode);
                    droneLocationLat = flightControllerState.getAircraftLocation().getLatitude();
                    droneLocationLng = flightControllerState.getAircraftLocation().getLongitude();
                    updateDroneLocation();
                }
            });
        }
    }

    private void getBatteryInfos() {
        Battery battery = DJITrainingApplication.getProductInstance().getBattery();
        if(battery != null) {
            battery.setStateCallback(new BatteryState.Callback() {
                @Override
                public void onUpdate(BatteryState batteryState) {
                    int remaining = batteryState.getChargeRemainingInPercent();
                    updateBatteryRemaining(remaining);
                }
            });
        }
    }

    private void updateVelocity(final float vh, final float vv) {
        horizontal_velocity.post(new Runnable() {
            @Override
            public void run() {
                horizontal_velocity.setText("水平：" + String.valueOf(vh) + " 米/秒");
            }
        });
        vertical_velocity.post(new Runnable() {
            @Override
            public void run() {
                vertical_velocity.setText("垂直：" + String.valueOf(vv) + " 米/秒");
            }
        });
    }

    private void updateAltitude(final float alt) {
        altitudeTextview.post(new Runnable() {
            @Override
            public void run() {
                altitudeTextview.setText("高度：" + String.valueOf(alt) + " 米");
            }
        });
    }

    private void updateFlightMode(final FlightMode mode) {
        flightMode.post(new Runnable() {
            @Override
            public void run() {
                flightMode.setText(mode.toString());
            }
        });
    }

    private void updateBatteryRemaining(final int remaining) {
        batteryRemaining.post(new Runnable() {
            @Override
            public void run() {
                batteryRemaining.setText("电池：" + String.valueOf(remaining) + " %");
            }
        });
    }
}
