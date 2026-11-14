#!/usr/bin/env python3
"""Offline regression contracts for removing bundled games, retaining optional tools."""
from pathlib import Path
import unittest
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

    def test_six_optional_tools_remain_disabled_until_selected(self):
        names = [
            '.features.phone.TenSevenPhoneActivity', '.features.calendar.TenSevenCalendarActivity',
            '.tasks.TasksLauncherActivity',
            'io.github.aimindseye.tenseven.extras.calculator.MainActivity',
            'io.github.aimindseye.tenseven.extras.notes.MainActivity',
            'io.github.aimindseye.tenseven.extras.profiles.MainActivity',
        ]
        for name in names:
            with self.subTest(activity=name):
                activity = self.activities[name]
                self.assertEqual('false', activity.get(ANDROID + 'enabled'))
                self.assertEqual('true', activity.get(ANDROID + 'exported'))
                actions = [a.get(ANDROID + 'name') for a in activity.findall('intent-filter/action')]
                self.assertIn('android.intent.action.MAIN', actions)

    def test_notes_share_entry_point_is_retained(self):
        notes = self.activities['io.github.aimindseye.tenseven.extras.notes.MainActivity']
        actions = [a.get(ANDROID + 'name') for a in notes.findall('intent-filter/action')]
        self.assertIn('android.intent.action.SEND', actions)

    def test_bundled_game_implementation_and_tests_are_absent(self):
        for source_set in ['main', 'test', 'androidTest']:
            path = ROOT / 'app/src' / source_set / 'java/io/github/aimindseye/tenseven/extras/games'
            self.assertEqual([], list(path.rglob('*.kt')))

    def test_unused_games_setup_resource_is_absent(self):
        resources = ET.parse(ROOT / 'app/src/main/res/values/tenseven.xml').getroot()
        names = {s.get('name') for s in resources.findall('string')}
        self.assertNotIn('setup_extras_games', names)
        self.assertIn('setup_extras_tools', names)


if __name__ == '__main__':
    unittest.main()
