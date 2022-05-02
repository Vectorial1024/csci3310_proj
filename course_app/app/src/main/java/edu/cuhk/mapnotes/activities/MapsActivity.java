package edu.cuhk.mapnotes.activities;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.room.Room;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import edu.cuhk.mapnotes.R;
import edu.cuhk.mapnotes.databinding.ActivityMapsBinding;
import edu.cuhk.mapnotes.datatypes.AppDatabase;
import edu.cuhk.mapnotes.datatypes.NotePin;

public class MapsActivity extends FragmentActivity
        implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private List<NotePin> notePins = new ArrayList<>();

    private GoogleMap mMap;
    private ActivityMapsBinding binding;

    public static AppDatabase noteDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Obtain the SupportMapFragment and get notified when the map is ready to be used
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // start the Rooms database
        // todo the allowMainThreadQueries is unsafe
        noteDatabase = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "notes-database").allowMainThreadQueries().build();

        // debug/demo behavior: each time the app runs, a new pin is added to the db
        // when there are too many pins, clear the db and add again
        List<NotePin> notePins = noteDatabase.notePinDao().getAllPins();
        if (notePins.size() >= 5) {
            // clear the list
            for (NotePin dumpingPin : notePins) {
                noteDatabase.notePinDao().deletePin(dumpingPin);
            }
        }
        // add 1 random pin to map
        Random random = new Random(System.currentTimeMillis());
        NotePin randomPin = new NotePin();
        randomPin.pinName = "Random Pin";
        // a box inside Shatin
        // 22.3787,114.1930 -> 22.3907,114.2104
        randomPin.latitude = 22.3787 + random.nextDouble() * (22.3907 - 22.3787);
        randomPin.longitude = 114.1930 + random.nextDouble() * (114.2104 - 114.1930);
        randomPin.pinDescription = "Is Random";
        noteDatabase.notePinDao().insertPins(randomPin);

        loadNotePins();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        drawNotePins();

        mMap.setOnMarkerClickListener(this);
        initCamera();
    }

    private void drawNotePins() {
        for (NotePin notePin : notePins) {
            drawNotePin(notePin);
        }
    }

    private void drawNotePin(NotePin notePin) {
        LatLng latlng = new LatLng(notePin.latitude, notePin.longitude);
        mMap.addMarker(new MarkerOptions().position(latlng).title(notePin.pinName));
    }

    private void initCamera() {
        LatLng center = getCenterOfPins(notePins);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(center));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(center, 13));
    }

    private void loadNotePins() {
        prepareTestPins();
    }

    private void prepareTestPins() {
        // Generate some pins for testing
        NotePin pin1 = new NotePin();
        pin1.pinName = "Shatin";
        pin1.pinDescription = "My first notes";
        pin1.latitude = 22.38670278162099;
        pin1.longitude = 114.1954333616334;
        notePins.add(pin1);

        NotePin pin2 = new NotePin();
        pin2.pinName = "Shek Mun";
        pin2.pinDescription = "My second notes";
        pin2.latitude = 22.386583738555515;
        pin2.longitude = 114.20890877918538;
        notePins.add(pin2);
    }

    private LatLng getCenterOfPins(List<NotePin> notePins) {
        List<Double> latitudes = notePins.stream().map(notePin -> notePin.latitude)
                .collect(Collectors.toList());
        List<Double> longitudes = notePins.stream().map(notePin -> notePin.longitude)
                .collect(Collectors.toList());

        double latCenter = (Collections.max(latitudes) + Collections.min(latitudes)) / 2;
        double lngCenter = (Collections.max(longitudes) + Collections.min(longitudes)) / 2;
        return new LatLng(latCenter, lngCenter);
    }

    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {
        Log.d("NotePin", "CLICK");
        goToPinsActivity();

        // As we will launch the notes activity immediately, return true to prevent the default
        // google map marker onclick behaviours (center marker and open info window).
        return true;
    }

    private void goToPinsActivity() {
        Intent intent = new Intent(this, PinsActivity.class);
        startActivity(intent);
    }
}