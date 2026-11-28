package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Device decisions go through [DeviceProfile]; only the profile reader and the diagnostic report read the build. */
class DeviceProfileContractTest {
    private val allowed = setOf("CurrentDeviceProfile.kt", "DeviceCompatibility.kt")
    private val buildProperty = Regex("""Build\.(MODEL|DEVICE|MANUFACTURER)""")

    @Test fun onlyTheProfileReaderLooksAtTheBuildModel() {
        val root = File("src/main/java")
        assertTrue("run from the module directory", root.isDirectory)
        val offenders =
            root
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name !in allowed }
                .filter { file -> file.readLines().any { buildProperty.containsMatchIn(it) } }
                .map { it.name }
                .toList()
        assertTrue("Use DeviceProfile instead of Build.* in: $offenders", offenders.isEmpty())
    }
}
