/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.j2k

import com.intellij.psi.*
import org.jetbrains.kotlin.j2k.conversions.multiResolveFqName
import org.jetbrains.kotlin.j2k.conversions.resolveFqName
import org.jetbrains.kotlin.j2k.tree.*
import org.jetbrains.kotlin.j2k.tree.impl.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs


class JKSymbolProvider {
    val symbolsByPsi = mutableMapOf<PsiElement, JKSymbol>()
    val symbolsByJK = mutableMapOf<JKDeclaration, JKSymbol>()

    private val elementVisitor = ElementVisitor()

    fun preBuildTree(files: List<PsiJavaFile>) {
        files.forEach { it.accept(elementVisitor) }
    }

    fun provideDirectSymbol(psi: PsiElement): JKSymbol {
        return symbolsByPsi.getOrPut(psi) {
            when (psi) {
                is PsiClass -> JKMultiverseClassSymbol(psi)
                is KtClassOrObject -> JKMultiverseKtClassSymbol(psi)
                is PsiMethod -> JKMultiverseMethodSymbol(psi, this)
                is PsiField -> JKMultiverseFieldSymbol(psi, this)
                is KtNamedFunction -> JKMultiverseFunctionSymbol(psi, this)
                is KtProperty -> JKMultiversePropertySymbol(psi, this)
                is KtParameter -> JKMultiversePropertySymbol(psi, this)
                is PsiParameter -> JKMultiverseFieldSymbol(psi, this)
                is PsiLocalVariable -> JKMultiverseFieldSymbol(psi, this)
                else -> TODO(psi::class.toString())
            }
        }
    }

    internal inline fun <reified T : JKSymbol> provideSymbol(reference: PsiReference): T {
        val target = reference.resolve()
        if (target != null) return provideDirectSymbol(target) as T
        return (if (isAssignable<T, JKUnresolvedField>()) JKUnresolvedField(reference.canonicalText, this) else JKUnresolvedMethod(reference)) as T
    }

    fun provideUniverseSymbol(psi: PsiElement, jk: JKDeclaration): JKSymbol = provideUniverseSymbol(psi).also {
        when (it) {
            is JKUniverseClassSymbol -> it.target = jk as JKClass
            is JKUniverseFieldSymbol -> it.target = jk as JKVariable
            is JKUniverseMethodSymbol -> it.target = jk as JKMethod
        }
        symbolsByJK[jk] = it
    }

    fun provideUniverseSymbol(psi: PsiElement): JKSymbol = symbolsByPsi.getOrPut(psi) {
        when (psi) {
            is PsiField, is PsiParameter, is PsiLocalVariable -> JKUniverseFieldSymbol()
            is PsiMethod -> JKUniverseMethodSymbol(this)
            is PsiClass -> JKUniverseClassSymbol()
            else -> TODO()
        }
    }

    fun transferSymbol(to: JKDeclaration, from: JKDeclaration) = symbolsByJK[from]!!.let {
        it as JKUniverseSymbol<*>
        it.target = to
        symbolsByJK[to] = it
    }

    fun provideUniverseSymbol(jk: JKClass): JKClassSymbol = symbolsByJK.getOrPut(jk) {
        JKUniverseClassSymbol().also { it.target = jk }
    } as JKClassSymbol

    fun provideUniverseSymbol(jk: JKVariable): JKFieldSymbol = symbolsByJK.getOrPut(jk) {
        JKUniverseFieldSymbol().also { it.target = jk }
    } as JKFieldSymbol

    fun provideUniverseSymbol(jk: JKMethod): JKMethodSymbol = symbolsByJK.getOrPut(jk) {
        JKUniverseMethodSymbol(this).also { it.target = jk }
    } as JKMethodSymbol

    internal inline fun <reified T : JKSymbol> provideByFqName(classId: ClassId, context: PsiElement = symbolsByPsi.keys.first()): T {
        return resolveFqName(classId, context)?.let(::provideDirectSymbol).safeAs<T>() ?: when {
            isAssignable<T, JKUnresolvedMethod>() -> JKUnresolvedMethod(classId.asSingleFqName().asString().replace('/', '.'))
            isAssignable<T, JKUnresolvedField>() -> JKUnresolvedField(classId.asSingleFqName().asString().replace('/', '.'), this)
            else -> JKUnresolvedClassSymbol(classId.asSingleFqName().asString().replace('/', '.'))
        } as T
    }

    internal inline fun <reified T : JKSymbol> provideByFqNameMulti(fqName: String, context: PsiElement = symbolsByPsi.keys.first()): T {
        return multiResolveFqName(ClassId.fromString(fqName), context).firstOrNull()?.let(::provideDirectSymbol).safeAs<T>() ?: when {
            isAssignable<T, JKUnresolvedMethod>() -> JKUnresolvedMethod(fqName.replace('/', '.'))
            isAssignable<T, JKUnresolvedField>() -> JKUnresolvedField(fqName.replace('/', '.'), this)
            else -> JKUnresolvedClassSymbol(fqName.replace('/', '.'))
        } as T
    }

    internal inline fun <reified T : JKSymbol> provideByFqName(fqName: String, context: PsiElement = symbolsByPsi.keys.first()): T =
        provideByFqName(ClassId.fromString(fqName), context)

    internal inline fun <reified T : JKSymbol> provideByFqName(fqName: FqName, context: PsiElement = symbolsByPsi.keys.first()): T =
        provideByFqName(fqName.asString(), context)

    internal inline fun <reified T : JKSymbol> provideByFqName(fqName: FqNameUnsafe, context: PsiElement = symbolsByPsi.keys.first()): T =
        provideByFqName(fqName.asString(), context)


    private inner class ElementVisitor : JavaElementVisitor() {
        override fun visitClass(aClass: PsiClass) {
            provideUniverseSymbol(aClass)
            aClass.acceptChildren(this)
        }

        override fun visitField(field: PsiField) {
            provideUniverseSymbol(field)
        }

        override fun visitMethod(method: PsiMethod) {
            provideUniverseSymbol(method)
        }

        override fun visitEnumConstant(enumConstant: PsiEnumConstant) {
            provideUniverseSymbol(enumConstant)
        }

        override fun visitFile(file: PsiFile) {
            file.acceptChildren(this)
        }
    }

    internal inline fun <reified A, reified B> isAssignable(): Boolean = A::class.java.isAssignableFrom(B::class.java)
}