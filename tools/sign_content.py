#!/usr/bin/env python3
"""Sign a manually reviewed text-data payload with the existing release identity; never create keys."""
import argparse,json,os,subprocess
from pathlib import Path
from sign_release import Keychain,KEYSTORE,ALIAS

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--payload',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--certificate',type=Path,required=True)
    args=parser.parse_args()
    body=json.loads(args.payload.read_text())
    assert body['schema']==1 and body['version']>0 and body['reviewedBy']=='Ufuq Dream maintainers'
    assert 3<=len(body['entries'])<=128
    # A maintainer must independently review text, sources, display fit and redistribution rights first.
    env=os.environ.copy();env['UFUQ_SIGNING_PASSWORD']=Keychain().password()
    args.output.parent.mkdir(parents=True,exist_ok=True);args.certificate.parent.mkdir(parents=True,exist_ok=True)
    try:
        subprocess.run([str(Path(env['JAVA_HOME'])/'bin/java'),'--source','17',str(Path(__file__).with_name('SignContent.java')),str(KEYSTORE),ALIAS,str(args.payload),str(args.output),str(args.certificate)],env=env,check=True,capture_output=True)
    finally:env.pop('UFUQ_SIGNING_PASSWORD',None)
    print('Signed reviewed content using the existing identity. Public certificate only; no secrets exported.')
if __name__=='__main__':main()
