package edu.cuhk.mapnotes.datatypes;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface NotePinDao {
    // todo add more queries as needed

    @Query("SELECT * FROM note_pin")
    List<NotePin> getAllPins();

    @Query("SELECT * FROM note_pin WHERE latitude BETWEEN :fromLatitude AND :toLatitude AND longitude BETWEEN :fromLongitude AND :toLongitude")
    List<NotePin> getAllPinsInArea(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude);

    @Insert
    void insertPins(NotePin... pins);

    @Delete
    void deletePin(NotePin pin);
}