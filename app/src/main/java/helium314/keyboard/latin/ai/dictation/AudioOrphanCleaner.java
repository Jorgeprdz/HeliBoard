// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.dictation;
import java.io.File;
/** Called once before this process starts its first recording, in its dedicated cache directory. */
public final class AudioOrphanCleaner {
 private AudioOrphanCleaner(){}
 public static int clean(File directory){if(directory==null)return 0;File[] files=directory.listFiles();if(files==null)return 0;int deleted=0;for(File file:files)if(file.isFile()&&file.getName().startsWith("heli_ai_audio_")&&file.getName().endsWith(".pcm")&&file.delete())deleted++;return deleted;}
}
