// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.index

import com.intellij.util.indexing.IndexInfrastructure
import java.io.File

/**
 * @author traff
 */
abstract class PrebuiltIndexProviderBase<Value> : PrebuiltIndexProvider<Value>() {
  override fun getIndexRoot(): File = File(IndexInfrastructure.getPersistentIndexRoot(), "prebuilt/$dirName")
}
