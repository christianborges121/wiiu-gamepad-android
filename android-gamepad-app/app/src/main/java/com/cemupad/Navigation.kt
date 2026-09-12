package com.cemupad

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cemupad.dsu.DSUServer
import com.cemupad.input.TouchInputHandler
import com.cemupad.ui.main.MainScreen

@Composable
fun MainNavigation(
    dsuServer: DSUServer? = null,
    touchHandler: TouchInputHandler? = null,
    modifier: Modifier = Modifier
) {
    MainScreen(
        dsuServer = dsuServer,
        touchHandler = touchHandler,
        modifier = modifier
    )
}
