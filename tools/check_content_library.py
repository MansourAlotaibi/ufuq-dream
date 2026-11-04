#!/usr/bin/env python3
"""Offline signed text-channel integrity gate. Does not certify religious authenticity or rights."""
import base64,hashlib,json,subprocess,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
    raw=(ROOT/'content/library.json').read_bytes()
    assert raw==(ROOT/'app/src/main/assets/reviewed-library.json').read_bytes()
    assert 0<len(raw)<=524288
    envelope=json.loads(raw);assert envelope['formatVersion']==1
    payload=base64.b64decode(envelope['payload'],validate=True)
    signature=base64.b64decode(envelope['signature'],validate=True)
    assert payload==(ROOT/'content/reviewed-payload.json').read_bytes()
    cert=ROOT/'app/src/main/assets/content-verifier-cert.der'
    assert hashlib.sha256(cert.read_bytes()).hexdigest()=='de13674be215040bafb029fe637a74e9e981accfa5a763bba62ad99dbcda5169'
    public=subprocess.check_output(['openssl','x509','-inform','DER','-in',str(cert),'-pubkey','-noout'])
    with tempfile.TemporaryDirectory(prefix='ufuq-public-content-') as directory:
        work=Path(directory);(work/'public.pem').write_bytes(public);(work/'payload').write_bytes(payload);(work/'signature').write_bytes(signature)
        result=subprocess.run(['openssl','dgst','-sha256','-verify',str(work/'public.pem'),'-signature',str(work/'signature'),str(work/'payload')],capture_output=True)
        assert result.returncode==0,'Signature mismatch'
    body=json.loads(payload);assert body['schema']==1 and body['version']>0
    assert body['reviewedBy']=='Ufuq Dream maintainers'
    assert 3<=len(body['entries'])<=128
    assert {e['kind'] for e in body['entries']}=={'quran','hadith','dhikr'}
    for e in body['entries']:
        assert 0<len(e['text'])<=4096 and e['source'] and e['attribution'] and e['notice']
        assert e['sourceUrl'].startswith('https://')
    print('PASS: bounded signed content channel; public certificate pin; exact bundled bytes; preserved source metadata')
if __name__=='__main__':main()
