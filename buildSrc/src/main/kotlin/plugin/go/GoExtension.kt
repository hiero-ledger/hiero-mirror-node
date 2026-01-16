// SPDX-License-Identifier: Apache-2.0

package plugin.go

import java.io.File

// Extension object to contain the Go plugin properties
open class GoExtension {
    var arch: String = ""
    lateinit var cacheDir: File
    lateinit var goRoot: File
    lateinit var goBin: File
    var os: String = ""
    var pkg: String = ""
    var version: String = ""
}
