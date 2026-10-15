package com.raumanian.thirtysix.browser.architecture

import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/** Every type has one home, and data flows one way. */
class PlacementTest {

    private val scope = productionScope()

    @Test
    fun `view models and ui state live in presentation`() {
        scope.classes().withNameEndingWith("ViewModel").assertTrue { it.resideInPackage("..presentation..") }
        scope.classesAndInterfaces().withNameEndingWith("UiState")
            .assertTrue { it.resideInPackage("..presentation..") }
    }

    @Test
    fun `use cases and repository contracts live in domain`() {
        scope.classes().withNameEndingWith("UseCase").assertTrue { it.resideInPackage("..domain.usecase..") }
        scope.interfaces().withNameEndingWith("Repository").assertTrue { it.resideInPackage("..domain.repository..") }
        scope.classes().withNameEndingWith("Repository").filter { it.hasAbstractModifier }
            .assertTrue { it.resideInPackage("..domain.repository..") }
    }

    @Test
    fun `data types live in their data folder`() {
        scope.classes().withNameEndingWith("Repository", "RepositoryImpl").filterNot { it.hasAbstractModifier }
            .assertTrue { it.resideInPackage("..data.repository..") }
        scope.interfaces().withNameEndingWith("Api").assertTrue { it.resideInPackage("..data.datasource.remote..") }
        scope.classes().withNameEndingWith("Dto").assertTrue { it.resideInPackage("..data.datasource.remote.dto..") }
        scope.interfaces().withNameEndingWith("Dao").assertTrue { it.resideInPackage("..data.datasource.local..") }
        scope.classes().withNameEndingWith("Entity")
            .assertTrue { it.resideInPackage("..data.datasource.local.entity..") }
        scope.classesAndInterfaces().withNameEndingWith("RemoteDataSource")
            .assertTrue { it.resideInPackage("..data.datasource.remote..") }
        scope.classesAndInterfaces().withNameEndingWith("LocalDataSource")
            .assertTrue { it.resideInPackage("..data.datasource.local..") }
    }

    @Test
    fun `domain models are not data types`() {
        scope.classes()
            .filter { it.resideInPackage("..domain.model..") }
            .assertFalse { model -> DATA_TYPE_SUFFIXES.any { model.name.endsWith(it) } }
    }

    @Test
    fun `hilt modules live in di`() {
        scope.classesAndObjects()
            .filter { declaration -> declaration.annotations.any { it.name == "Module" } }
            .assertTrue { it.resideInPackage("..di..") }
    }

    @Test
    fun `view models never reach past the domain`() {
        scope.classes().withNameEndingWith("ViewModel").assertTrue { viewModel ->
            viewModel.primaryConstructor?.parameters.orEmpty().none { parameter ->
                val isDomainContract = parameter.type.hasInterfaceDeclaration { it.resideInPackage("..domain..") }
                DATA_SUFFIXES.any { parameter.type.name.endsWith(it) } ||
                    (parameter.type.name.startsWith("Default") && !isDomainContract) ||
                    parameter.type.name.endsWith("Impl")
            }
        }
    }

    @Test
    fun `view models expose read-only state`() {
        scope.classes().withNameEndingWith("ViewModel").assertTrue { viewModel ->
            viewModel.properties().filterNot { it.hasPrivateModifier }.none { property ->
                MUTABLE_STATE.any { property.text.contains(it) }
            }
        }
    }

    @Test
    fun `ui state is immutable`() {
        scope.classes().withNameEndingWith("UiState").assertTrue { state ->
            (state.hasDataModifier || state.hasSealedModifier) && state.properties().none { it.isVar }
        }
    }

    private companion object {
        val DATA_TYPE_SUFFIXES = listOf("Dto", "Entity", "Response", "Request")
        val DATA_SUFFIXES = listOf("DataSource", "Api", "Dao", "Dto", "Entity")
        val MUTABLE_STATE = listOf("MutableStateFlow", "MutableSharedFlow", "mutableStateOf", "Channel<")
    }
}
