import json
import tempfile
from pathlib import Path
import unittest
from app_updates import UpdateStore,publish

class UpdateTests(unittest.TestCase):
    def test_publish_load_hash_and_atomic_metadata(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);apk=root/'source.apk';apk.write_bytes(b'APK_TEST_BYTES')
            release=publish(apk,23,'test',root/'releases')
            public,body=UpdateStore(root/'releases').load()
            self.assertEqual(body,apk.read_bytes());self.assertEqual(public['size'],len(body));self.assertNotIn('file',public)
            self.assertEqual(public['apk_path'],'/app/update.apk?sha256='+release['sha256'])
            (root/'releases'/release['file']).write_bytes(b'APK_TEST_BYTEX')
            with self.assertRaises(ValueError):UpdateStore(root/'releases').load()
    def test_traversal_and_wrong_package_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);apk=root/'source.apk';apk.write_bytes(b'bytes');data=publish(apk,23,'test',root/'releases')
            manifest=root/'releases'/'latest.json'
            for changes in ({'file':'../source.apk'},{'package':'other.app'},{'size':999},{'sha256':'bad'}):
                manifest.write_text(json.dumps({**data,**changes}))
                with self.assertRaises(ValueError):UpdateStore(root/'releases').load()
