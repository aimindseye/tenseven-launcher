# Deferred applications — provenance only

This directory is not an Android module and contains no application implementation. The unchanged deferred Tasks/Remind UI, Notes, Calc Plus/unit conversion and Sound Profiles source is preserved in the L0 deliverable under preserved-legacy-source. SOURCE_PROVENANCE.json records exact frozen-base Git blobs and SHA-256 hashes.

Every original file is also recoverable from 09d331d9f9b278ba1bed1323056cf1504e456629. No existing user's app data has been accessed, copied, migrated or deleted. The retained pure compatibility models live only under app/src/test/java/.../extras (with five identical support files under androidTest) for the existing JVM and instrumentation regressions, outside production source sets.

Hub-owned reminders, their domain records and scheduler remain in the Launcher. Standalone Tasks UI and its old receiver are not packaged. All four deferred applications require real module extraction, data ownership/migration and device acceptance before distribution.


PR143-completion qualification preserves the original before blobs and separately records candidate hashes for the test-only models. Attribution is unchanged but wrapped. Calculator/converter validation and formatting were extracted into cohesive helpers; 7,602 differential output cases against the frozen implementations passed. Note color values remain exact Long constants. None of this compatibility code enters a production APK.
