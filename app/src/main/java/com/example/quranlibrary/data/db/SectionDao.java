package com.example.quranlibrary.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * DAO للأقسام. يستخدم LiveData للاستعلامات التفاعلية،
 * بحيث تُحدَّث الواجهة تلقائيًا عند أي تغيير في قاعدة البيانات.
 */
@Dao
public interface SectionDao {

    @Query("SELECT * FROM sections ORDER BY sort_order ASC, id ASC")
    LiveData<List<Section>> getAllSections();

    @Query("SELECT * FROM sections WHERE id = :id LIMIT 1")
    Section getSectionById(int id);

    @Query("SELECT COUNT(*) FROM sections")
    int getCount();

    @Insert
    long insert(Section section);

    @Insert
    void insertAll(List<Section> sections);

    @Update
    void update(Section section);

    @Delete
    void delete(Section section);
}
