package helium314.keyboard.latin.gifs;
import org.junit.Test;
import static org.junit.Assert.*;
public class GifPanelGeometryTest {
    @org.junit.Test public void fallbackReservesOnlyNavigationThatOverlapsThePanel() {
        org.junit.Assert.assertEquals(63,GifPanelGeometry.safeBottom(904,904,63,0));
        org.junit.Assert.assertEquals(0,GifPanelGeometry.safeBottom(1840,1966,63,0));
        org.junit.Assert.assertEquals(39,GifPanelGeometry.safeBottom(880,904,63,0));
        org.junit.Assert.assertEquals(24,GifPanelGeometry.safeBottom(300,904,63,24));
    }
 @Test public void expandsOnlyWhenUsefulSpaceExists(){assertEquals(350,GifPanelGeometry.browseHeight(280,800));assertEquals(300,GifPanelGeometry.browseHeight(280,500));assertEquals(280,GifPanelGeometry.browseHeight(280,400));}
 @Test public void legacyUnknownWindowStillHasPositiveNormalDerivedHeight(){assertEquals(350,GifPanelGeometry.browseHeight(280,0));assertTrue(GifPanelGeometry.browseHeight(0,800)>0);}
 @Test public void portraitAndLandscapeFitTheirOwnRatios(){assertEquals(240,GifPanelGeometry.previewHeight(180,100,200,240));assertEquals(90,GifPanelGeometry.previewHeight(180,200,100,240));assertEquals(180,GifPanelGeometry.previewHeight(180,0,0,240));}
 @Test public void malformedDimensionsCannotOverflowOrCreateHugeTiles(){assertTrue(GifPanelGeometry.previewHeight(180,1,Integer.MAX_VALUE,240)<=240);assertTrue(GifPanelGeometry.previewHeight(0,-1,-1,0)>0);}
}
