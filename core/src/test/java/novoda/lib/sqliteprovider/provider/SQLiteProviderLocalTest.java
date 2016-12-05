package novoda.lib.sqliteprovider.provider;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

import java.io.IOException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowContentUris;

import novoda.lib.sqliteprovider.RoboRunner;
import novoda.lib.sqliteprovider.sqlite.ExtendedSQLiteOpenHelper;
import novoda.lib.sqliteprovider.sqlite.ExtendedSQLiteQueryBuilder;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.hasItems;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

@RunWith(RoboRunner.class)
@Config(shadows = {ShadowContentUris.class}, manifest = "src/test/resources/AndroidManifest.xml")
public class SQLiteProviderLocalTest {

    private SQLiteProviderImpl provider;

    @Mock
    private SQLiteDatabase db;
    @Mock
    private ExtendedSQLiteQueryBuilder builder;
    @Mock
    private Cursor mockCursor;

    private int notifyChangeCounter;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);

        stub(builder.query((SQLiteDatabase) anyObject(), (String[]) anyObject(), anyString(), (String[]) anyObject(), anyString(),
                anyString(), anyString(), anyString())).toReturn(mockCursor);
        stub(db.rawQuery(anyString(), (String[]) anyObject())).toReturn(mockCursor);

        provider = new SQLiteProviderImpl();
        provider.onCreate();
    }

    @Test
    public void testSelectTableFromUri() {
        // Simple directory listing against table test
        query("test.com/test");
        verify(builder).setTables("test");
    }

    @Test
    public void testSelectTableItemFromUri() {
        // Single item against table test
        query("test.com/test/1");
        verify(builder).setTables("test");
        verify(builder).appendWhere("_id=1");
    }

    @Test
    public void testSelectTableForChildren() {
        query("test.com/parent/1/children");
        verify(builder).setTables("children");
        verify(builder).appendWhere("parent_id='1'");
    }

    @Test
    public void testInsertAgainstCorrectTable() {
        when(db.insert(anyString(), anyString(), (ContentValues) anyObject())).thenReturn(2L);
        ContentValues cv = new ContentValues();
        cv.put("column1", "2erverver");

        insert("test.com/table1", cv);
        insert("test.com/parent/1/child", cv);

        verify(db).insert(eq("table1"), anyString(), (ContentValues) anyObject());
        verify(db).insert(eq("child"), anyString(), (ContentValues) anyObject());
    }

    @Test
    public void testInsertAgainstOneToManyShouldInputCorrectParam() {
        when(db.insert(anyString(), anyString(), (ContentValues) anyObject())).thenReturn(2L);
        ContentValues values = new ContentValues();
        values.put("test", "test");
        values.put("parent_id", "1");

        insert("test.com/parent/1/children", values);

        verify(db).insert(eq("children"), anyString(), (ContentValues) anyObject());
    }

    @Test
    public void testDeleteFromSingleTable() {
        when(db.delete(anyString(), anyString(), (String[]) anyObject())).thenReturn(0);

        delete("test.com/parent", null, null);

        verify(db).delete(eq("parent"), anyString(), (String[]) anyObject());
    }

    @Test
    public void testUpdateFromSingleTable() {
        when(db.update(anyString(), (ContentValues) anyObject(), anyString(), (String[]) anyObject())).thenReturn(2);
        ContentValues values = new ContentValues();
        values.put("test", "test");

        update("test.com/parent", values, null, null);

        verify(db).update(eq("parent"), (ContentValues) anyObject(), anyString(), (String[]) anyObject());
    }

    @Test
    public void testUpdateFromSOneToMany() {
        when(db.update(anyString(), (ContentValues) anyObject(), anyString(), (String[]) anyObject())).thenReturn(2);
        ContentValues values = new ContentValues();
        values.put("test", "test");

        update("test.com/parent/1/child", values, null, null);

        verify(db).update(eq("child"), (ContentValues) anyObject(), anyString(), (String[]) anyObject());
    }

    @Test
    public void testDeleteFromOneToManyTable() {
        when(db.delete(anyString(), anyString(), (String[]) anyObject())).thenReturn(0);

        delete("test.com/parent/1/child", null, null);

        verify(db).delete(eq("child"), anyString(), (String[]) anyObject());
    }

    @Test
    public void testGroupByQuery() {
        query("test.com/table?groupBy=table");

        verify(builder).query((SQLiteDatabase) anyObject(), (String[]) anyObject(), anyString(), (String[]) anyObject(), eq("table"),
                anyString(), anyString(), anyString());
    }

    @Test
    public void testHavingQuery() {
        query("test.com/table?having=g");

        verify(builder).query((SQLiteDatabase) anyObject(), (String[]) anyObject(), anyString(), (String[]) anyObject(), anyString(),
                eq("g"), anyString(), anyString());
    }

    @Test
    public void testLimitQuery() {
        query("test.com/table?limit=100");

        verify(builder).query((SQLiteDatabase) anyObject(), (String[]) anyObject(), anyString(), (String[]) anyObject(), anyString(),
                anyString(), anyString(), eq("100"));
    }

    @Test
    public void testDistinct() {
        query("test.com/table?distinct=true");

        verify(builder).setDistinct(true);
    }

    @Test
    public void testNotDistinct() {
        query("test.com/table?distinct=false");

        verify(builder).setDistinct(false);
    }

    @Test
    public void testExpand() {
        when(builder.getTables()).thenReturn("table");
        Uri uri = Uri.parse("content://test.com/?q=1&q=2");
        List<String> p = uri.getQueryParameters("q");
        assertThat(p, hasItems("1", "2"));

        query("test.com/table?expand=childs");
        verify(builder).setTables("table");
        // verify(builder).setTables("table INNER JOIN childs ON table.child_id=childs._id");
    }

    @Test
    public void testBulkInsertInsertsCorrectly() {
        when(db.insert(anyString(), anyString(), (ContentValues) anyObject())).thenReturn(2L);
        int bulkSize = 100;
        ContentValues[] bulkToInsert = createContentValuesArray(bulkSize);

        bulkInsert("test.com/table1", bulkToInsert);

        verify(db, times(bulkSize)).insert(eq("table1"), anyString(), (ContentValues) anyObject());
    }

    @Test
    public void testBulkInsertNotifiesOnlyOnce() {
        when(db.insert(anyString(), anyString(), (ContentValues) anyObject())).thenReturn(2L);
        int bulkSize = 100;
        ContentValues[] bulkToInsert = createContentValuesArray(bulkSize);

        bulkInsert("test.com/table1", bulkToInsert);

        assertThat(notifyChangeCounter, is(1));
    }

    @Test
    public void testInsertNotifiesAsManyChangesAsInserts() {
        when(db.insert(anyString(), anyString(), (ContentValues) anyObject())).thenReturn(2L);
        int insertSize = 100;
        ContentValues[] inserts = createContentValuesArray(insertSize);

        for (ContentValues insert : inserts) {
            insert("test.com/table1", insert);
        }

        assertThat(notifyChangeCounter, is(insertSize));
    }

    @Test
    public void testUpdateNotifiesAsManyChangesAsUpdates() {
        when(db.update(anyString(), (ContentValues) anyObject(), anyString(), any(String[].class))).thenReturn(1);
        int updateSize = 100;
        ContentValues[] updates = createContentValuesArray(updateSize);

        for (ContentValues update : updates) {
            update("test.com/table1", update, null, null);
        }

        assertThat(notifyChangeCounter, is(updateSize));
    }

    @Test
    public void testDeleteNotifiesAsManyChangesAsDeletes() {
        when(db.delete(anyString(), anyString(), (String[]) anyObject())).thenReturn(0);
        int deleteSize = 100;

        for (int i = 0; i < deleteSize; i++) {
            delete("test.com/table1", null, null);
        }

        assertThat(notifyChangeCounter, is(deleteSize));

    }

    @Test
    public void testBulkInsertDoesYieldByDefault() {
        when(db.insert(anyString(), anyString(), (ContentValues) anyObject())).thenReturn(2L);
        int bulkSize = 100;
        ContentValues[] bulkToInsert = createContentValuesArray(bulkSize);

        bulkInsert("test.com/table1", bulkToInsert);

        verify(db, times(bulkSize)).yieldIfContendedSafely();
    }

    @Test
    public void testWhenSpecifyingAllowYieldQueryParameterAsTrueThanBulkInsertDoesYield() {
        when(db.insert(anyString(), anyString(), (ContentValues) anyObject())).thenReturn(2L);
        int bulkSize = 100;
        ContentValues[] bulkToInsert = createContentValuesArray(bulkSize);

        bulkInsert("test.com/table1?allowYield=true", bulkToInsert);

        verify(db, times(bulkSize)).yieldIfContendedSafely();
    }

    @Test
    public void testWhenSpecifyingAllowYieldQueryParameterAsFalseThanBulkInsertDoesNotYield() {
        when(db.insert(anyString(), anyString(), (ContentValues) anyObject())).thenReturn(2L);
        int bulkSize = 100;
        ContentValues[] bulkToInsert = createContentValuesArray(bulkSize);

        bulkInsert("test.com/table1?allowYield=false", bulkToInsert);

        verify(db, never()).yieldIfContendedSafely();
    }

    @Test
    public void testProvidedNotificationUriSetCorrectly() {
        query("test.com/view1");
        verify(mockCursor).setNotificationUri((ContentResolver) anyObject(), eq(Uri.parse("content://test.com/table1")));
    }

    @Implements(ContentUris.class)
    static class ShadowContentUris {

        @SuppressWarnings("unused")
        @Implementation
        public static Uri withAppendedId(Uri uri, long id) {
            return Uri.parse("content://test.com");
        }
    }

    private void update(String uri, ContentValues initialValues, String selection, String[] selectionArgs) {
        provider.update(Uri.parse("content://" + uri), initialValues, selection, selectionArgs);
    }

    private void delete(String uri, String where, String[] whereArgs) {
        provider.delete(Uri.parse("content://" + uri), where, whereArgs);
    }

    private void insert(String uri, ContentValues cv) {
        provider.insert(Uri.parse("content://" + uri), cv);
    }

    private void bulkInsert(String uri, ContentValues[] cv) {
        provider.bulkInsert(Uri.parse("content://" + uri), cv);
    }

    private void query(String uri) {
        provider.query(Uri.parse("content://" + uri), null, null, null, null);
    }

    private ContentValues[] createContentValuesArray(int size) {
        ContentValues[] bulkToInsert = new ContentValues[size];
        ContentValues contentValues = new ContentValues();
        contentValues.put("test", "test");
        for (int i = 0; i < size; i++) {
            bulkToInsert[i] = contentValues;
        }
        return bulkToInsert;
    }

    public class SQLiteProviderImpl extends SQLiteContentProviderImpl {
        @Override
        protected SQLiteDatabase getReadableDatabase() {
            return db;
        }

        @Override
        protected SQLiteDatabase getWritableDatabase() {
            return db;
        }

        @Override
        protected ExtendedSQLiteQueryBuilder getSQLiteQueryBuilder() {
            return builder;
        }

        @Override
        protected ExtendedSQLiteOpenHelper getDatabaseHelper(Context context) {
            try {
                return new ExtendedSQLiteOpenHelper(getContext()) {
                    @Override
                    public void onCreate(SQLiteDatabase db) {
                        // dont do a migrate
                    }

                    @Override
                    public synchronized SQLiteDatabase getReadableDatabase() {
                        return db;
                    }

                    @Override
                    public synchronized SQLiteDatabase getWritableDatabase() {
                        return db;
                    }
                };
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public void notifyUriChange(Uri uri) {
            notifyChangeCounter++;
        }

        @Override
        protected Uri getNotificationUri(Uri uri) {
            return Uri.parse("content://test.com/table1");
        }
    }
}
