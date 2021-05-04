/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.builder

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.scopes.FirScopeProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.psi.*

data class RawFirReplacement<T : KtElement>(val from: T, val to: T)

class RawFirFragmentForLazyBodiesBuilder<T : KtElement> private constructor(
    session: FirSession,
    baseScopeProvider: FirScopeProvider,
    private val declarationToBuild: KtDeclaration,
    private val replacement: RawFirReplacement<T>? = null
) : RawFirBuilder(session, baseScopeProvider, RawFirBuilderMode.NORMAL) {

    companion object {
        fun <T : KtElement> buildWithReplacement(
            session: FirSession,
            baseScopeProvider: FirScopeProvider,
            designation: List<FirDeclaration>,
            declarationToBuild: KtDeclaration,
            replacement: RawFirReplacement<T>? = null
        ): FirDeclaration {
            if (replacement != null) {
                require(replacement.from::class == replacement.to::class) {
                    "Build with replacement is possible for same type in replacements but given\n${replacement::class.simpleName} and ${replacement.to::class.simpleName}"
                }
            }
            val builder = RawFirFragmentForLazyBodiesBuilder(session, baseScopeProvider, declarationToBuild, replacement)
            builder.context.packageFqName = declarationToBuild.containingKtFile.packageFqName
            return builder.moveNext(designation.iterator(), containingClass = null)
        }

        fun build(
            session: FirSession,
            baseScopeProvider: FirScopeProvider,
            designation: List<FirDeclaration>,
            rootNonLocalDeclaration: KtDeclaration
        ): FirDeclaration {
            val builder = RawFirFragmentForLazyBodiesBuilder<KtElement>(session, baseScopeProvider, rootNonLocalDeclaration)
            builder.context.packageFqName = rootNonLocalDeclaration.containingKtFile.packageFqName
            return builder.moveNext(designation.iterator(), containingClass = null)
        }
    }

    private fun moveNext(iterator: Iterator<FirDeclaration>, containingClass: FirRegularClass?): FirDeclaration {
        if (!iterator.hasNext()) {
            val visitor = object : Visitor() {
                override fun convertElement(element: KtElement): FirElement? = super.convertElement(
                    if (replacement != null && replacement.from == element) replacement.to else element
                )

                override fun convertProperty(
                    property: KtProperty,
                    ownerRegularOrAnonymousObjectSymbol: FirClassSymbol<*>?,
                    ownerRegularClassTypeParametersCount: Int?
                ): FirProperty {
                    val replacementProperty = if (replacement != null && replacement.from == property) replacement.to else property
                    check(replacementProperty is KtProperty)
                    return super.convertProperty(
                        property = replacementProperty,
                        ownerRegularOrAnonymousObjectSymbol = ownerRegularOrAnonymousObjectSymbol,
                        ownerRegularClassTypeParametersCount = ownerRegularClassTypeParametersCount
                    )
                }
            }

            return when (declarationToBuild) {
                is KtProperty -> {
                    val ownerSymbol = containingClass?.symbol
                    val ownerTypeArgumentsCount = containingClass?.typeParameters?.size
                    visitor.convertProperty(declarationToBuild, ownerSymbol, ownerTypeArgumentsCount)
                }
                else -> visitor.convertElement(declarationToBuild)
            } as FirDeclaration
        }

        val parent = iterator.next()
        if (parent !is FirRegularClass) return moveNext(iterator, containingClass = null)

        val classOrObject = parent.psi
        check(classOrObject is KtClassOrObject)

        withChildClassName(classOrObject.nameAsSafeName, false) {
            withCapturedTypeParameters {
                if (!parent.isInner) context.capturedTypeParameters = context.capturedTypeParameters.clear()
                addCapturedTypeParameters(parent.typeParameters.take(classOrObject.typeParameters.size))
                registerSelfType(classOrObject.toDelegatedSelfType(parent))
                return moveNext(iterator, parent)
            }
        }
    }

    private fun PsiElement?.toDelegatedSelfType(firClass: FirRegularClass): FirResolvedTypeRef =
        toDelegatedSelfType(firClass.typeParameters, firClass.symbol)
}

