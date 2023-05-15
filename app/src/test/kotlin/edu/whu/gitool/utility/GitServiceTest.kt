package edu.whu.gitool.utility

import edu.whu.gitool.utility.GitService.Companion.SHA1_ID_LENGTH
import edu.whu.gitool.utility.GitService.Companion.getCompleteObjectId
import edu.whu.gitool.utility.GitService.Companion.isHexString
import org.junit.jupiter.api.Test
import java.io.File

class GitServiceTest {
    @Test
    fun `Valid commit id should return complete commit id`() {
        val rocksDB = File("/home/whalien/Desktop/rocksdb")
        if (!rocksDB.exists()) return
        val commitIdOpt =
            getCompleteObjectId("d52b520d", rocksDB.path)
        assert(commitIdOpt.isPresent) {
            "commit id should be present"
        }
        val commitId = commitIdOpt.get()[0]
        assert(commitId.length == SHA1_ID_LENGTH && isHexString(commitId)) {
            "commit id should be valid"
        }
    }
}
