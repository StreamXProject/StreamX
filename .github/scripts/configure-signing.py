#!/usr/bin/env python3
import base64,os,pathlib
root=pathlib.Path(__file__).resolve().parents[2]
keys=['ANDROID_KEYSTORE_BASE64','ANDROID_KEYSTORE_PASSWORD','ANDROID_KEY_ALIAS','ANDROID_KEY_PASSWORD']
missing=[key for key in keys if not os.environ.get(key)]
if missing:raise SystemExit('Configure repository Actions secrets: '+', '.join(missing))
key=root/'release-signing.jks'
key.write_bytes(base64.b64decode(os.environ[keys[0]],validate=True));key.chmod(0o600)
def escape(value):return value.replace('\\','\\\\').replace('\n','\\n').replace('\r','\\r')
props={'storeFile':str(key),'storePassword':os.environ[keys[1]],'keyAlias':os.environ[keys[2]],'keyPassword':os.environ[keys[3]]}
(root/'keystore.properties').write_text(''.join(k+'='+escape(v)+'\n' for k,v in props.items()))
(root/'keystore.properties').chmod(0o600)
