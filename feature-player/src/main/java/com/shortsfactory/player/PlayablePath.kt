package com.shortsfactory.player

import android.content.Context
import java.io.File

/** O leitor só reproduz arquivos regulares dentro de `filesDir` ou `cacheDir` do app. */
object PlayablePath {
    fun isAllowed(context: Context, path: String): Boolean =
        isInside(path, listOf(context.filesDir, context.cacheDir))

    /** Arquivo regular cujo caminho canônico fica dentro de uma das raízes (bloqueia `..` e symlinks para fora). */
    fun isInside(path: String, roots: List<File>): Boolean = runCatching {
        val target = File(path).canonicalFile
        target.isFile && roots.any { root ->
            target.path.startsWith(root.canonicalFile.path + File.separator)
        }
    }.getOrDefault(false)
}
