package com.squads.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner

private const val PARENT_KEY = "shared_viewmodel_parent_content_key"

/**
 * Provides ViewModelStoreOwner to NavEntries, with support for parent-child ViewModel sharing.
 * Adapted from android/nav3-recipes/sharedviewmodel.
 */
@Composable
fun <T : Any> rememberSharedViewModelStoreNavEntryDecorator(
    viewModelStoreOwner: ViewModelStoreOwner =
        checkNotNull(LocalViewModelStoreOwner.current) {
            "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
        },
    removeViewModelStoreOnPop: () -> Boolean = { true },
): NavEntryDecorator<T> {
    val currentRemoveViewModelStoreOnPop = rememberUpdatedState(removeViewModelStoreOnPop)
    return remember(viewModelStoreOwner, currentRemoveViewModelStoreOnPop) {
        NavEntryDecorator<T>(
            onPop = { key ->
                if (currentRemoveViewModelStoreOnPop.value()) {
                    viewModelStoreOwner.viewModelStore
                        .getEntryViewModel()
                        .clearViewModelStoreOwnerForKey(key)
                }
            },
            decorate = { entry ->
                val contentKey = entry.metadata[PARENT_KEY] ?: entry.contentKey
                val entryViewModelStore =
                    viewModelStoreOwner.viewModelStore
                        .getEntryViewModel()
                        .viewModelStoreForKey(contentKey)

                val savedStateRegistryOwner = LocalSavedStateRegistryOwner.current
                val childViewModelStoreOwner =
                    remember {
                        object :
                            ViewModelStoreOwner,
                            SavedStateRegistryOwner by savedStateRegistryOwner,
                            HasDefaultViewModelProviderFactory {
                            override val viewModelStore: ViewModelStore
                                get() = entryViewModelStore

                            override val defaultViewModelProviderFactory: ViewModelProvider.Factory
                                get() = SavedStateViewModelFactory()

                            override val defaultViewModelCreationExtras: CreationExtras
                                get() =
                                    MutableCreationExtras().also {
                                        it[SAVED_STATE_REGISTRY_OWNER_KEY] = this
                                        it[VIEW_MODEL_STORE_OWNER_KEY] = this
                                    }

                            init {
                                require(this.lifecycle.currentState == Lifecycle.State.INITIALIZED) {
                                    "The Lifecycle state is already beyond INITIALIZED. The " +
                                        "SharedViewModelStoreNavEntryDecorator requires adding the " +
                                        "SavedStateNavEntryDecorator to ensure support for " +
                                        "SavedStateHandles."
                                }
                                enableSavedStateHandles()
                            }
                        }
                    }
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides childViewModelStoreOwner,
                ) {
                    entry.Content()
                }
            },
        )
    }
}

fun parentMetadata(key: Any): Map<String, Any> = mapOf(PARENT_KEY to key)

private class EntryViewModel : ViewModel() {
    private val owners = mutableMapOf<Any, ViewModelStore>()

    fun viewModelStoreForKey(key: Any): ViewModelStore = owners.getOrPut(key) { ViewModelStore() }

    fun clearViewModelStoreOwnerForKey(key: Any) {
        owners.remove(key)?.clear()
    }

    override fun onCleared() {
        owners.forEach { (_, store) -> store.clear() }
    }
}

private val entryViewModelFactory =
    viewModelFactory {
        initializer { EntryViewModel() }
    }

private fun ViewModelStore.getEntryViewModel(): EntryViewModel =
    ViewModelProvider.create(store = this, factory = entryViewModelFactory)[EntryViewModel::class]
