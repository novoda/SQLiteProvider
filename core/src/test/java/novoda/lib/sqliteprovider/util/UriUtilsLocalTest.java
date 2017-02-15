
package novoda.lib.sqliteprovider.util;

import android.net.Uri;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import junit.framework.TestCase;
import novoda.lib.sqliteprovider.RoboRunner;

@RunWith(RoboRunner.class)
public class UriUtilsLocalTest extends TestCase {

    @Test
    public void testGenericItemWithinCollectionQuery() {
        final Uri uri = Uri.parse("content://test.com/item/1");
        assertTrue(UriUtils.isNumberedEntryWithinCollection(uri));
        assertEquals("item", UriUtils.getItemDirID(uri));
    }

    @Test
    public void testChangingRootOfQuery() {
        Uri uri = Uri.parse("content://test.com/root/item/1");
        assertTrue(UriUtils.isItem("root", uri));
        assertEquals("item", UriUtils.getItemDirID("root", uri));
        uri = Uri.parse("content://test.com/root/root2/item/1");
        assertTrue(UriUtils.isItem("root/root2", uri));
        assertEquals("item", UriUtils.getItemDirID("root/root2", uri));
    }

    @Test
    public void testGettingRowIds() {
        Uri uri = Uri.parse("content://test.com");
        Map<String, String> result = UriUtils.mapIds(uri);
        assertTrue(result.size() == 0);

        uri = Uri.parse("content://test.com/parent");
        result = UriUtils.mapIds(uri);
        assertTrue(result.size() == 0);

        uri = Uri.parse("content://test.com/parent/1");
        result = UriUtils.mapIds(uri);
        assertEquals(result.size(),1);
        assertTrue(result.containsKey("parent"));
        assertEquals("1", result.get("parent"));

        uri = Uri.parse("content://test.com/parent/1/child");
        result = UriUtils.mapIds(uri);
        assertTrue(result.size() == 1);
        assertTrue(result.containsKey("parent"));
        assertEquals("1", result.get("parent"));

        uri = Uri.parse("content://test.com/parent/1/child/6");
        result = UriUtils.mapIds(uri);
        assertTrue(result.size() == 2);
        assertTrue(result.containsKey("parent") && result.containsKey("child"));
        assertEquals("1", result.get("parent"));
        assertEquals("6", result.get("child"));

        uri = Uri.parse("content://test.com/parent/0/child/6");
        result = UriUtils.mapIds(uri);
        assertTrue(result.size() == 2);
        assertTrue(result.containsKey("parent") && result.containsKey("child"));
        assertEquals("0", result.get("parent"));
        assertEquals("6", result.get("child"));
    }

    @Test
    public void testGettingParentDetails(){
        Uri uri = Uri.parse("content://test.com");
        assertEquals("",UriUtils.getParentColumnName(uri));
        assertEquals("",UriUtils.getParentId(uri));
        assertEquals(false,UriUtils.hasParent(uri));
        uri = Uri.parse("content://test.com/parent/1/child");
        assertEquals("parent",UriUtils.getParentColumnName(uri));
        assertEquals("1",UriUtils.getParentId(uri));
        assertEquals(true,UriUtils.hasParent(uri));
        uri = Uri.parse("content://test.com/parent/1/child/6");
        assertEquals("parent",UriUtils.getParentColumnName(uri));
        assertEquals("1",UriUtils.getParentId(uri));
        assertEquals(true,UriUtils.hasParent(uri));
        uri = Uri.parse("content://test.com/parent/1/child/6/subchild");
        assertEquals("child",UriUtils.getParentColumnName(uri));
        assertEquals("6",UriUtils.getParentId(uri));
        assertEquals(true,UriUtils.hasParent(uri));
        uri = Uri.parse("content://test.com/parent/1/child/6/subchild/3");
        assertEquals("child",UriUtils.getParentColumnName(uri));
        assertEquals("6",UriUtils.getParentId(uri));
        assertEquals(true,UriUtils.hasParent(uri));
    }
}
