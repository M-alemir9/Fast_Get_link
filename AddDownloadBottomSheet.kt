package com.fastget.downloader.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fastget.downloader.R
import com.fastget.downloader.data.model.MediaQualityOption
import com.fastget.downloader.ui.viewmodel.DownloadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDownloadBottomSheet(
    viewModel: DownloadViewModel,
    initialUrl: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var urlText by remember { mutableStateOf(initialUrl ?: "") }
    var chunkCount by remember { mutableFloatStateOf(8f) }
    var customFileName by remember { mutableStateOf("") }
    var selectedQuality by remember { mutableStateOf<MediaQualityOption?>(null) }

    val isInspecting by viewModel.isInspecting.collectAsState()
    val inspectionResult by viewModel.inspectionResult.collectAsState()
    val inspectionError by viewModel.inspectionError.collectAsState()

    // Auto inspect initial URL if provided from Share Intent
    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            viewModel.inspectUrl(initialUrl)
        }
    }

    LaunchedEffect(inspectionResult) {
        inspectionResult?.let { info ->
            customFileName = info.fileName
            if (info.mediaFormats.isNotEmpty()) {
                selectedQuality = info.mediaFormats.first()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.clearInspection()
            onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.add_download),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // URL input & Paste button
            OutlinedTextField(
                value = urlText,
                onValueChange = {
                    urlText = it
                    if (it.startsWith("http://") || it.startsWith("https://")) {
                        viewModel.inspectUrl(it.trim())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.enter_url_hint)) },
                trailingIcon = {
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!clip.isNullOrBlank()) {
                            urlText = clip.trim()
                            viewModel.inspectUrl(urlText)
                        }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Inspecting Progress
            AnimatedVisibility(visible = isInspecting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.inspecting_url),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Inspection Error
            AnimatedVisibility(visible = inspectionError != null) {
                Text(
                    text = inspectionError ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Inspection Details Card
            AnimatedVisibility(visible = inspectionResult != null) {
                inspectionResult?.let { info ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(14.dp)
                    ) {
                        // File Name field
                        OutlinedTextField(
                            value = customFileName,
                            onValueChange = { customFileName = it },
                            label = { Text("اسم الملف") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Range support badge
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (info.supportsRange) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (info.supportsRange) Color(0xFF10B981) else Color(0xFFF59E0B),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (info.supportsRange) {
                                    stringResource(R.string.range_supported, chunkCount.toInt())
                                } else {
                                    stringResource(R.string.range_not_supported)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Quality options (if video/audio)
                        if (info.mediaFormats.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.select_quality),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            info.mediaFormats.forEach { option ->
                                val isSelected = selectedQuality?.id == option.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                                        .clickable { selectedQuality = option }
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = option.label, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = formatBytes(option.fileSize),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Chunk Count Slider (4 - 16)
                        if (info.supportsRange) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.parts_slider_title, chunkCount.toInt()),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Slider(
                                value = chunkCount,
                                onValueChange = { chunkCount = it },
                                valueRange = 4f..16f,
                                steps = 3 // values: 4, 8, 12, 16
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Start Download Button
            Button(
                onClick = {
                    val info = inspectionResult ?: return@Button
                    val finalUrl = selectedQuality?.downloadUrl ?: info.url
                    val finalSize = selectedQuality?.fileSize ?: info.totalBytes
                    viewModel.startDownload(
                        url = finalUrl,
                        fileName = customFileName.ifBlank { info.fileName },
                        totalBytes = finalSize,
                        supportsRange = info.supportsRange,
                        chunkCount = chunkCount.toInt(),
                        category = info.category,
                        mimeType = info.mimeType
                    )
                    onDismiss()
                },
                enabled = inspectionResult != null && !isInspecting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.start_download_btn),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
