#!/usr/bin/env python3
"""Local macOS production signing. Password stays in Keychain, never in argv or a file.
No credentials are uploaded to CI. Requires owner-controlled encrypted keystore backup.
"""
import argparse
import ctypes as C
import os
from pathlib import Path
import secrets
import shutil
import subprocess

SERVICE = 'com.ufuq.dream.production-signing'
ACCOUNT = 'release-key-v1'
ALIAS = 'ufuq-production'
KEYSTORE = Path.home() / 'Library/Application Support/Ufuq Dream/Signing/ufuq-production.p12'

class Keychain:
    def __init__(self):
        self.cf = C.CDLL('/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation')
        self.sec = C.CDLL('/System/Library/Frameworks/Security.framework/Security')
        self.cf.CFStringCreateWithCString.argtypes = [C.c_void_p, C.c_char_p, C.c_uint32]; self.cf.CFStringCreateWithCString.restype = C.c_void_p
        self.cf.CFDictionaryCreateMutable.argtypes = [C.c_void_p, C.c_long, C.c_void_p, C.c_void_p]; self.cf.CFDictionaryCreateMutable.restype = C.c_void_p
        self.cf.CFDictionarySetValue.argtypes = [C.c_void_p, C.c_void_p, C.c_void_p]
        self.cf.CFDataCreate.argtypes = [C.c_void_p, C.c_void_p, C.c_long]; self.cf.CFDataCreate.restype = C.c_void_p
        self.cf.CFDataGetLength.argtypes = [C.c_void_p]; self.cf.CFDataGetLength.restype = C.c_long
        self.cf.CFDataGetBytePtr.argtypes = [C.c_void_p]; self.cf.CFDataGetBytePtr.restype = C.c_void_p
        self.cf.CFRelease.argtypes = [C.c_void_p]
        self.sec.SecItemAdd.argtypes = [C.c_void_p, C.c_void_p]; self.sec.SecItemAdd.restype = C.c_int
        self.sec.SecItemCopyMatching.argtypes = [C.c_void_p, C.c_void_p]; self.sec.SecItemCopyMatching.restype = C.c_int
    def constant(self, name): return C.c_void_p.in_dll(self.sec, name).value
    def query(self):
        # NULL callbacks: keep all CF values alive until after SecItem invocation.
        d = self.cf.CFDictionaryCreateMutable(None, 0, None, None)
        values = [self.cf.CFStringCreateWithCString(None, x.encode(), 0x08000100) for x in (SERVICE, ACCOUNT)]
        for key, value in [('kSecClass', self.constant('kSecClassGenericPassword')), ('kSecAttrService', values[0]), ('kSecAttrAccount', values[1])]:
            self.cf.CFDictionarySetValue(d, self.constant(key), value)
        return d, values
    def password(self, new=None):
        d, values = self.query(); result = C.c_void_p()
        try:
            if new is not None:
                raw = new.encode(); data = self.cf.CFDataCreate(None, raw, len(raw)); values.append(data)
                self.cf.CFDictionarySetValue(d, self.constant('kSecValueData'), data)
                status = self.sec.SecItemAdd(d, None)
                if status != 0: raise RuntimeError(f'Keychain insert failed ({status}); existing item is never replaced')
                return new
            true = C.c_void_p.in_dll(self.cf, 'kCFBooleanTrue').value
            self.cf.CFDictionarySetValue(d, self.constant('kSecReturnData'), true)
            status = self.sec.SecItemCopyMatching(d, C.byref(result))
            if status != 0: raise RuntimeError(f'Keychain access failed ({status}); unlock or approve access yourself')
            return C.string_at(self.cf.CFDataGetBytePtr(result), self.cf.CFDataGetLength(result)).decode()
        finally:
            if result.value: self.cf.CFRelease(result)
            self.cf.CFRelease(d)
            for value in values: self.cf.CFRelease(value)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--initialize', action='store_true', help='Owner-authorized one-time key creation; never rotates an existing identity')
    parser.add_argument('--input', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    java = Path(os.environ['JAVA_HOME']) / 'bin'
    env = os.environ.copy()
    keychain = Keychain()
    if args.initialize:
        if KEYSTORE.exists(): raise SystemExit('Key already exists; refusing to replace it')
        KEYSTORE.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        os.chmod(KEYSTORE.parent, 0o700)
        password = secrets.token_urlsafe(48)
        # Store first; if creation fails, never replace the saved credential automatically.
        keychain.password(password)
        env['UFUQ_SIGNING_PASSWORD'] = password
        subprocess.run([str(java / 'keytool'), '-genkeypair', '-keystore', str(KEYSTORE), '-storetype', 'PKCS12', '-alias', ALIAS, '-keyalg', 'RSA', '-keysize', '4096', '-validity', '10000', '-dname', 'CN=Ufuq Dream Production,O=Ufuq Dream,C=SA', '-storepass:env', 'UFUQ_SIGNING_PASSWORD', '-keypass:env', 'UFUQ_SIGNING_PASSWORD'], env=env, check=True)
        os.chmod(KEYSTORE, 0o600)
        backup = KEYSTORE.with_name('ufuq-production.backup.p12')
        shutil.copyfile(KEYSTORE, backup); os.chmod(backup, 0o600)
        print('Created encrypted production key and a local encrypted backup. Password is in macOS Keychain. An independent offline backup is still required.')
    else:
        if not args.input or not args.output: parser.error('--input and --output are required')
        env['UFUQ_SIGNING_PASSWORD'] = keychain.password()
        sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk')))
        tools = sdk / 'build-tools/34.0.0'
        args.output.parent.mkdir(parents=True, exist_ok=True)
        aligned = args.output.with_suffix('.aligned.tmp.apk')
        try:
            subprocess.run([str(tools / 'zipalign'), '-f', '-p', '4', str(args.input), str(aligned)], check=True)
            subprocess.run([str(tools / 'apksigner'), 'sign', '--ks', str(KEYSTORE), '--ks-key-alias', ALIAS, '--ks-pass', 'env:UFUQ_SIGNING_PASSWORD', '--key-pass', 'env:UFUQ_SIGNING_PASSWORD', '--out', str(args.output), str(aligned)], env=env, check=True)
            subprocess.run([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(args.output)], env=env, check=True)
        finally: aligned.unlink(missing_ok=True)
    env.pop('UFUQ_SIGNING_PASSWORD', None)

if __name__ == '__main__': main()
