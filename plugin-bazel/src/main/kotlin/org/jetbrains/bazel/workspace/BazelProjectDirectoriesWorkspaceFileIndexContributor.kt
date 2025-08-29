package org.jetbrains.bazel.workspace

import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.intellij.workspaceModel.ide.toPath
import org.jetbrains.bazel.workspacemodel.entities.BazelProjectDirectoriesEntity

class BazelProjectDirectoriesWorkspaceFileIndexContributor : WorkspaceFileIndexContributor<BazelProjectDirectoriesEntity> {
  override val entityClass: Class<BazelProjectDirectoriesEntity> = BazelProjectDirectoriesEntity::class.java

  override fun registerFileSets(
    entity: BazelProjectDirectoriesEntity,
    registrar: WorkspaceFileSetRegistrar,
    storage: EntityStorage,
  ) {
    /**
     * This makes files under the project root part of the project without actually having to index them
     * (which can be very slow if there files other than source files, see https://youtrack.jetbrains.com/issue/BAZEL-2088).
     * If for some reason we do want to index files that aren't part of any target, then we can call [registerIncludedDirectories].
     * Conversely, in IDEA 2025.1, where [WorkspaceFileKind.CONTENT_NON_INDEXABLE] isn't available, we have to do it regardless.
     */
    if (entity.indexAllFilesInIncludedRoots) {
      registrar.registerIncludedDirectories(entity)
    } else {
      registrar.registerIndexAdditionalFiles(entity)
    }
    registrar.registerExcludedDirectories(entity)

    registrar.registerFileSet(
      root = entity.projectRoot,
      kind = WorkspaceFileKind.CONTENT_NON_INDEXABLE,
      entity = entity,
      customData = null,
    )
  }

  private fun WorkspaceFileSetRegistrar.registerIncludedDirectories(entity: BazelProjectDirectoriesEntity) {
    if (!Registry.`is`("bazel.x.index.all", false)) {
      registerFileSet(
        root = entity.projectRoot,
        kind = WorkspaceFileKind.CONTENT,
        entity = entity,
        customData = null,
      )
    } else {
      val includedGrouped = entity.includedRoots.groupBy {
        it.url.contains("strato/config")
      }
      val nonStrato = includedGrouped[false] ?: emptyList()
      val strato = includedGrouped[true] ?: emptyList()

      nonStrato.forEach {
        registerFileSet(
          root = it,
          kind = WorkspaceFileKind.CONTENT,
          entity = entity,
          customData = null,
        )
      }
      if (strato.isNotEmpty()) {
        var stratoDir: VirtualFileUrl? = strato.first()
        while (stratoDir != null && !stratoDir.url.endsWith("/strato/config")) {
          stratoDir = stratoDir.parent
        }
        if (stratoDir != null) {
          listOf(stratoDir, stratoDir.append(".thrift_deps")).forEach { stratoDirsToIndex ->
            registerFileSet(
              root = stratoDirsToIndex,
              kind = WorkspaceFileKind.CONTENT,
              entity = entity,
              customData = null,
            )
          }
        }
      }
    }
  }

  private fun WorkspaceFileSetRegistrar.registerExcludedDirectories(entity: BazelProjectDirectoriesEntity) {
    excludeSymlinksFromFileWatcher(entity.excludedRoots.map { it.toPath() })
    entity.excludedRoots.forEach {
      registerExcludedRoot(
        excludedRoot = it,
        entity = entity,
      )
    }
  }

  private fun WorkspaceFileSetRegistrar.registerIndexAdditionalFiles(entity: BazelProjectDirectoriesEntity) {
    entity.indexAdditionalFiles.forEach {
      registerNonRecursiveFileSet(
        file = it,
        kind = WorkspaceFileKind.CONTENT,
        entity = entity,
        customData = null,
      )
    }
  }
}
