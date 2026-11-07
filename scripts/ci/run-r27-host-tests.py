#!/usr/bin/env python3
"""Essential pure-Java regression suite, including provider transport tests; no API key."""
from pathlib import Path
import os, subprocess, tempfile

root=Path(__file__).resolve().parents[2]
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1'
def jar(group,name,version):
    return str(next((cache/group/name/version).glob('*/*.jar')))
classpath=os.pathsep.join([jar('junit','junit','4.13.2'),jar('org.hamcrest','hamcrest-core','1.3'),jar('org.json','json','20240303')])
jdk=Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else None
java=str(jdk/'java') if jdk else 'java'
javac=str(jdk/'javac') if jdk else 'javac'
base='app/src/main/java/helium314/keyboard/'
main=['latin/ai/SensitiveInputPolicy.java','latin/ai/AiConnectionStatus.java','latin/ai/provider/GeminiOperation.java','latin/ai/provider/GeminiProvider.java','latin/ai/dictation/VoiceEditPolicy.java','latin/gifs/GifPolicy.java','latin/gifs/GifItem.java','latin/gifs/GifPanelGeometry.java']
tests=['latin/ai/AiConnectionStatusTest.java','latin/ai/dictation/VoiceEditPolicyTest.java','latin/gifs/GifPanelGeometryTest.java','latin/gifs/GifItemMetadataTest.java','latin/ai/provider/GeminiProviderTest.java']
classes=['helium314.keyboard.latin.ai.AiConnectionStatusTest','helium314.keyboard.latin.ai.dictation.VoiceEditPolicyTest','helium314.keyboard.latin.gifs.GifPanelGeometryTest','helium314.keyboard.latin.gifs.GifItemMetadataTest','helium314.keyboard.latin.ai.provider.GeminiProviderHostTest']
with tempfile.TemporaryDirectory(prefix='tactic-r27-host-') as tmp:
    wrapper=Path(tmp)/'GeminiProviderHostTest.java'
    wrapper.write_text('package helium314.keyboard.latin.ai.provider; public class GeminiProviderHostTest extends GeminiProviderTest {}')
    files=[str(root/base/f) for f in main]+[str(root/'app/src/test/java/helium314/keyboard'/f) for f in tests]+[str(wrapper)]
    subprocess.run([javac,'-cp',classpath,'-d',tmp,*files],check=True)
    subprocess.run([java,'-cp',tmp+os.pathsep+classpath,'org.junit.runner.JUnitCore',*classes],check=True)
