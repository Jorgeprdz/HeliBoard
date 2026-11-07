#!/usr/bin/env python3
"""Verify release shrinker retains entry points used only through SDK reflection."""
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[2]
seeds = (root / 'app/build/outputs/mapping/release/seeds.txt').read_text()
for name in ('com.google.mlkit.common.internal.CommonComponentRegistrar',
             'com.google.mlkit.vision.digitalink.internal.DigitalInkRecognitionRegistrar',
             'androidx.work.impl.WorkDatabase_Impl'):
    assert name + ': ' + name.rsplit('.', 1)[1] + '()' in seeds, 'Reflective constructor unprotected: ' + name
manifest = root / 'app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml'
app = ET.parse(manifest).getroot().find('application')
a = '{http://schemas.android.com/apk/res/android}'
assert app.get(a+'defaultToDeviceProtectedStorage') == 'true'
providers = {p.get(a+'name'): p for p in app.findall('provider')}
assert 'com.google.mlkit.common.internal.MlKitInitProvider' not in providers
startup = providers['androidx.startup.InitializationProvider']
assert all(m.get(a+'name') != 'androidx.work.WorkManagerInitializer' for m in startup.findall('meta-data'))
provider = providers['helium314.keyboard.latin.ai.handwriting.HandwritingRuntimeProvider']
assert provider.get(a+'directBootAware') == 'false'
assert provider.get(a+'exported') == 'false'
print('R26 release runtime PASS: reflective constructors protected; optional runtime CE-only; keyboard Direct Boot preserved')
