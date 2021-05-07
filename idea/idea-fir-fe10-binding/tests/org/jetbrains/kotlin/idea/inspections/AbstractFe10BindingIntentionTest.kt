/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.intentions.AbstractIntentionTest
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.utils.IgnoreTests
import java.io.File

abstract class AbstractFe10BindingIntentionTest : AbstractIntentionTest() {
    override fun isFirPlugin() = true

    override fun checkForErrorsBefore(fileText: String) {
        if (!InTextDirectivesUtils.isDirectiveDefined(fileText, IgnoreTests.DIRECTIVES.IGNORE_FIR)) {
            super.checkForErrorsBefore(fileText)
        }
    }

    override fun checkForErrorsAfter(fileText: String) {
        if (!InTextDirectivesUtils.isDirectiveDefined(fileText, IgnoreTests.DIRECTIVES.IGNORE_FIR)) {
            super.checkForErrorsAfter(fileText)
        }
    }

    override fun doTestFor(mainFile: File, pathToFiles: Map<String, PsiFile>, intentionAction: IntentionAction, fileText: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(mainFile.toPath(), IgnoreTests.DIRECTIVES.IGNORE_FIR, "after") {
            super.doTestFor(mainFile, pathToFiles, intentionAction, fileText)
        }
    }
}