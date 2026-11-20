package com.truefind.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.truefind.app.model.PromptTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorSheet(
    editingTemplate: PromptTemplate?,
    onDismiss: () -> Unit,
    onSave: (title: String, body: String, existingId: String?) -> Unit
) {
    var title by remember(editingTemplate) { mutableStateOf(editingTemplate?.title.orEmpty()) }
    var body by remember(editingTemplate) { mutableStateOf(editingTemplate?.body.orEmpty()) }
    val canSave = title.trim().isNotEmpty() && body.trim().isNotEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (editingTemplate == null) "新增查核模板" else "編輯查核模板",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("模板名稱（例如：查財經數據）") },
                singleLine = true
            )

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("送給 AI 的查核指令") },
                minLines = 4,
                maxLines = 8
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("取消")
                }
                Button(
                    onClick = { onSave(title.trim(), body.trim(), editingTemplate?.id) },
                    enabled = canSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("儲存")
                }
            }
        }
    }
}
