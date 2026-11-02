package com.pratap.filesharingapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pratap.filesharingapp.receive.ReceiveScreen
import com.pratap.filesharingapp.send.SendScreen

/** Single-activity app: a home screen with Send and Receive, each a Compose destination. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { AppNav() }
            }
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val SEND = "send"
    const val RECEIVE = "receive"
}

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onSend = { nav.navigate(Routes.SEND) }, onReceive = { nav.navigate(Routes.RECEIVE) })
        }
        composable(Routes.SEND) { SendScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.RECEIVE) { ReceiveScreen(onBack = { nav.popBackStack() }) }
    }
}

@Composable
private fun HomeScreen(onSend: () -> Unit, onReceive: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("FileSharingApp", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Send photos to another phone through the screen and camera. No Wi-Fi, Bluetooth or internet needed.",
            Modifier.padding(vertical = 24.dp),
            textAlign = TextAlign.Center,
        )
        Button(onClick = onSend, Modifier.fillMaxWidth()) { Text("Send photos") }
        Button(onClick = onReceive, Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Receive photos") }
    }
}
