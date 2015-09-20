package com.bigapewhat.navigationcompass.CompassController;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bigapewhat.navigationcompass.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

/*
Created by Michael Karwowski

Note: Only one image size for all screens so it may not be best quality for certain devices
Compass by default points to north until longitude or latitude is entered
Compass is working like default compass where the phone must be flat on the x-axis other wise it will not work
 */
public class MainActivity extends AppCompatActivity implements SensorEventListener,  GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private boolean newArrived, firstWarning, hasAccelerometer;
    private String TAG = "GoogleApiClient";
    private Context context;
    private Button lonBtn, latBtn;

    private LocationRequest mLocationRequest;
    private LocationManager locationManager;
    private Location currentloc = new Location("currentlocationprovider");
    private Location destinationLoc = new Location("destinationlocationprovider");

    private GoogleApiClient mGoogleApiClient;
    private CompassOrientation compassOrientation;
    private ImageView compassImage;

    //set the destination to North by default, was not sure if it should be last location entered or
    //    kept at some default value
        private float defLon = 0, defLat = 90;
    //-----------------------------\\

    TextView textTemp;

    //Note: if user does not have accelerometer then user should not have option to download on play store because of
    //  uses-feature declared in app manifest
    //----------
    // set boolean for device accelerometer
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        PackageManager PM= this.getPackageManager();
        hasAccelerometer = PM.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        destinationLoc.setLatitude(defLat);
        context = this;

        //debugging text, left it so it will be easier to know whether location from gps is set
        textTemp = (TextView) findViewById(R.id.textTemp);

        //referance to custom compass class, setting up Accelerometer and Magnetometer
        compassOrientation = new CompassOrientation((SensorManager) getSystemService(SENSOR_SERVICE));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //access to google api for location services
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        //Get reference longitude and latitude buttons
        lonBtn = (Button) findViewById(R.id.lonBtn);
        latBtn = (Button) findViewById(R.id.latBtn);

        //Get reference to the compass image
        compassImage = (ImageView) findViewById(R.id.compassImg);

        //set onclick listeners for longitude and latitude
        lonBtn.setOnClickListener(lonBtnClicked);
        latBtn.setOnClickListener(latBtnClicked);
    }

    //when user resumes to app then turn sensor detection back on, and check if user has gps turned on
    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        boolean firstTimeOpened = sharedPref.getBoolean(getString(R.string.firstOpen), true);
        SharedPreferences.Editor editor = sharedPref.edit();

        if (firstTimeOpened) {
            welcomeUserMessage();
            editor.putBoolean(getString(R.string.firstOpen), false);
            editor.commit();
        } else {
            if (!hasAccelerometer) {
                buildAlertMessageNoAccelerometer();
            } else
                checkGpsStatus();
        }
    }

    //when user leaves app but does not kill it, turn off sensors to save battery
    @Override
    protected void onPause() {
        super.onPause();
        compassOrientation.onStopSensorService(this);
    }

    //Check for just in case user turns off GPS from notification drawer
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            onResume();
        }
    }

    //method for when changes in device sensor is detected
    //when sensor changes call custom compass class and calculate changes, return value is rotation for compass image
    @Override
    public void onSensorChanged(SensorEvent event) {
        RotateAnimation ra = compassOrientation.onSensorChanged(event, currentloc, destinationLoc);
        if(ra != null)
            compassImage.startAnimation(ra);
    }

    //not needed, any changes we get for this app will be enough whether it is deemed inaccurate or not
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    //when user clicks the longitude button, a dialog pops up allowing user to enter a float value for location
    //make sure that user only inputs floats and prevent crash if nothing was input
    View.OnClickListener lonBtnClicked = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Enter Longitude");

            final EditText input = new EditText(context);
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            builder.setView(input);

            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try{
                        defLon = Float.parseFloat(input.getText().toString());
                        destinationLoc.setLongitude(defLon);
                        newArrived = true;
                        firstWarning = true;
                    }catch(NumberFormatException e){
                        //no input
                        e.printStackTrace();
                    }
                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });

            builder.show();
        }
    };

    //when user clicks the latitude button, a dialog pops up allowing user to enter a float value for location
    //make sure that user only inputs floats and prevent crash if nothing was input
    View.OnClickListener latBtnClicked = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Enter Latitude");

            final EditText input = new EditText(context);
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            builder.setView(input);

            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        defLat = Float.parseFloat(input.getText().toString());
                        destinationLoc.setLatitude(defLat);
                        newArrived = true;
                        firstWarning = true;
                    }catch(NumberFormatException e){
                        //no input
                        e.printStackTrace();
                    }
                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });

            builder.show();
        }
    };

    //Check if gps is enabled if not show alert
    private void checkGpsStatus(){
        if(locationManager==null)
            locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps();
        }else
            compassOrientation.onStartSensorService(this);
    }

    //popup only when app is opened for the first time, welcome message with small detailsb
    private void welcomeUserMessage(){
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(("Welcome to my Compass app. This app by default points to true North. After user places location follow \"N\"" +
                "on compass to reach destination. When within 100 Meters to destination toast will appear, in 20 meters toast and vibration." +
                " Also a some info on the top left corner. Enjoy :)"))
                .setCancelable(false)
                .setPositiveButton("Ok, Thanks", new DialogInterface.OnClickListener() {
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        onResume();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    //Popup to warn user that GPS is turned off, forces user to turn on else app turns off
    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        dialog.cancel();
                        finish();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    //popup alerting user that there is no accelerometer detected on device
    private void buildAlertMessageNoAccelerometer(){
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your Device does not have an Accelerometer sensor. Without this sensor this app will not work")
                .setCancelable(false)
                .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        finish();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    //when app starts connect to google api client
    @Override
    protected void onStart() {
        super.onStart();
        // Connect the client.
        mGoogleApiClient.connect();
    }

    //when app terminates disconnect from google api client
    @Override
    protected void onStop() {
        // Disconnecting the client invalidates it.
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    //when connected to google api client, get constant location updates from gps passing this class as the looper
    @Override
    public void onConnected(Bundle bundle) {
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(500);

        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);

        currentloc = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        if(currentloc != null)
            textTemp.setText("Last Known Location Found, connecting to GPS location");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "GoogleApiClient connection has been suspend");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "GoogleApiClient connection has failed");
    }

    //when google api location has noticed a differece in user location this method is called in which
    //the distance is check, if distance is within 20 meters alert user
    @Override
    public void onLocationChanged(Location location) {
        currentloc = location;

        float distance = currentloc.distanceTo(destinationLoc);
        if(distance <= 100 && newArrived) {
            if (distance <= 20 && newArrived) {
                Toast.makeText(this, "Destination within 20 meters", Toast.LENGTH_LONG).show();
                newArrived = false;

                Vibrator v = (Vibrator) this.context.getSystemService(Context.VIBRATOR_SERVICE);
                long[] pattern = {0, 100, 100, 300, 200, 100, 500, 200, 100, 100, 100, 300, 200, 100, 500, 200, 100, 100, 100, 300, 200, 100, 500, 200, 100};

                v.vibrate(pattern, -1);
            }else if(firstWarning) {
                Toast.makeText(this, "Destination within 100 meters", Toast.LENGTH_LONG).show();
                firstWarning = false;
            }
        }

        textTemp.setText("Current Location = " + currentloc.getLatitude() + ", " + currentloc.getLongitude()
                + "\nDestination Location = " + destinationLoc.getLatitude() + ", " + destinationLoc.getLongitude()
                + "\nDistance = " + distance+ "m");
    }
}
