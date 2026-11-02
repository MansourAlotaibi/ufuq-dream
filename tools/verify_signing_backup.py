#!/usr/bin/env python3
"""Owner-run recovery check: an independent encrypted backup AND a separately recovered password.
Never use the live Keychain password for this test. Password is read from a private terminal prompt.
The tool verifies certificate identity, not the physical independence/offline status of a drive.
"""
import argparse,base64,getpass,hashlib,json,os,re,subprocess,tempfile,sys
from pathlib import Path
from datetime import datetime,timezone
EXPECTED='de13674be215040bafb029fe637a74e9e981accfa5a763bba62ad99dbcda5169'
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('backup',type=Path);p.add_argument('--report',type=Path,required=True);p.add_argument('--confirm-independent',action='store_true');a=p.parse_args()
    if not a.confirm_independent: p.error('Owner must confirm independent/offline storage and a separately recovered password')
    primary=Path.home()/'Library/Application Support/Ufuq Dream/Signing/ufuq-production.p12'
    if not a.backup.is_file() or (primary.exists() and os.path.samefile(a.backup,primary)):p.error('Choose the independent backup, not the live keystore')
    if not sys.stdin.isatty():p.error('Run in a private interactive terminal; never pipe a password')
    secret=getpass.getpass('Separately recovered backup password (not logged): ')
    env=os.environ.copy();env['UFUQ_RECOVERY_PASSWORD']=secret
    java=Path(env['JAVA_HOME'])/'bin/keytool'
    # Work on a temporary copy; the original backup is read-only to this workflow.
    with tempfile.TemporaryDirectory(prefix='ufuq-recovery-') as directory:
        copy=Path(directory)/'backup.p12';copy.write_bytes(a.backup.read_bytes());copy.chmod(0o600)
        result=subprocess.run([str(java),'-list','-rfc','-keystore',str(copy),'-storepass:env','UFUQ_RECOVERY_PASSWORD','-alias','ufuq-production'],env=env,capture_output=True,text=True)
        proof=Path(directory)/'recovery-proof.csr'
        signing=subprocess.run([str(java),'-certreq','-keystore',str(copy),'-storepass:env','UFUQ_RECOVERY_PASSWORD','-keypass:env','UFUQ_RECOVERY_PASSWORD','-alias','ufuq-production','-file',str(proof)],env=env,capture_output=True,text=True)
        verified=subprocess.run(['openssl','req','-in',str(proof),'-noout','-verify'],capture_output=True) if signing.returncode==0 else None
    env.pop('UFUQ_RECOVERY_PASSWORD',None);secret=None
    if verified is None or verified.returncode:raise SystemExit('Private-key signing recovery failed; no tool output or secrets logged')
    if result.returncode:raise SystemExit('Recovery failed. No password or tool output was logged; check your private backup/password.')
    match=re.search(r'-----BEGIN CERTIFICATE-----\s*(.*?)\s*-----END CERTIFICATE-----',result.stdout,re.S)
    if not match:raise SystemExit('No certificate recovered')
    fingerprint=hashlib.sha256(base64.b64decode(match.group(1))).hexdigest()
    if fingerprint!=EXPECTED:raise SystemExit('Wrong signing identity; do not replace the production key')
    report={'checkedAt':datetime.now(timezone.utc).isoformat(),'certificateSha256':fingerprint,'recoveredPasswordOpenedIndependentCopy':True,'privateKeySignedRecoveryProof':True,'physicalIndependence':'owner-attested; not technically proven by this tool','originalBackupModified':False,'secretsLogged':False}
    a.report.write_text(json.dumps(report,indent=2)+'\n');a.report.chmod(0o600)
    print('PASS: recovered password opens a copied backup with the production certificate. Keep the private report offline.')
if __name__=='__main__':main()
