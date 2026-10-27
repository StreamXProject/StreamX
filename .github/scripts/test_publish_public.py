import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch
import zipfile
import hashlib

spec = importlib.util.spec_from_file_location('publisher', Path(__file__).with_name('publish-public.py'))
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


def archive(files):
    data = io.BytesIO()
    with zipfile.ZipFile(data, 'w') as output:
        for name, content in files.items():
            output.writestr(name, content)
    return data.getvalue()


class PublicationTests(unittest.TestCase):
    def setUp(self):
        self.calls = []
        self.uploads = {}
        self.notes = ''
        self.existing = False
        self.prerelease = False
        self.draft = False
        self.files = {'StreamX-v1.8_arm64-v8a.apk': archive({'AndroidManifest.xml': 'binary manifest'})}
        self.env = patch.dict(os.environ, GH_SOURCE_TOKEN='private-read', GH_PUBLIC_TOKEN='public-write')
        self.env.start()
        self.addCleanup(self.env.stop)
        self.mock = patch.object(publisher, 'gh', self.gh)
        self.mock.start()
        self.addCleanup(self.mock.stop)
        publisher.TAG = 'v1.8.5'

    def gh(self, token, *args, check=True):
        self.calls.append((token, args))
        out, code = '', 0
        if args[:2] == ('release', 'view'):
            if token == 'private-read':
                out = json.dumps({'assets': [{'name': n} for n in self.files],
                                  'isDraft': self.draft, 'isPrerelease': self.prerelease,
                                  'body': 'SECRET private notes', 'target_commitish': 'private-commit'})
            elif not self.existing:
                code = 1
        elif args[:2] == ('release', 'download'):
            name = args[args.index('--pattern') + 1]
            (Path(args[args.index('--dir') + 1]) / name).write_bytes(self.files[name])
        elif args[1] in ('create', 'upload'):
            for item in args:
                path = Path(item)
                if path.is_file() and path.name != 'notes.md':
                    self.uploads[path.name] = path.read_bytes()
        if '--notes-file' in args:
            self.notes = Path(args[args.index('--notes-file') + 1]).read_text()
        return subprocess.CompletedProcess(args, code, out, '')

    def model(self):
        publisher.TAG = 'automix-1'
        self.files = {}
        packages = {}
        for abi in ('arm64-v8a', 'armeabi-v7a', 'x86_64'):
            content = {name: b'model' for name in ('libonnxruntime.so', 'libonnxruntime4j_jni.so',
                                                'beat_this_int8.onnx', 'vocals_umxhq_int8.onnx')}
            content['package.json'] = json.dumps({'version': 1, 'abi': abi, 'onnxRuntime': '1.28.0',
                'files': [{'name': n, 'size': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
                          for n, data in content.items()]}).encode()
            name = f'streamx-automix-1-{abi}.zip'
            self.files[name] = data = archive(content)
            packages[abi] = {'file': name, 'size': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
        self.files['automix-manifest.json'] = json.dumps({'version': 1, 'onnxRuntime': '1.28.0', 'packages': packages}).encode()

    def test_stable_has_alias_checksums_and_public_only_notes(self):
        self.files['source.zip'] = b'private source'
        publisher.publish()
        self.assertEqual(set(self.uploads), {'StreamX-v1.8_arm64-v8a.apk', 'StreamX.apk', 'SHA256SUMS.txt'})
        self.assertNotIn('SECRET', self.notes)
        edit = self.calls[-1][1]
        self.assertIn('--latest=true', edit)
        self.assertIn('--prerelease=false', edit)
        self.assertEqual(edit[edit.index('--title') + 1], 'StreamX 1.8.5')
        create = next(args for _, args in self.calls if args[1] == 'create')
        self.assertEqual(create[create.index('--target') + 1], 'main')
        self.assertNotIn('private-commit', create)
        self.assertEqual(self.uploads['StreamX.apk'], self.files['StreamX-v1.8_arm64-v8a.apk'])

    def test_preview_is_not_latest(self):
        self.prerelease = True
        publisher.TAG = 'v1.8-preview.20261022114339'
        publisher.publish()
        self.assertIn('--latest=false', self.calls[-1][1])
        self.assertIn('--prerelease=true', self.calls[-1][1])

    def test_models_preserve_bytes_and_are_not_latest(self):
        self.model()
        publisher.publish()
        self.assertIn('--latest=false', self.calls[-1][1])
        self.assertEqual(self.uploads['automix-manifest.json'], self.files['automix-manifest.json'])
        self.assertEqual(len(self.uploads), 5)

    def test_corrupt_model_never_reaches_public_repo(self):
        self.model()
        self.files['streamx-automix-1-arm64-v8a.zip'] += b'corruption'
        with self.assertRaisesRegex(ValueError, 'checksum'):
            publisher.publish()
        self.assertFalse(any(token == 'public-write' for token, _ in self.calls))

    def test_draft_stays_private(self):
        self.draft = True
        with self.assertRaisesRegex(ValueError, 'published first'):
            publisher.publish()
        self.assertFalse(any(token == 'public-write' for token, _ in self.calls))

    def test_invalid_tag_is_rejected_before_network_access(self):
        publisher.TAG = '../source'
        with self.assertRaises(ValueError):
            publisher.publish()
        self.assertEqual(self.calls, [])

    def test_repeat_uploads_to_existing_release(self):
        self.existing = True
        publisher.publish()
        self.assertTrue(any(args[1] == 'upload' for _, args in self.calls))
        self.assertFalse(any(args[1] == 'create' for _, args in self.calls))

    def test_missing_apk_refused(self):
        self.files = {'source.zip': b'private'}
        with self.assertRaisesRegex(ValueError, 'no StreamX APKs'):
            publisher.publish()
        self.assertFalse(any(token == 'public-write' for token, _ in self.calls))


if __name__ == '__main__':
    unittest.main()
