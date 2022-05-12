package edu.cuhk.mapnotes.activities;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.room.Room;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

import edu.cuhk.mapnotes.NotesTreeActivity;
import edu.cuhk.mapnotes.R;
import edu.cuhk.mapnotes.databinding.ActivityMapsBinding;
import edu.cuhk.mapnotes.datatypes.AppDatabase;
import edu.cuhk.mapnotes.datatypes.NotePin;
import edu.cuhk.mapnotes.util.HelpButtonOnClickListener;
import edu.cuhk.mapnotes.util.NoteEntryUtil;
import edu.cuhk.mapnotes.util.NotePinUtil;

public class MapsActivity extends FragmentActivity
        implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private HashMap<Integer, Marker> notePinsMapping = new HashMap<>();

    private GoogleMap mMap;
    private ActivityMapsBinding binding;

    public static AppDatabase noteDatabase;

    AlertDialog.Builder builder;

    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        syncMapView();

        startRoomDatabase();
        // todo addrandompin is debug feature
        addRandomPin();

        // Tree view button
        CheckBox treeViewCheckBox = findViewById(R.id.notetree_checkbox);
        treeViewCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> goToNotesTreeActivity());

        // my location button
        FloatingActionButton mapMyLocationButton = binding.fabMyLocation;
        mapMyLocationButton.setOnClickListener(view -> tryMoveCameraToGpsLocation());

        this.showWelcomeDialog();
    }

    private void syncMapView() {
        // Obtain the SupportMapFragment and get notified when the map is ready to be used
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    private void goToNotesTreeActivity() {
        Intent intent = new Intent(this, NotesTreeActivity.class);
        startActivity(intent);
    }

    private void startRoomDatabase() {
        // todo the allowMainThreadQueries is unsafe
        noteDatabase = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "notes-database").allowMainThreadQueries().build();

        // debug/demo behavior: each time the app runs, a new pin is added to the db
        // when there are too many pins, clear the db and add again
        // todo remove in prod
        List<NotePin> notePins = noteDatabase.notePinDao().getAllPins();
        if (notePins.size() >= 5) {
            // clear the list
            for (NotePin dumpingPin : notePins) {
                noteDatabase.notePinDao().deletePin(dumpingPin);
            }
        }
        NoteEntryUtil.cleanupInvalidData();
    }

    private void addRandomPin() {
        Random random = new Random(System.currentTimeMillis());
        // a box inside Shatin
        // 22.3787,114.1930 -> 22.3907,114.2104
        double latitude = 22.3787 + random.nextDouble() * (22.3907 - 22.3787);
        double longitude = 114.1930 + random.nextDouble() * (114.2104 - 114.1930);
        NotePinUtil.MakeNewPinAtLocation(latitude, longitude);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        this.updateGoogleMapContents();

        this.tryMoveCameraToGpsLocation();

        mMap.setOnMarkerClickListener(this);
        mMap.setOnMapLongClickListener(latLng -> {
            // create a new pin
            NotePin pin = NotePinUtil.MakeNewPinAtLocation(latLng.latitude, latLng.longitude);
            drawNotePin(pin);
            Toast.makeText(getApplicationContext(), R.string.toast_pin_created, Toast.LENGTH_LONG).show();
        });
        initCameraPosition();
    }

    @Override
    public void onResume() {
        super.onResume();
        // when returning from another activity, sometimes onresume does not call onmapready, which is understandable
        this.updateGoogleMapContents();
    }

    private void updateGoogleMapContents() {
        if (this.mMap == null) {
            // cannot update
            return;
        }
        removeOldPinsIfExists();
        pairNotePins();
    }

    private void removeOldPinsIfExists() {
        for (int pinUid : notePinsMapping.keySet()) {
            Marker marker = notePinsMapping.get(pinUid);
            if (marker != null) {
                marker.remove();
            }
        }
        notePinsMapping.clear();
    }

    private void pairNotePins() {
        // pairs the notepins in the database with the markers on the map
        for (NotePin notePin : noteDatabase.notePinDao().getAllPins()) {
            LatLng latlng = new LatLng(notePin.latitude, notePin.longitude);
            Marker marker = mMap.addMarker(new MarkerOptions().position(latlng).title("" + notePin.uid));
            notePinsMapping.put(notePin.uid, marker);
        }
    }

    private void drawNotePin(NotePin notePin) {
        LatLng latlng = new LatLng(notePin.latitude, notePin.longitude);
        mMap.addMarker(new MarkerOptions().position(latlng).title("" + notePin.uid));
    }

    private void initCameraPosition() {
        // inits the camera position
        // try to move it to center of pin; if no pin, then move to hong kong
        LatLng hk = new LatLng(22.318736573752293, 114.16958960975587);
        LatLng center = notePinsMapping.isEmpty() ? hk : getCenterOfPins();
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(center, 13));
    }

    private LatLng getCenterOfPins() {
        List<NotePin> allNotes = MapsActivity.noteDatabase.notePinDao().getAllPins();

        List<Double> latitudes = allNotes.stream().map(notePin -> notePin.latitude)
                .collect(Collectors.toList());
        List<Double> longitudes = allNotes.stream().map(notePin -> notePin.longitude)
                .collect(Collectors.toList());

        double latCenter = (Collections.max(latitudes) + Collections.min(latitudes)) / 2;
        double lngCenter = (Collections.max(longitudes) + Collections.min(longitudes)) / 2;
        return new LatLng(latCenter, lngCenter);
    }

    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {
        Log.d("NotePin", "CLICK");
//        goToPinsActivity();

        // title = uid of pin
        int clickedPinUid = Integer.parseInt(Objects.requireNonNull(marker.getTitle()));
        this.goToPinsActivity(clickedPinUid);

        // As we will launch the notes activity immediately, return true to prevent the default
        // google map marker onclick behaviours (center marker and open info window).
        return true;
    }

    private void goToPinsActivity(int pinUid) {
        Intent intent = new Intent(this, PinsActivity.class);
        intent.putExtra("pinUid", pinUid);
        startActivity(intent);
    }

    private void showWelcomeDialog() {
        if (builder == null) {
            builder = new AlertDialog.Builder(this);
        }
        new HelpButtonOnClickListener(builder, R.string.welcome_to_app_title, R.string.welcome_to_app_descr).onClick(null);
    }

    private void tryMoveCameraToGpsLocation() {
        // obtains 1x GPS location and returns it
        // also handles the necessary flow properly
        if (mMap == null) {
            // not ready yet!
            return;
        }
        if (fusedLocationClient == null) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        }
        // ask permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // permission granted
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    // usually not null, but hey, who knows.
                    LatLng position = new LatLng(location.getLatitude(), location.getLongitude());
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 13));
                }
            });
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 5);
        }
    }
}