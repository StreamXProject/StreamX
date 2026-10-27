#!/usr/bin/env python3
"""Publish only approved binary assets; never copy source tags, commits or private notes."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

SOURCE = os.environ.get('SOURCE_REPO', 'StreamXProject/StreamX')
PUBLIC = os.environ.get('PUBLIC_REPO', 'StreamXProject/StreamX-Releases')
TAG = os.environ.get('RELEASE_TAG', '')


def gh(token, *args, check=True):
    return subprocess.run(['gh', *args], env={**os.environ, 'GH_TOKEN': token},
                          check=check, text=True, capture_output=True)


def publish():
    source_token = os.environ['GH_SOURCE_TOKEN']
    public_token = os.environ['GH_PUBLIC_TOKEN']
    model = re.fullmatch(r'automix-[1-9][0-9]*', TAG) is not None
    if not model and not re.fullmatch(r'v[0-9]+(?:\.[0-9]+)+(?:-[A-Za-z0-9.-]+)?', TAG):
        raise ValueError('Expected an app version tag or automix-N')
    release = json.loads(gh(source_token, 'release', 'view', TAG, '--repo', SOURCE,
                            '--json', 'assets,isDraft,isPrerelease').stdout)
    if release['isDraft']:
        raise ValueError('Source release must be published first')
    names = [asset['name'] for asset in release['assets']]
    if model:
        allowed = ['automix-manifest.json'] + [
            f'streamx-{TAG}-{abi}.zip' for abi in ('arm64-v8a', 'armeabi-v7a', 'x86_64')]
        if not all(name in names for name in allowed):
            raise ValueError('Automix release must contain the manifest and all three ABI packages')
    else:
        allowed = [name for name in names if re.fullmatch(r'StreamX[-A-Za-z0-9._]*\.apk', name)]
        if not allowed:
            raise ValueError('Release has no StreamX APKs')
    with tempfile.TemporaryDirectory(prefix='streamx-public-') as temp:
        root = Path(temp)
        for name in allowed:
            gh(source_token, 'release', 'download', TAG, '--repo', SOURCE,
               '--pattern', name, '--dir', temp)
        if model:
            manifest = json.loads((root / 'automix-manifest.json').read_text())
            if manifest['version'] != int(TAG.removeprefix('automix-')):
                raise ValueError('Manifest version does not match the tag')
            packages = manifest['packages']
            if set(packages) != {'arm64-v8a', 'armeabi-v7a', 'x86_64'}:
                raise ValueError('Unexpected Automix architectures')
            for abi, entry in packages.items():
                expected = f'streamx-{TAG}-{abi}.zip'
                if entry['file'] != expected:
                    raise ValueError('Unexpected package filename')
                data = (root / expected).read_bytes()
                if len(data) != entry['size'] or hashlib.sha256(data).hexdigest() != entry['sha256']:
                    raise ValueError('Package checksum does not match manifest')
                with zipfile.ZipFile(root / expected) as archive:
                    required = {'libonnxruntime.so', 'libonnxruntime4j_jni.so',
                                'beat_this_int8.onnx', 'vocals_umxhq_int8.onnx'}
                    if len(archive.namelist()) != 5 or set(archive.namelist()) != required | {'package.json'}:
                        raise ValueError('Unexpected files inside Automix package')
                    description = json.loads(archive.read('package.json'))
                    if (description['version'] != manifest['version'] or description['abi'] != abi
                            or description['onnxRuntime'] != manifest['onnxRuntime']):
                        raise ValueError('Incompatible Automix package description')
                    if len(description['files']) != 4 or {f['name'] for f in description['files']} != required:
                        raise ValueError('Incomplete package description')
                    for item in description['files']:
                        content = archive.read(item['name'])
                        if len(content) != item['size'] or hashlib.sha256(content).hexdigest() != item['sha256']:
                            raise ValueError('Automix content checksum mismatch')
            title = f'Advanced Automix · package {manifest["version"]}'
            notes = (f'Optional Advanced Automix engine and models. Package {manifest["version"]}, '
                     f'ONNX Runtime {manifest["onnxRuntime"]}.\n\n'
                     'Install from StreamX Settings → Advanced Automix download. '
                     'Packages are available for ARM64, ARMv7 and x86_64. '
                     'The manifest records package sizes and SHA-256 checksums.\n\n'
                     'This is a model package, not an Android app update.\n')
        else:
            # Preserve per-ABI names for the updater. The site's stable alias currently targets ARM64.
            preferred = next((n for n in allowed if n == 'StreamX.apk'), None)
            preferred = preferred or next((n for n in allowed if 'arm64-v8a' in n), None)
            if not preferred:
                raise ValueError('A StreamX.apk or ARM64 APK is required for the download page')
            if preferred != 'StreamX.apk':
                (root / 'StreamX.apk').write_bytes((root / preferred).read_bytes())
                allowed.append('StreamX.apk')
            for name in allowed:
                with zipfile.ZipFile(root / name) as apk:
                    if 'AndroidManifest.xml' not in apk.namelist():
                        raise ValueError('APK has no Android manifest')
            title = f'StreamX {TAG.removeprefix("v")}'
            notes = ('StreamX for Android 8.0 and later.\n\n'
                     'Download **StreamX.apk** for ARM64 devices. Architecture-specific APKs '
                     'are also listed when available.\n\n'
                     'Advanced Automix is an optional download from Settings. '
                     'SHA256SUMS.txt contains the SHA-256 checksum of every download.\n')
        sums = ''.join(f'{hashlib.sha256((root / name).read_bytes()).hexdigest()}  {name}\n'
                       for name in sorted(allowed))
        (root / 'SHA256SUMS.txt').write_text(sums)
        notes_path = root / 'notes.md'
        notes_path.write_text(notes)
        assets = [str(root / name) for name in allowed + ['SHA256SUMS.txt']]
        exists = gh(public_token, 'release', 'view', TAG, '--repo', PUBLIC, check=False).returncode == 0
        if exists:
            gh(public_token, 'release', 'upload', TAG, *assets, '--repo', PUBLIC, '--clobber')
        else:
            # Create tags only in the public repository, pointing to its own website commit.
            gh(public_token, 'release', 'create', TAG, *assets, '--repo', PUBLIC,
               '--target', 'main', '--draft', '--title', title, '--notes-file', str(notes_path))
        prerelease = release['isPrerelease'] and not model
        stable = not model and not prerelease
        gh(public_token, 'release', 'edit', TAG, '--repo', PUBLIC,
           '--draft=false', f'--prerelease={str(prerelease).lower()}',
           f'--latest={str(stable).lower()}', '--title', title, '--notes-file', str(notes_path))
        print(f'Published https://github.com/{PUBLIC}/releases/tag/{TAG}')


if __name__ == '__main__':
    publish()
