#!/usr/bin/env python3
"""Verify signed release identity and bundled media against the checked-out source."""
import argparse, hashlib, json, os, re, subprocess, zipfile
from pathlib import Path

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('apk',type=Path);p.add_argument('--commit',required=True);p.add_argument('--certificate',required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args(); assert re.fullmatch('[0-9a-f]{40}',a.commit)
    sdk=Path(os.environ['ANDROID_HOME'])/'build-tools/34.0.0'
    cert=subprocess.check_output([str(sdk/'apksigner'),'verify','--verbose','--print-certs',str(a.apk)],text=True)
    assert a.certificate.lower() in cert.lower(), 'Unexpected signing certificate'
    assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in cert
    badging=subprocess.check_output([str(sdk/'aapt'),'dump','badging',str(a.apk)],text=True)
    assert "name='com.ufuq.dream'" in badging and "versionCode='15'" in badging and "versionName='0.8.3-rc1'" in badging
    assert "sdkVersion:'28'" in badging and "targetSdkVersion:'34'" in badging
    assert 'application-debuggable' not in badging and 'uses-permission:' not in badging
    manifest=subprocess.check_output([str(sdk/'aapt'),'dump','xmltree',str(a.apk),'AndroidManifest.xml'],text=True)
    for required in ['UfuqDreamService','android.permission.BIND_DREAM_SERVICE','android.service.dreams.DreamService','com.ufuq.dream.SOURCE_COMMIT']:
        assert required in manifest,required
    resources=subprocess.check_output([str(sdk/'aapt'),'dump','--values','resources',str(a.apk)],text=True)
    assert a.commit in resources, 'APK source marker mismatch'
    root=Path(__file__).resolve().parents[1]
    photos=json.loads((root/'app/src/main/assets/photo-provenance.json').read_text())
    with zipfile.ZipFile(a.apk) as z:
        names=z.namelist()
        assert not any(n.lower().endswith(('.mp4','.webm','.jks','.keystore','.p12','.pem','.key')) for n in names)
        assert len([n for n in names if n.startswith('assets/cinematic/') and n.endswith('.jpg')])==13
        for item in photos: assert hashlib.sha256(z.read('assets/cinematic/'+item['fileName'])).hexdigest()==item['sha256']
        assert z.read('assets/photo-provenance.json')==(root/'app/src/main/assets/photo-provenance.json').read_bytes()
        assert 'assets/licenses/APACHE-2.0.txt' in names and 'assets/licenses/UFUQ-MIT.txt' in names
    result={'apk':a.apk.name,'bytes':a.apk.stat().st_size,'sha256':hashlib.sha256(a.apk.read_bytes()).hexdigest(),'package':'com.ufuq.dream','versionName':'0.8.3-rc1','versionCode':15,'minSdk':28,'targetSdk':34,'sourceCommit':a.commit,'certificateSha256':a.certificate,'signatureVerified':True,'debuggable':False,'bundledPhotos':13,'bundledVideos':0,'photoHashesMatchSource':True,'checks':'Identity, v2 signature, manifest, source marker, licensed photo hashes and no private media/key files'}
    a.output.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
if __name__=='__main__': main()
