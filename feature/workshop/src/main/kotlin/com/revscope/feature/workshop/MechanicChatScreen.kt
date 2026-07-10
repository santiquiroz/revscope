package com.revscope.feature.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val AccentColor = Color(0xFFE8FF00)
private val ErrorColor = Color(0xFFFF3040)
private val TextColor = Color(0xFFE6E8F0)
private val TextMutedColor = Color(0xFF6B7089)

@Composable
fun MechanicChatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    vm: MechanicChatViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = BgColor,
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).background(BgColor)) {
            ChatTopBar(onNavigateBack)
            when (state.hasProvider) {
                null -> LoadingState()
                false -> NoProviderState(onNavigateToSettings)
                true -> ChatContent(state = state, onSend = vm::sendMessage)
            }
        }
    }
}

@Composable
private fun ChatTopBar(onNavigateBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextColor)
        }
        Text(
            "Mecánico IA",
            color = TextColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AccentColor)
    }
}

@Composable
private fun NoProviderState(onNavigateToSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Para hablar con el Mecánico IA, configura un proveedor en Ajustes → Inteligencia artificial.",
            color = TextMutedColor,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onNavigateToSettings,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color.Black),
        ) {
            Text("Ir a Ajustes", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ChatContent(state: MechanicChatUiState, onSend: (String) -> Unit) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        if (state.messages.isEmpty()) {
            EmptyChatHint(Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(state.messages) { _, message -> ChatBubble(message) }
                if (state.isSending) item { ThinkingBubble() }
            }
        }
        MessageInput(enabled = !state.isSending, onSend = onSend)
    }
}

@Composable
private fun EmptyChatHint(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            "Preguntale al mecánico sobre tu vehículo — ya tiene tus datos actuales a mano.",
            color = TextMutedColor,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ChatBubble(message: MechanicChatMessage) {
    val alignment = if (message is MechanicChatMessage.User) Arrangement.End else Arrangement.Start
    Row(Modifier.fillMaxWidth(), horizontalArrangement = alignment) {
        val (bgColor, textColor) = bubbleColors(message)
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .background(bgColor, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(bubbleText(message), color = textColor, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

private fun bubbleText(message: MechanicChatMessage): String = when (message) {
    is MechanicChatMessage.User -> message.text
    is MechanicChatMessage.Assistant -> message.text
    is MechanicChatMessage.SystemError -> message.text
}

private fun bubbleColors(message: MechanicChatMessage): Pair<Color, Color> = when (message) {
    is MechanicChatMessage.User -> AccentColor to Color.Black
    is MechanicChatMessage.Assistant -> SurfaceColor to TextColor
    is MechanicChatMessage.SystemError -> Color(0xFF2A1216) to ErrorColor
}

@Composable
private fun ThinkingBubble() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .background(SurfaceColor, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AccentColor)
            Text("Pensando…", color = TextMutedColor, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MessageInput(enabled: Boolean, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Row(
        Modifier.fillMaxWidth().imePadding().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            enabled = enabled,
            placeholder = { Text("Escribe tu pregunta…", color = TextMutedColor) },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentColor,
                unfocusedBorderColor = SurfaceColor,
                focusedTextColor = TextColor,
                unfocusedTextColor = TextColor,
            ),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            enabled = enabled && text.isNotBlank(),
            onClick = {
                onSend(text)
                text = ""
            },
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = if (enabled && text.isNotBlank()) AccentColor else TextMutedColor)
        }
    }
}
