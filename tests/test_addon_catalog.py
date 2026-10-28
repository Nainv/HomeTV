"""Catalog entries must match buildable, separate module-card apps."""
import json
from pathlib import Path
import re
import unittest

ROOT=Path(__file__).resolve().parents[1]
class AddonCatalogTest(unittest.TestCase):
    def test_catalog_matches_all_declared_card_packages_and_sdk_levels(self):
        entries=json.loads((ROOT/'launcher/app/src/main/assets/addons/catalog.json').read_text())
        declared={p.parents[3].name:p for p in (ROOT/'addons').glob('*/app/src/main/AndroidManifest.xml')
                  if 'com.gathin.tvhome.card' in p.read_text()}
        self.assertEqual({e['id'] for e in entries},set(declared))
        self.assertEqual(len(entries),len({e['packageName'] for e in entries}))
        for entry in entries:
            with self.subTest(addon=entry['id']):
                self.assertRegex(entry['id'],r'^[a-z0-9]+(?:-[a-z0-9]+)*$')
                build=next(declared[entry['id']].parents[3].glob('app/build.gradle*')).read_text()
                pkg=re.search(r'applicationId\s*(?:=\s*)?[\x27\"]([^\x27\"]+)',build).group(1)
                sdk=int(re.search(r'minSdk\s*(?:=\s*)?(\d+)',build).group(1))
                self.assertEqual(entry['packageName'],pkg)
                self.assertEqual(entry['minSdk'],sdk)
                self.assertTrue(entry['ar'].strip())
                self.assertTrue(entry['en'].strip())
