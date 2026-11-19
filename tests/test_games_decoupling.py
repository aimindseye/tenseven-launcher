#!/usr/bin/env python3
"""Offline regression contracts for the games-free L0 application separation."""
from pathlib import Path
import unittest
import json
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'


class GamesDecouplingTests(unittest.TestCase):
    def setUp(self):
        self.manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
        self.activities = {a.get(ANDROID + 'name'): a for a in self.manifest.findall('application/activity') + self.manifest.findall('application/activity-alias')}

    def test_no_bundled_game_entry_points_or_companion_visibility(self):
        self.assertFalse(any('.extras.games.' in name for name in self.activities))
        packages = [p.get(ANDROID + 'name') for p in self.manifest.findall('queries/package')]
        self.assertNotIn('io.github.aimindseye.tenseven.games', packages)

    def test_optional_app_implementations_are_outside_core(self):
        forbidden = ('.features.phone.', '.features.calendar.', '.extras.', '.tasks.Tasks')
        for name in self.activities:
            self.assertFalse(any(part in name for part in forbidden), name)
        source = ROOT / 'app/src/main/java/io/github/aimindseye/tenseven'
        self.assertEqual([], list((source / 'features/phone').rglob('*.kt')))
        self.assertEqual([], list((source / 'features/calendar').rglob('*.kt')))
        self.assertEqual([], list((source / 'extras').rglob('*.kt')))
        self.assertFalse((source / 'tasks/TasksActivity.kt').exists())
        for module, activity in [('phone', 'TenSevenPhoneActivity'), ('calendar', 'TenSevenCalendarActivity')]:
            manifest = ET.parse(ROOT / module / 'src/main/AndroidManifest.xml').getroot()
            entry = next(a for a in manifest.findall('application/activity') if a.get(ANDROID + 'name').endswith('.' + activity))
            self.assertEqual('true', entry.get(ANDROID + 'exported'))
            self.assertNotEqual('false', entry.get(ANDROID + 'enabled'))
            actions = [a.get(ANDROID + 'name') for a in entry.findall('intent-filter/action')]
            self.assertIn('android.intent.action.MAIN', actions)
        settings = (ROOT / 'settings.gradle.kts').read_text()
        self.assertNotIn('include(":legacy-apps")', settings)
        provenance = json.loads((ROOT / 'legacy-apps/SOURCE_PROVENANCE.json').read_text())
        archived = {item['original']: item for item in provenance}
        for path in ['tasks/TasksActivity.kt', 'extras/notes/MainActivity.kt', 'extras/calculator/MainActivity.kt', 'extras/convert/ConverterScreen.kt', 'extras/profiles/MainActivity.kt']:
            entry = archived['app/src/main/java/io/github/aimindseye/tenseven/' + path]
            self.assertEqual('09d331d9f9b278ba1bed1323056cf1504e456629', entry['base'])
            self.assertEqual(40, len(entry['blob']))
            self.assertEqual(64, len(entry['sha256']))
            self.assertIn('artifact', entry)

    def test_notes_share_entry_is_not_exported_by_core(self):
        self.assertNotIn('io.github.aimindseye.tenseven.extras.notes.MainActivity', self.activities)
        for activity in self.activities.values():
            actions = [a.get(ANDROID + 'name') for a in activity.findall('intent-filter/action')]
            self.assertNotIn('android.intent.action.SEND', actions)

    def test_bundled_game_implementation_and_tests_are_absent(self):
        for module in ['app', 'phone', 'calendar', 'design']:
            for source_set in ['main', 'test', 'androidTest']:
                path = ROOT / module / 'src' / source_set / 'java/io/github/aimindseye/tenseven/extras/games'
                self.assertEqual([], list(path.rglob('*.kt')))

    def test_unused_games_setup_resource_is_absent(self):
        resources = ET.parse(ROOT / 'app/src/main/res/values/tenseven.xml').getroot()
        names = {s.get('name') for s in resources.findall('string')}
        self.assertNotIn('setup_extras_games', names)
        self.assertIn('setup_extras_tools', names)


if __name__ == '__main__':
    unittest.main()
