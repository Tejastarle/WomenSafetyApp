package org.tejas.womensafetyapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Collections;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private GeofencingClient geofencingClient;
    private DatabaseReference databaseReference;

    private final String GEOFENCE_ID = "UnsafeArea";
    private final LatLng UNSAFE_LOCATION = new LatLng(19.075984, 72.877656); // Replace with actual coordinates

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Firebase Initialization
        databaseReference = FirebaseDatabase.getInstance().getReference("EmergencyAlerts");

        // Initialize Location Services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        geofencingClient = LocationServices.getGeofencingClient(this);

        // Load Google Map
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Set up SOS button
        findViewById(R.id.sosButton).setOnClickListener(v -> sendEmergencyMessage());

        // Set up Geofencing
        setupGeofence();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Check permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return;
        }

        // Enable My Location and Move Camera
        mMap.setMyLocationEnabled(true);
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                LatLng userLocation = new LatLng(location.getLatitude(), location.getLongitude());
                mMap.addMarker(new MarkerOptions().position(userLocation).title("You are here"));
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15));
            }
        });
    }

    private void sendEmergencyMessage() {
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                String emergencyContact = "1234567890"; // Replace with actual contact
                String message = "I need help! My location is: https://maps.google.com/?q="
                        + location.getLatitude() + "," + location.getLongitude();

                // Save to Firebase
                databaseReference.push().setValue(new EmergencyAlert(emergencyContact, message, System.currentTimeMillis()));

                // Feedback
                Toast.makeText(this, "Emergency alert sent!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to get location!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupGeofence() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
            return;
        }

        Geofence geofence = new Geofence.Builder()
                .setRequestId(GEOFENCE_ID)
                .setCircularRegion(UNSAFE_LOCATION.latitude, UNSAFE_LOCATION.longitude, 500) // 500-meter radius
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build();

        GeofencingRequest geofencingRequest = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();

        geofencingClient.addGeofences(geofencingRequest, getGeofencePendingIntent())
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Geofence added!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to add Geofence", Toast.LENGTH_SHORT).show());
    }

    private PendingIntent getGeofencePendingIntent() {
        Intent intent = new Intent(this, GeofenceBroadcastReceiver.class);
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // Handle permissions
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 || requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show();
                setupGeofence();
            } else {
                Toast.makeText(this, "Permission denied!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
