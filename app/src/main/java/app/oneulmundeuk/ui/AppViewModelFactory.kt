package app.oneulmundeuk.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import app.oneulmundeuk.AppContainer
import app.oneulmundeuk.JournalApplication

/** Access the manual DI container from a ViewModel initializer. */
fun CreationExtras.container(): AppContainer = (this[APPLICATION_KEY] as JournalApplication).container
