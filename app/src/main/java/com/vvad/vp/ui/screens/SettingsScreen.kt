package com.vvad.vp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vvad.vp.data.CredentialsManager
import com.vvad.vp.data.md5
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(credentialsManager: CredentialsManager) {
    val scope = rememberCoroutineScope()
    var serverAddress by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var useHttps by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf("Not Connected") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Navidrome Settings", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = serverAddress,
            onValueChange = { serverAddress = it },
            label = { Text("Server Address (domain:port)") },
            modifier = Modifier.fillMaxWidth()
        )

        // HTTPS Switch Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Use HTTPS (Secure)")
            Switch(
                checked = useHttps,
                onCheckedChange = { useHttps = it }
            )
        }

        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())

        Button(
            onClick = {
                connectionStatus = "Connecting..."
                scope.launch(Dispatchers.IO) {
                    val result = try {
                        val salt = (1..6).map { (('a'..'z') + ('0'..'9')).random() }.joinToString("")
                        val token = md5(password + salt)

                        // Select protocol based on switch [cite: 35, 36]
                        val protocol = if (useHttps) "https://" else "http://"
                        val url = "${protocol}$serverAddress/rest/ping.view?" +
                                "u=$username&t=$token&s=$salt&v=1.16.1&c=VVADPlayer&f=json"

                        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                        if (connection.responseCode == 200) {
                            credentialsManager.save(serverAddress, username, password, useHttps)
                            "Connected!"
                        } else "Error: ${connection.responseCode}"
                    } catch (e: Exception) {
                        "Failed: ${e.localizedMessage}"
                    }
                    connectionStatus = result
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Connect") }
        Text("Status: $connectionStatus")
    }
}