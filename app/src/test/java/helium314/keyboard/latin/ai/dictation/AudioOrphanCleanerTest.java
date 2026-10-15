// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.dictation;
import java.io.*;import java.nio.file.*;import org.junit.Test;import static org.junit.Assert.*;
public class AudioOrphanCleanerTest {
 private int clean(File directory){try{return (int)Class.forName("helium314.keyboard.latin.ai.dictation.AudioOrphanCleaner").getMethod("clean",File.class).invoke(null,directory);}catch(Exception e){throw new AssertionError("Orphan audio cleanup missing",e);}}
 @Test public void removesOnlyOwnedRecordings() throws Exception {Path root=Files.createTempDirectory("ai-audio-test");try{File own=root.resolve("heli_ai_audio_old.pcm").toFile(),other=root.resolve("unrelated.pcm").toFile();assertTrue(own.createNewFile());assertTrue(other.createNewFile());assertEquals(1,clean(root.toFile()));assertFalse(own.exists());assertTrue(other.exists());}finally{for(File f:root.toFile().listFiles())f.delete();root.toFile().delete();}}
 @Test public void neverRecursesIntoDirectories() throws Exception {Path root=Files.createTempDirectory("ai-audio-test");try{File nested=root.resolve("heli_ai_audio_nested.pcm").toFile();assertTrue(nested.mkdir());assertEquals(0,clean(root.toFile()));assertTrue(nested.isDirectory());nested.delete();}finally{root.toFile().delete();}}
}
