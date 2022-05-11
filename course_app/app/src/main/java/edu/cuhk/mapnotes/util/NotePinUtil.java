package edu.cuhk.mapnotes.util;

import com.google.android.gms.maps.model.LatLng;

import java.text.DecimalFormat;
import java.util.List;

import edu.cuhk.mapnotes.activities.MapsActivity;
import edu.cuhk.mapnotes.datatypes.NoteEntry;
import edu.cuhk.mapnotes.datatypes.NotePin;

public class NotePinUtil {
    private static final DecimalFormat df = new DecimalFormat("0.00000");

    public static NotePin MakeNewPinAtLocation(double latitude, double longitude) {
        NotePin newPin = new NotePin();
        newPin.pinName = "New Location";
        newPin.latitude = latitude;
        newPin.longitude = longitude;
        List<Long> insertionResponse = MapsActivity.noteDatabase.notePinDao().insertPins(newPin);
        // immediately write back to the object here, others may want to use the UID
        newPin.uid = (int) ((long) insertionResponse.get(0));

        ensurePinHasSomeNotes(newPin);
        return newPin;
    }

    public static void ensurePinHasSomeNotes(NotePin pin) {
        // if pin has no notes, then add a default note to it
        List<NoteEntry> notesOfPin = MapsActivity.noteDatabase.noteEntryDao().getAllNoteEntries(pin.uid);
        if (notesOfPin.isEmpty()) {
            NoteEntry noteEntry = new NoteEntry();
            String roundedLat = df.format(pin.latitude);
            String roundedLng = df.format(pin.longitude);
            noteEntry.noteTitle = "First Note of This Pin";
            noteEntry.noteText = "The location (" + roundedLat + ", " + roundedLng + ") is now marked. Perhaps there is something important here to be noted.";
            noteEntry.pinUid = pin.uid;
            MapsActivity.noteDatabase.noteEntryDao().insertNoteEntries(noteEntry);
        }
    }
}
