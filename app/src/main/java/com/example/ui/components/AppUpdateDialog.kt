package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.AppUpdateChecker
import com.example.data.AppUpdateState
import com.example.data.GitHubReleaseInfo

@Composable
fun AppUpdateDialog(
    updateState: AppUpdateState,
    onStartDownload: (GitHubReleaseInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onInstallDownloaded: (AppUpdateState.ReadyToInstall) -> Unit,
    onDismiss: (GitHubReleaseInfo?) -> Unit
) {
    val release = when (updateState) {
        is AppUpdateState.UpdateAvailable -> updateState.release
        is AppUpdateState.Downloading -> updateState.release
        is AppUpdateState.ReadyToInstall -> updateState.release
        is AppUpdateState.Error -> updateState.failedRelease
        else -> null
    } ?: return

    val isDownloading = updateState is AppUpdateState.Downloading

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss(release)
        },
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (updateState) {
                        is AppUpdateState.ReadyToInstall -> Icons.Filled.InstallMobile
                        else -> Icons.Filled.SystemUpdate
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = when (updateState) {
                    is AppUpdateState.Downloading -> stringResource(R.string.update_downloading_title)
                    is AppUpdateState.ReadyToInstall -> stringResource(R.string.update_ready_title)
                    is AppUpdateState.Error -> stringResource(R.string.update_failed_title)
                    else -> stringResource(R.string.update_available_title)
                },
                style = MaterialTheme.typography.headlineMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} → ${release.tagName}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = AppUpdateChecker.formatApkSize(release.apkSizeBytes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                when (updateState) {
                    is AppUpdateState.Downloading -> {
                        val total = updateState.totalBytes
                        val downloaded = updateState.downloadedBytes
                        if (total > 0L) {
                            val fraction = (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                            )
                            Text(
                                text = "${AppUpdateChecker.formatApkSize(downloaded)} / ${AppUpdateChecker.formatApkSize(total)} (${(fraction * 100).toInt()}%)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                            )
                            Text(
                                text = "Downloaded ${AppUpdateChecker.formatApkSize(downloaded)}…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is AppUpdateState.ReadyToInstall -> {
                        Text(
                            text = if (updateState.needsInstallPermission) {
                                "Download complete! Tap below to allow WaddleUp to install updates once, then installation starts automatically."
                            } else {
                                "Update downloaded in-app and ready to install over your current version."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is AppUpdateState.Error -> {
                        Text(
                            text = updateState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    else -> {
                        if (release.releaseNotes.isNotBlank()) {
                            Text(
                                text = release.releaseNotes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        } else {
                            Text(
                                text = "A fresh WaddleUp release is ready on GitHub. Download and install it directly inside the app.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (updateState) {
                is AppUpdateState.Downloading -> {
                    TextButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.testTag("cancel_update_download_button")
                    ) {
                        Text("Cancel")
                    }
                }

                is AppUpdateState.ReadyToInstall -> {
                    Button(
                        onClick = { onInstallDownloaded(updateState) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("install_downloaded_update_button")
                    ) {
                        Icon(
                            imageVector = if (updateState.needsInstallPermission) {
                                Icons.Filled.Security
                            } else {
                                Icons.Filled.InstallMobile
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (updateState.needsInstallPermission) {
                                stringResource(R.string.update_allow_install_cta)
                            } else {
                                stringResource(R.string.update_install_now_cta)
                            }
                        )
                    }
                }

                is AppUpdateState.Error -> {
                    Button(
                        onClick = { onStartDownload(release) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("retry_update_download_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry Download")
                    }
                }

                else -> {
                    Button(
                        onClick = { onStartDownload(release) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("update_now_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.update_now_cta))
                    }
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(
                    onClick = { onDismiss(release) },
                    modifier = Modifier.testTag("update_later_button")
                ) {
                    Text(stringResource(R.string.update_later_cta))
                }
            }
        }
    )
}
