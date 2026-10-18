package com.example.fmodelandroid

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FModelMobileApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FModelMobileApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rootUri by remember { mutableStateOf<Uri?>(null) }
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var entries by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var selected by remember { mutableStateOf<DocumentFile?>(null) }
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("اختر مجلد لعبة أو مجلد ملفات للبدء.") }
    var previewText by remember { mutableStateOf<String?>(null) }
    var previewBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun loadFolder(uri: Uri) {
        val folder = DocumentFile.fromTreeUri(context, rootUri ?: uri)
            ?.let { root ->
                if (uri == root.uri) root else findDocumentFile(context, uri)
            }
        currentUri = uri
        entries = folder?.listFiles()?.sortedWith(
            compareBy<DocumentFile> { !it.isDirectory }.thenBy { it.name?.lowercase().orEmpty() }
        ) ?: emptyList()
        selected = null
        previewText = null
        previewBitmap = null
        status = "${entries.size} عنصر"
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) { }
            rootUri = uri
            currentUri = uri
            loadFolder(uri)
            status = "تم فتح المجلد. اختر ملفاً لعرضه."
        }
    }

    val exportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { target ->
        val source = selected
        if (target != null && source != null && source.isFile) {
            scope.launch {
                busy = true
                status = "جارٍ نسخ الملف..."
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(source.uri).use { input ->
                            context.contentResolver.openOutputStream(target).use { output ->
                                requireNotNull(input) { "تعذرت قراءة الملف المصدر" }
                                requireNotNull(output) { "تعذر فتح الملف الوجهة" }
                                input.copyTo(output)
                            }
                        }
                    }
                    status = "اكتمل نسخ الملف."
                } catch (e: Exception) {
                    status = "فشل التصدير: ${e.message ?: "خطأ غير معروف"}"
                } finally { busy = false }
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("FModel Mobile", fontWeight = FontWeight.Bold)
                            Text("مستكشف ملفات تجريبي", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            val uri = currentUri
                            val root = rootUri
                            if (uri != null && root != null && uri != root) {
                                val parent = findParentUri(context, uri, root)
                                loadFolder(parent ?: root)
                            }
                        }) { Icon(Icons.Default.ArrowBack, contentDescription = "رجوع") }
                    },
                    actions = {
                        IconButton(onClick = { folderPicker.launch(rootUri) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "فتح مجلد")
                        }
                    }
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("بحث باسم الملف...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = { folderPicker.launch(rootUri) }, label = { Text("اختيار مجلد") },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) })
                    if (selected?.isFile == true) {
                        AssistChip(
                            onClick = { exportPicker.launch(selected?.name ?: "export.bin") },
                            label = { Text("تصدير") },
                            leadingIcon = { Icon(Icons.Default.SaveAlt, contentDescription = null) }
                        )
                    }
                }
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(8.dp))

                val visibleEntries = entries.filter { it.name.orEmpty().contains(search, ignoreCase = true) }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(16.dp)) {
                        if (rootUri == null) {
                            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(42.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("لم يتم اختيار مجلد", fontWeight = FontWeight.SemiBold)
                                Text("اضغط «اختيار مجلد» للوصول إلى ملفاتك.", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            LazyColumn(contentPadding = PaddingValues(6.dp)) {
                                items(visibleEntries, key = { it.uri.toString() }) { file ->
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .clickable {
                                                if (file.isDirectory) {
                                                    currentUri = file.uri
                                                    entries = file.listFiles().sortedWith(
                                                        compareBy<DocumentFile> { !it.isDirectory }
                                                            .thenBy { it.name?.lowercase().orEmpty() }
                                                    )
                                                    selected = null
                                                    previewText = null
                                                    previewBitmap = null
                                                    status = "${entries.size} عنصر"
                                                } else {
                                                    selected = file
                                                    previewText = null
                                                    previewBitmap = null
                                                    status = file.name ?: "ملف"
                                                    scope.launch {
                                                        val name = file.name.orEmpty().lowercase()
                                                        try {
                                                            if (name.endsWith(".png") || name.endsWith(".jpg") ||
                                                                name.endsWith(".jpeg") || name.endsWith(".webp")) {
                                                                val bitmap = withContext(Dispatchers.IO) {
                                                                    context.contentResolver.openInputStream(file.uri).use {
                                                                        BitmapFactory.decodeStream(it)
                                                                    }
                                                                }
                                                                previewBitmap = bitmap?.asImageBitmap()
                                                                if (bitmap == null) previewText = "تعذر فك ترميز الصورة."
                                                            } else if (name.endsWith(".txt") || name.endsWith(".json") ||
                                                                name.endsWith(".ini") || name.endsWith(".xml") ||
                                                                name.endsWith(".csv") || name.endsWith(".log")) {
                                                                previewText = withContext(Dispatchers.IO) {
                                                                    context.contentResolver.openInputStream(file.uri).use { stream ->
                                                                        val bytes = requireNotNull(stream).readNBytes(512 * 1024)
                                                                        bytes.toString(Charsets.UTF_8)
                                                                    }
                                                                }
                                                            } else {
                                                                previewText = "نوع الملف غير مدعوم للمعاينة المباشرة حالياً. يمكنك تصديره."
                                                            }
                                                        } catch (e: Exception) {
                                                            previewText = "تعذرت المعاينة: ${e.message ?: "خطأ"}"
                                                        }
                                                    }
                                                }
                                            }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                            contentDescription = null,
                                            tint = if (file.isDirectory) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(file.name ?: "(بدون اسم)", maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                                            Text(if (file.isDirectory) "مجلد" else formatSize(file.length()),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary)
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }

                    Card(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxSize().padding(12.dp)) {
                            Text("معاينة", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(selected?.name ?: "اختر ملفاً", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            when {
                                previewBitmap != null -> Image(
                                    bitmap = previewBitmap!!,
                                    contentDescription = "معاينة الصورة",
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                )
                                previewText != null -> Column(
                                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        previewText.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                else -> Column(
                                    Modifier.fillMaxWidth().weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(36.dp))
                                    Text("صور وملفات نصية مدعومة مبدئياً", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "تنبيه: محرك تحليل حزم Unreal Engine غير مدمج في هذه النسخة الأولية.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun findDocumentFile(context: android.content.Context, target: Uri): DocumentFile? {
    // SAF does not expose a universal URI-to-DocumentFile lookup. This helper handles the
    // common tree-document URI representation without requesting broad storage permissions.
    return try { DocumentFile.fromTreeUri(context, target) ?: DocumentFile.fromSingleUri(context, target) }
    catch (_: Exception) { null }
}

private fun findParentUri(context: android.content.Context, uri: Uri, root: Uri): Uri? {
    if (uri == root) return null
    return try {
        val current = DocumentFile.fromTreeUri(context, root) ?: return root
        // SAF providers vary; use the encoded document ID path when possible.
        val currentId = android.provider.DocumentsContract.getDocumentId(uri)
        val rootId = android.provider.DocumentsContract.getTreeDocumentId(root)
        if (currentId.startsWith(rootId) && currentId != rootId) {
            val parentId = currentId.substringBeforeLast('/')
            android.provider.DocumentsContract.buildDocumentUriUsingTree(root, parentId)
        } else current.uri
    } catch (_: Exception) { root }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${DecimalFormat("#,##0.0").format(kb)} KB"
    return "${DecimalFormat("#,##0.0").format(kb / 1024)} MB"
}
